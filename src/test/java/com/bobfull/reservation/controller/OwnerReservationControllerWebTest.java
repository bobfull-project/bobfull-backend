package com.bobfull.reservation.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bobfull.common.config.ClockConfig;
import com.bobfull.common.response.PageResponse;
import com.bobfull.common.security.AuthMember;
import com.bobfull.common.security.MemberRole;
import com.bobfull.common.security.SecurityConfig;
import com.bobfull.reservation.dto.OwnerReservationDetailResponse;
import com.bobfull.reservation.dto.OwnerReservationParticipantResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.service.OwnerReservationQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerReservationController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=owner-reservation-controller-web-test-secret-key-please-keep-long",
        "jwt.access-token-expiration-seconds=3600"
})
class OwnerReservationControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OwnerReservationQueryService ownerReservationQueryService;

    @Test
    void OWNER가_본인_식당_예약_상세를_조회한다() throws Exception {
        // given
        OwnerReservationDetailResponse response = new OwnerReservationDetailResponse(
                1L, 10L, 100L, 1000L, 4,
                OffsetDateTime.parse("2026-08-01T18:00:00+09:00"), OffsetDateTime.parse("2026-08-01T20:00:00+09:00"),
                ReservationStatus.RECRUITING, RecruitmentStatus.OPEN, 2, 2, 3);
        given(ownerReservationQueryService.getReservationDetail(eq(1L), eq(1L))).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/owner/reservations/1").with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId", is(1)))
                .andExpect(jsonPath("$.data.restaurantId", is(10)))
                .andExpect(jsonPath("$.data.confirmationThreshold", is(3)));
    }

    @Test
    void OWNER가_아니면_예약_상세_조회는_403을_반환한다() throws Exception {
        AuthMember member = new AuthMember(1L, MemberRole.MEMBER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(member, null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        mockMvc.perform(get("/api/owner/reservations/1").with(authentication(authentication)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void OWNER가_예약_참여자_목록을_조회한다() throws Exception {
        // given
        OwnerReservationParticipantResponse participant =
                new OwnerReservationParticipantResponse(10L, 20L, "홍길동", 2, ParticipationStatus.RESERVED);
        given(ownerReservationQueryService.getParticipants(eq(1L), eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageResponse<>(List.of(participant), 0, 20, 1, 1));

        // when & then
        mockMvc.perform(get("/api/owner/reservations/1/participations").with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].memberId", is(20)))
                .andExpect(jsonPath("$.data.content[0].name", is("홍길동")));
    }

    private Authentication ownerAuthentication() {
        AuthMember owner = new AuthMember(1L, MemberRole.OWNER);
        return new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    }
}
