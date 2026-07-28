package com.bobfull.timeslot.service;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.common.exception.SharedTableErrorCode;
import com.bobfull.common.exception.TimeSlotErrorCode;
import com.bobfull.common.response.PageResponse;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.dto.AvailableDiningSessionListResponse;
import com.bobfull.timeslot.dto.AvailableDiningSessionResponse;
import com.bobfull.timeslot.dto.DiningSessionBulkRequest;
import com.bobfull.timeslot.dto.DiningSessionBulkResponse;
import com.bobfull.timeslot.dto.DiningSessionIdResponse;
import com.bobfull.timeslot.dto.DiningSessionRequest;
import com.bobfull.timeslot.dto.DiningSessionResponse;
import com.bobfull.timeslot.dto.DiningSessionTableBulkRequest;
import com.bobfull.timeslot.dto.DiningSessionTableBulkResponse;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API에서는 diningSession, 내부 영속 모델에서는 TimeSlot으로 다루는 회차 서비스다.
 */
@Service
public class TimeSlotService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<Integer> ALLOWED_CAPACITIES = Set.of(2, 4, 6, 8);

    private final TimeSlotRepository timeSlotRepository;
    private final SharedTableRepository sharedTableRepository;
    private final RestaurantRepository restaurantRepository;
    private final TimeSlotReservationValidator timeSlotReservationValidator;
    private final Clock clock;

    public TimeSlotService(
            TimeSlotRepository timeSlotRepository,
            SharedTableRepository sharedTableRepository,
            RestaurantRepository restaurantRepository,
            TimeSlotReservationValidator timeSlotReservationValidator,
            Clock clock
    ) {
        this.timeSlotRepository = timeSlotRepository;
        this.sharedTableRepository = sharedTableRepository;
        this.restaurantRepository = restaurantRepository;
        this.timeSlotReservationValidator = timeSlotReservationValidator;
        this.clock = clock;
    }

    @Transactional
    public DiningSessionIdResponse register(Long ownerMemberId, Long tableId, DiningSessionRequest request) {
        SharedTable sharedTable = findActiveTableOrThrow(tableId);
        validateRestaurantOwnership(sharedTable.getRestaurantId(), ownerMemberId);

        TimeRange timeRange = toTimeRange(request.startAt(), request.endAt());
        validateActiveDuplicate(sharedTable.getId(), timeRange.startAt());

        TimeSlot savedTimeSlot = saveTimeSlotOrThrowDuplicate(
                TimeSlot.create(sharedTable.getId(), timeRange.startAt(), timeRange.endAt()));
        return DiningSessionIdResponse.from(savedTimeSlot);
    }

    @Transactional
    public DiningSessionBulkResponse registerBulk(
            Long ownerMemberId,
            Long tableId,
            DiningSessionBulkRequest request
    ) {
        SharedTable sharedTable = findActiveTableOrThrow(tableId);
        validateRestaurantOwnership(sharedTable.getRestaurantId(), ownerMemberId);

        List<TimeRange> timeRanges = toSingleDailyTimeRanges(request.dates(), request.startTime(), request.endTime());
        validateNoDuplicateStarts(sharedTable.getId(), timeRanges);

        List<TimeSlot> timeSlots = timeRanges.stream()
                .map(timeRange -> TimeSlot.create(sharedTable.getId(), timeRange.startAt(), timeRange.endAt()))
                .toList();
        saveAllTimeSlotsOrThrowDuplicate(timeSlots);
        return new DiningSessionBulkResponse(sharedTable.getId(), timeSlots.size());
    }

    @Transactional(readOnly = true)
    public PageResponse<DiningSessionResponse> getOwnerDiningSessions(
            Long ownerMemberId,
            Long restaurantId,
            LocalDate date,
            Pageable pageable
    ) {
        Restaurant restaurant = findActiveRestaurantOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);

        List<SharedTable> sharedTables = sharedTableRepository.findAllByRestaurantIdAndDeletedAtIsNull(restaurantId);
        if (sharedTables.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        Map<Long, Integer> capacityByTableId = capacityByTableId(sharedTables);
        Collection<Long> tableIds = capacityByTableId.keySet();
        Page<TimeSlot> timeSlots = findOwnerTimeSlots(tableIds, date, pageable);
        Page<DiningSessionResponse> responsePage = timeSlots.map(timeSlot -> toDiningSessionResponse(
                timeSlot,
                capacityByTableId.get(timeSlot.getSharedTableId())
        ));
        return PageResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public AvailableDiningSessionListResponse getAvailableDiningSessions(
            Long restaurantId,
            LocalDate date,
            Integer partySize
    ) {
        validatePartySize(partySize);
        findActiveRestaurantOrThrow(restaurantId);

        List<SharedTable> sharedTables = sharedTableRepository.findAllByRestaurantIdAndDeletedAtIsNull(restaurantId);
        if (sharedTables.isEmpty()) {
            return new AvailableDiningSessionListResponse(restaurantId, List.of());
        }

        Map<Long, Integer> capacityByTableId = capacityByTableId(sharedTables);
        DateRange dateRange = toDateRange(date);
        List<AvailableDiningSessionResponse> content = timeSlotRepository
                .findAllBySharedTableIdInAndStartAtGreaterThanEqualAndStartAtLessThanAndDeletedAtIsNullOrderByStartAtAsc(
                        capacityByTableId.keySet(), dateRange.startAt(), dateRange.endAt())
                .stream()
                .map(timeSlot -> toAvailableDiningSessionResponse(
                        timeSlot,
                        capacityByTableId.get(timeSlot.getSharedTableId())
                ))
                .filter(response -> partySize == null || response.availableCapacity() >= partySize)
                .toList();
        return new AvailableDiningSessionListResponse(restaurantId, content);
    }

    @Transactional
    public DiningSessionIdResponse update(Long ownerMemberId, Long sessionId, DiningSessionRequest request) {
        TimeSlot timeSlot = findActiveTimeSlotOrThrow(sessionId);
        SharedTable sharedTable = findActiveTableOrThrow(timeSlot.getSharedTableId());
        validateRestaurantOwnership(sharedTable.getRestaurantId(), ownerMemberId);

        TimeRange timeRange = toTimeRange(request.startAt(), request.endAt());
        timeSlotReservationValidator.validateChangeAllowed(timeSlot.getId());
        validateActiveDuplicateForUpdate(sharedTable.getId(), timeRange.startAt(), timeSlot.getId());

        timeSlot.update(timeRange.startAt(), timeRange.endAt());
        flushTimeSlotOrThrowDuplicate();
        return DiningSessionIdResponse.from(timeSlot);
    }

    @Transactional
    public DiningSessionIdResponse delete(Long ownerMemberId, Long sessionId) {
        TimeSlot timeSlot = findActiveTimeSlotOrThrow(sessionId);
        SharedTable sharedTable = findActiveTableOrThrow(timeSlot.getSharedTableId());
        validateRestaurantOwnership(sharedTable.getRestaurantId(), ownerMemberId);
        timeSlotReservationValidator.validateDeletionAllowed(timeSlot.getId());

        timeSlot.softDelete(clock.instant());
        return DiningSessionIdResponse.from(timeSlot);
    }

    @Transactional
    public DiningSessionTableBulkResponse registerTableWithDiningSessions(
            Long ownerMemberId,
            Long restaurantId,
            DiningSessionTableBulkRequest request
    ) {
        Restaurant restaurant = findActiveRestaurantOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);
        validateCapacity(request.capacity());

        List<TimeRange> timeRanges = toIntervalTimeRanges(
                request.dates(), request.startTime(), request.endTime(), request.intervalMinutes());
        validateNoDuplicateStartsInRequest(timeRanges);

        SharedTable sharedTable = sharedTableRepository.save(SharedTable.create(restaurantId, request.capacity()));
        List<TimeSlot> timeSlots = timeRanges.stream()
                .map(timeRange -> TimeSlot.create(sharedTable.getId(), timeRange.startAt(), timeRange.endAt()))
                .toList();
        saveAllTimeSlotsOrThrowDuplicate(timeSlots);

        return new DiningSessionTableBulkResponse(sharedTable.getId(), sharedTable.getCapacity(), timeSlots.size());
    }

    private Page<TimeSlot> findOwnerTimeSlots(Collection<Long> tableIds, LocalDate date, Pageable pageable) {
        if (date == null) {
            return timeSlotRepository.findAllBySharedTableIdInAndDeletedAtIsNullOrderByStartAtAsc(tableIds, pageable);
        }

        DateRange dateRange = toDateRange(date);
        return timeSlotRepository
                .findAllBySharedTableIdInAndStartAtGreaterThanEqualAndStartAtLessThanAndDeletedAtIsNullOrderByStartAtAsc(
                        tableIds, dateRange.startAt(), dateRange.endAt(), pageable);
    }

    private DiningSessionResponse toDiningSessionResponse(TimeSlot timeSlot, Integer capacity) {
        return DiningSessionResponse.of(
                timeSlot,
                capacity,
                toOffsetDateTime(timeSlot.getStartAt()),
                toOffsetDateTime(timeSlot.getEndAt())
        );
    }

    private AvailableDiningSessionResponse toAvailableDiningSessionResponse(TimeSlot timeSlot, Integer capacity) {
        return AvailableDiningSessionResponse.of(
                timeSlot,
                capacity,
                toOffsetDateTime(timeSlot.getStartAt()),
                toOffsetDateTime(timeSlot.getEndAt()),
                capacity
        );
    }

    private Map<Long, Integer> capacityByTableId(List<SharedTable> sharedTables) {
        return sharedTables.stream()
                .collect(Collectors.toMap(SharedTable::getId, SharedTable::getCapacity));
    }

    private SharedTable findActiveTableOrThrow(Long tableId) {
        return sharedTableRepository.findByIdAndDeletedAtIsNull(tableId)
                .orElseThrow(() -> new CustomException(SharedTableErrorCode.TABLE_ID_NOT_FOUND));
    }

    private TimeSlot findActiveTimeSlotOrThrow(Long sessionId) {
        return timeSlotRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new CustomException(TimeSlotErrorCode.SESSION_ID_NOT_FOUND));
    }

    private Restaurant findActiveRestaurantOrThrow(Long restaurantId) {
        return restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(() -> new CustomException(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND));
    }

    private void validateRestaurantOwnership(Long restaurantId, Long ownerMemberId) {
        Restaurant restaurant = findActiveRestaurantOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);
    }

    private void validateOwnership(Restaurant restaurant, Long ownerMemberId) {
        if (!restaurant.isOwnedBy(ownerMemberId)) {
            throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void validateCapacity(Integer capacity) {
        if (capacity == null || !ALLOWED_CAPACITIES.contains(capacity)) {
            throw new CustomException(SharedTableErrorCode.INVALID_TABLE_CAPACITY);
        }
    }

    private void validatePartySize(Integer partySize) {
        if (partySize != null && partySize <= 0) {
            throw new CustomException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateActiveDuplicate(Long tableId, Instant startAt) {
        if (timeSlotRepository.existsBySharedTableIdAndStartAtAndDeletedAtIsNull(tableId, startAt)) {
            throw new CustomException(TimeSlotErrorCode.DUPLICATE_DINING_SESSION);
        }
    }

    private void validateActiveDuplicateForUpdate(Long tableId, Instant startAt, Long sessionId) {
        if (timeSlotRepository.existsBySharedTableIdAndStartAtAndDeletedAtIsNullAndIdNot(tableId, startAt, sessionId)) {
            throw new CustomException(TimeSlotErrorCode.DUPLICATE_DINING_SESSION);
        }
    }

    private void validateNoDuplicateStarts(Long tableId, List<TimeRange> timeRanges) {
        validateNoDuplicateStartsInRequest(timeRanges);
        for (TimeRange timeRange : timeRanges) {
            validateActiveDuplicate(tableId, timeRange.startAt());
        }
    }

    private void validateNoDuplicateStartsInRequest(List<TimeRange> timeRanges) {
        Set<Instant> starts = new HashSet<>();
        for (TimeRange timeRange : timeRanges) {
            if (!starts.add(timeRange.startAt())) {
                throw new CustomException(TimeSlotErrorCode.DUPLICATE_DINING_SESSION);
            }
        }
    }

    private TimeSlot saveTimeSlotOrThrowDuplicate(TimeSlot timeSlot) {
        try {
            return timeSlotRepository.saveAndFlush(timeSlot);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(TimeSlotErrorCode.DUPLICATE_DINING_SESSION);
        }
    }

    private void saveAllTimeSlotsOrThrowDuplicate(List<TimeSlot> timeSlots) {
        try {
            timeSlotRepository.saveAllAndFlush(timeSlots);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(TimeSlotErrorCode.DUPLICATE_DINING_SESSION);
        }
    }

    private void flushTimeSlotOrThrowDuplicate() {
        try {
            timeSlotRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(TimeSlotErrorCode.DUPLICATE_DINING_SESSION);
        }
    }

    private TimeRange toTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
        Instant startInstant = startAt.atZone(SEOUL_ZONE).toInstant();
        Instant endInstant = endAt.atZone(SEOUL_ZONE).toInstant();
        validateTimeRange(startInstant, endInstant);
        return new TimeRange(startInstant, endInstant);
    }

    private List<TimeRange> toSingleDailyTimeRanges(List<LocalDate> dates, LocalTime startTime, LocalTime endTime) {
        validateDailyTimeRange(startTime, endTime);
        return dates.stream()
                .map(date -> new TimeRange(
                        toInstant(date, startTime),
                        toInstant(date, endTime)
                ))
                .toList();
    }

    private List<TimeRange> toIntervalTimeRanges(
            List<LocalDate> dates,
            LocalTime startTime,
            LocalTime endTime,
            Integer intervalMinutes
    ) {
        validateDailyTimeRange(startTime, endTime);
        validateInterval(startTime, endTime, intervalMinutes);

        List<TimeRange> timeRanges = new ArrayList<>();
        for (LocalDate date : dates) {
            LocalTime currentStart = startTime;
            while (currentStart.isBefore(endTime)) {
                LocalTime currentEnd = currentStart.plusMinutes(intervalMinutes);
                timeRanges.add(new TimeRange(
                        toInstant(date, currentStart),
                        toInstant(date, currentEnd)
                ));
                currentStart = currentEnd;
            }
        }
        return timeRanges;
    }

    private void validateDailyTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new CustomException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateTimeRange(Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new CustomException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateInterval(LocalTime startTime, LocalTime endTime, Integer intervalMinutes) {
        long totalMinutes = Duration.between(startTime, endTime).toMinutes();
        if (intervalMinutes == null || intervalMinutes <= 0 || totalMinutes % intervalMinutes != 0) {
            throw new CustomException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private DateRange toDateRange(LocalDate date) {
        return new DateRange(toInstant(date, LocalTime.MIN), toInstant(date.plusDays(1), LocalTime.MIN));
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).atZone(SEOUL_ZONE).toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_ZONE).toOffsetDateTime();
    }

    private record TimeRange(Instant startAt, Instant endAt) {
    }

    private record DateRange(Instant startAt, Instant endAt) {
    }
}
