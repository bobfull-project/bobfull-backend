package com.bobfull.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * JwtTokenProvider의 Access Token 발급·검증(서명, 만료, 형식)을 검증한다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "jwt-token-provider-test-secret-key-please-keep-this-long-enough";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void 발급한_토큰을_검증하면_같은_회원ID와_역할을_가진_AuthMember를_반환한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(objectMapper, FIXED_CLOCK, SECRET, 3600L);
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.OWNER);

        // when
        AuthMember authMember = jwtTokenProvider.parseAccessToken(accessToken);

        // then
        assertThat(authMember.id()).isEqualTo(1L);
        assertThat(authMember.role()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    void 다른_비밀값으로_생성된_토큰은_검증에_실패한다() {
        // given
        JwtTokenProvider issuer = new JwtTokenProvider(objectMapper, FIXED_CLOCK, SECRET, 3600L);
        JwtTokenProvider verifier = new JwtTokenProvider(objectMapper, FIXED_CLOCK, "다른-비밀값-다른-비밀값-다른-비밀값", 3600L);
        String accessToken = issuer.createAccessToken(1L, MemberRole.MEMBER);

        // when
        Throwable result = catchThrowable(() -> verifier.parseAccessToken(accessToken));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void 만료_시간이_지난_토큰은_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(objectMapper, FIXED_CLOCK, SECRET, -10L);
        String expiredToken = jwtTokenProvider.createAccessToken(1L, MemberRole.MEMBER);

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken(expiredToken));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void 점으로_구분된_세_부분이_아니면_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(objectMapper, FIXED_CLOCK, SECRET, 3600L);

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken("header.payload"));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void 서명이_변조된_토큰은_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(objectMapper, FIXED_CLOCK, SECRET, 3600L);
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.MEMBER);
        String tamperedToken = accessToken.substring(0, accessToken.length() - 1)
                + (accessToken.endsWith("a") ? "b" : "a");

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken(tamperedToken));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }
}
