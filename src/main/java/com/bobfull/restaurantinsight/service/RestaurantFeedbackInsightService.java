package com.bobfull.restaurantinsight.service;

import com.bobfull.chat.entity.ChatMessage;
import com.bobfull.chat.entity.ChatRoom;
import com.bobfull.chat.repository.ChatMessageRepository;
import com.bobfull.chat.repository.ChatRoomRepository;
import com.bobfull.common.exception.ChatErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.restaurantinsight.adapter.RestaurantFeedbackPrompt;
import com.bobfull.restaurantinsight.dto.RestaurantFeedbackInsightListResponse;
import com.bobfull.restaurantinsight.dto.RestaurantFeedbackInsightResponse;
import com.bobfull.restaurantinsight.entity.RestaurantFeedbackAnalysisStatus;
import com.bobfull.restaurantinsight.entity.RestaurantFeedbackInsight;
import com.bobfull.restaurantinsight.port.RestaurantFeedbackInsightPort;
import com.bobfull.restaurantinsight.repository.RestaurantFeedbackInsightRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ChatMessage에서 식당을 역추적해 파생 결과를 만들고 OWNER용 익명 집계를 제공한다. */
@Service
public class RestaurantFeedbackInsightService {
    private static final long MINIMUM_DISTINCT_SENDERS = 3;
    private final ChatMessageRepository messages; private final ChatRoomRepository rooms; private final ReservationRepository reservations;
    private final TimeSlotRepository timeSlots; private final SharedTableRepository tables; private final RestaurantRepository restaurants;
    private final RestaurantFeedbackInsightRepository insights; private final RestaurantInsightCandidateGate candidateGate;
    private final RestaurantInsightPrivacyValidator privacyValidator; private final ObjectProvider<RestaurantFeedbackInsightPort> provider; private final Clock clock; private final String activePromptVersion;
    private final RestaurantFeedbackInsightWriter writer;
    public RestaurantFeedbackInsightService(ChatMessageRepository messages, ChatRoomRepository rooms, ReservationRepository reservations, TimeSlotRepository timeSlots, SharedTableRepository tables, RestaurantRepository restaurants, RestaurantFeedbackInsightRepository insights, RestaurantInsightCandidateGate candidateGate, RestaurantInsightPrivacyValidator privacyValidator, ObjectProvider<RestaurantFeedbackInsightPort> provider, Clock clock, @Value("${bobfull.restaurant-feedback.active-prompt-version:restaurant-feedback-v1}") String activePromptVersion, RestaurantFeedbackInsightWriter writer) {
        this.messages=messages; this.rooms=rooms; this.reservations=reservations; this.timeSlots=timeSlots; this.tables=tables; this.restaurants=restaurants; this.insights=insights; this.candidateGate=candidateGate; this.privacyValidator=privacyValidator; this.provider=provider; this.clock=clock; this.activePromptVersion=activePromptVersion; this.writer=writer;
    }
    @Transactional
    public void analyze(Long messageId) {
        if (insights.findByMessageIdAndPromptVersion(messageId, activePromptVersion).isPresent()) return;
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_MESSAGE_ID_NOT_FOUND));
        Long restaurantId = resolveRestaurantId(message.getChatRoomId());
        if (privacyValidator.containsSensitiveIdentifier(message.getContent())) { saveExcluded(messageId, restaurantId, RestaurantFeedbackAnalysisStatus.EXCLUDED_INPUT_PII); return; }
        if (!candidateGate.isCandidate(message.getContent())) { saveExcluded(messageId, restaurantId, RestaurantFeedbackAnalysisStatus.EXCLUDED_CANDIDATE); return; }
        RestaurantFeedbackInsightPort activeProvider = provider.getIfAvailable();
        if (activeProvider == null) {
            // consumer-enabled=false인 Production 기본값에서는 Consumer Bean 자체가 없어 이 메서드가 호출되지
            // 않는다. 반대로 consumer-enabled=true인데 ai.restaurant-insight.enabled=false로 Provider가 없는
            // 설정 오류 상태에서 여기까지 도달하면, 조용히 offset을 커밋시키지 않고 기술 실패로 재시도/DLT되게 한다.
            throw new IllegalStateException(
                    "Restaurant Insight consumer is enabled but no RestaurantFeedbackInsightPort bean is configured");
        }
        RestaurantFeedbackInsightPort.Result result = activeProvider.analyze(message.getContent());
        if (result.analysis() == null) throw new IllegalStateException("Insight structured output analysis is missing");
        java.util.List<com.bobfull.restaurantinsight.dto.RestaurantFeedbackAnalysis.Item> rawItems = !result.analysis().relevant() || result.analysis().items() == null ? java.util.List.of() : result.analysis().items();
        java.util.List<com.bobfull.restaurantinsight.dto.RestaurantFeedbackAnalysis.Item> validItems = rawItems.stream().filter(item -> item.category() != null && item.aspectType() != null && item.opinionType() != null && item.sentiment() != null && privacyValidator.isSafeAspect(item.normalizedAspect())).toList();
        RestaurantFeedbackInsight insight = validItems.isEmpty() ? RestaurantFeedbackInsight.excluded(messageId, restaurantId, activePromptVersion, clock.instant(), RestaurantFeedbackAnalysisStatus.EXCLUDED_OUTPUT_VALIDATION) : RestaurantFeedbackInsight.completed(messageId, restaurantId, activePromptVersion, result.provider(), result.modelName(), clock.instant());
        for (var item : validItems) insight.addItem(item.category(), item.aspectType(), privacyValidator.normalizeSafeAspect(item.normalizedAspect()), item.opinionType(), item.sentiment());
        // writer.save()는 REQUIRES_NEW로 별도 트랜잭션에서 실행되므로, 동시 경쟁으로 인한 UNIQUE 위반이
        // 이 메서드(및 호출자인 Kafka listener)의 트랜잭션을 rollback-only로 오염시키지 않는다.
        try { writer.save(insight); } catch (DataIntegrityViolationException exception) { if (insights.findByMessageIdAndPromptVersion(messageId, activePromptVersion).isEmpty()) throw exception; }
    }
    private void saveExcluded(Long messageId, Long restaurantId, RestaurantFeedbackAnalysisStatus status) {
        try { writer.save(RestaurantFeedbackInsight.excluded(messageId, restaurantId, activePromptVersion, clock.instant(), status)); }
        catch (DataIntegrityViolationException exception) { if (insights.findByMessageIdAndPromptVersion(messageId, activePromptVersion).isEmpty()) throw exception; }
    }
    @Transactional(readOnly = true)
    public RestaurantFeedbackInsightListResponse getOwnerInsights(Long ownerId, Long restaurantId) {
        Restaurant restaurant = restaurants.findByIdAndDeletedAtIsNull(restaurantId).orElseThrow(() -> new CustomException(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND));
        if (!restaurant.getOwnerMemberId().equals(ownerId)) throw new CustomException(com.bobfull.common.exception.CommonErrorCode.ACCESS_DENIED);
        Instant now = clock.instant(); Instant from = now.minus(java.time.Duration.ofDays(7));
        return new RestaurantFeedbackInsightListResponse(restaurantId, from, now, insights.aggregateForOwner(restaurantId, activePromptVersion, from, MINIMUM_DISTINCT_SENDERS).stream().map(RestaurantFeedbackInsightResponse::from).toList());
    }
    private Long resolveRestaurantId(Long chatRoomId) {
        ChatRoom room = rooms.findById(chatRoomId).orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_ROOM_ID_NOT_FOUND));
        Reservation reservation = reservations.findById(room.getReservationId()).orElseThrow(() -> new IllegalStateException("Insight reservation missing"));
        TimeSlot timeSlot = timeSlots.findById(reservation.getTimeSlotId()).orElseThrow(() -> new IllegalStateException("Insight timeSlot missing"));
        SharedTable table = tables.findById(timeSlot.getSharedTableId()).orElseThrow(() -> new IllegalStateException("Insight sharedTable missing"));
        return restaurants.findById(table.getRestaurantId()).orElseThrow(() -> new IllegalStateException("Insight restaurant missing")).getId();
    }
}
