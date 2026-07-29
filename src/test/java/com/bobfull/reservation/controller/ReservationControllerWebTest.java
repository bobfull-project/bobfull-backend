package com.bobfull.reservation.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bobfull.common.config.ClockConfig;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.common.security.AuthMember;
import com.bobfull.common.security.MemberRole;
import com.bobfull.common.security.SecurityConfig;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.reservation.dto.ReservationAvailabilityResponse;
import com.bobfull.reservation.dto.ReservationPrepareRequest;
import com.bobfull.reservation.dto.ReservationPrepareResponse;
import com.bobfull.reservation.service.ReservationPreparationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = ReservationController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=reservation-controller-web-test-secret-key-please-keep-long",
        "jwt.access-token-expiration-seconds=3600"
})
class ReservationControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationPreparationService reservationPreparationService;

    private Authentication memberAuthentication(Long memberId) {
        AuthMember authMember = new AuthMember(memberId, MemberRole.MEMBER);
        return new UsernamePasswordAuthenticationToken(
                authMember, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    }

    @Test
    void 인증_없이_예약_가능_여부를_조회하면_401을_반환한다() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/reservations/availability")
                .param("type", "CREATE").param("targetId", "200").param("partySize", "2"));

        // then
        result.andExpect(status().isUnauthorized());
    }

    @Test
    void 예약_가능_여부를_조회한다() throws Exception {
        // given
        given(reservationPreparationService.checkAvailability(1L, PaymentPurpose.CREATE, 200L, 2))
                .willReturn(ReservationAvailabilityResponse.available(4));

        // when
        ResultActions result = mockMvc.perform(get("/api/reservations/availability")
                .with(authentication(memberAuthentication(1L)))
                .param("type", "CREATE").param("targetId", "200").param("partySize", "2"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available", is(true)))
                .andExpect(jsonPath("$.data.availableCapacity", is(4)));
    }

    @Test
    void 활성_예약이_이미_있으면_409를_반환한다() throws Exception {
        // given
        given(reservationPreparationService.checkAvailability(eq(1L), eq(PaymentPurpose.CREATE), eq(200L), any()))
                .willThrow(new CustomException(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS));

        // when
        ResultActions result = mockMvc.perform(get("/api/reservations/availability")
                .with(authentication(memberAuthentication(1L)))
                .param("type", "CREATE").param("targetId", "200").param("partySize", "2"));

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ACTIVE_RESERVATION_ALREADY_EXISTS")));
    }

    @Test
    void 결제를_준비하면_paymentId와_만료시각을_반환한다() throws Exception {
        // given
        ReservationPrepareRequest request = new ReservationPrepareRequest(PaymentPurpose.CREATE, 200L, 2);
        given(reservationPreparationService.prepare(eq(1L), any(ReservationPrepareRequest.class)))
                .willReturn(new ReservationPrepareResponse("payment-id", PaymentStatus.READY, BigDecimal.valueOf(20000),
                        OffsetDateTime.parse("2026-07-25T17:10:00+09:00")));

        // when
        ResultActions result = mockMvc.perform(post("/api/reservations/prepare")
                .with(authentication(memberAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId", is("payment-id")))
                .andExpect(jsonPath("$.data.paymentStatus", is("READY")));
    }

    @Test
    void partySize가_없으면_결제_준비는_400을_반환한다() throws Exception {
        // given
        String invalidBody = "{\"type\":\"CREATE\",\"targetId\":200}";

        // when
        ResultActions result = mockMvc.perform(post("/api/reservations/prepare")
                .with(authentication(memberAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")));
    }
}
