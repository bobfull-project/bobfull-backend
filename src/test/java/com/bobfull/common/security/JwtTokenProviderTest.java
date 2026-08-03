package com.bobfull.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * JwtTokenProvider의 Access Token 발급·검증(서명, 만료, 형식)을 검증한다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "jwt-token-provider-test-secret-key-please-keep-this-long-enough";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 발급한_토큰을_검증하면_같은_회원ID와_역할을_가진_AuthMember를_반환한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(FIXED_CLOCK, SECRET, 3600L);
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.OWNER);

        // when
        AuthMember authMember = jwtTokenProvider.parseAccessToken(accessToken);

        // then
        assertThat(authMember.id()).isEqualTo(1L);
        assertThat(authMember.role()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    void 발급한_토큰의_서명_알고리즘은_HS256으로_고정된다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(FIXED_CLOCK, SECRET, 3600L);
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.OWNER);

        // when
        String algorithm = Jwts.parser()
                .clock(() -> Date.from(FIXED_CLOCK.instant()))
                .verifyWith(secretKeyFor(SECRET))
                .build()
                .parseSignedClaims(accessToken)
                .getHeader()
                .getAlgorithm();

        // then
        assertThat(algorithm).isEqualTo("HS256");
    }

    @Test
    void 다른_비밀값으로_생성된_토큰은_검증에_실패한다() {
        // given
        JwtTokenProvider issuer = new JwtTokenProvider(FIXED_CLOCK, SECRET, 3600L);
        JwtTokenProvider verifier = new JwtTokenProvider(FIXED_CLOCK, "다른-비밀값-다른-비밀값-다른-비밀값", 3600L);
        String accessToken = issuer.createAccessToken(1L, MemberRole.MEMBER);

        // when
        Throwable result = catchThrowable(() -> verifier.parseAccessToken(accessToken));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void 만료_시간이_지난_토큰은_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(FIXED_CLOCK, SECRET, -10L);
        String expiredToken = jwtTokenProvider.createAccessToken(1L, MemberRole.MEMBER);

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken(expiredToken));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void 점으로_구분된_세_부분이_아니면_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(FIXED_CLOCK, SECRET, 3600L);

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken("header.payload"));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void 서명이_변조된_토큰은_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(FIXED_CLOCK, SECRET, 3600L);
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.MEMBER);
        String tamperedToken = tamperSignature(accessToken);

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken(tamperedToken));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void memberId_클레임이_없으면_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(FIXED_CLOCK, SECRET, 3600L);
        String tokenWithoutMemberId = Jwts.builder()
                .claim("role", MemberRole.MEMBER.name())
                .issuedAt(Date.from(FIXED_CLOCK.instant()))
                .expiration(Date.from(FIXED_CLOCK.instant().plusSeconds(3600L)))
                .signWith(secretKeyFor(SECRET), Jwts.SIG.HS256)
                .compact();

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken(tokenWithoutMemberId));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void role_클레임이_없으면_검증에_실패한다() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(FIXED_CLOCK, SECRET, 3600L);
        String tokenWithoutRole = Jwts.builder()
                .claim("memberId", 1L)
                .issuedAt(Date.from(FIXED_CLOCK.instant()))
                .expiration(Date.from(FIXED_CLOCK.instant().plusSeconds(3600L)))
                .signWith(secretKeyFor(SECRET), Jwts.SIG.HS256)
                .compact();

        // when
        Throwable result = catchThrowable(() -> jwtTokenProvider.parseAccessToken(tokenWithoutRole));

        // then
        assertThat(result).isInstanceOf(InvalidJwtException.class);
    }

    private SecretKey secretKeyFor(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 서명 부분의 첫 글자를 변조한다. 마지막 글자는 Base64url 인코딩의 padding 비트에 걸릴 수 있어
     * 디코딩 결과가 우연히 바뀌지 않을 수 있으므로(flaky), 항상 실제 바이트가 바뀌는 첫 글자를 사용한다.
     */
    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char[] signatureChars = parts[2].toCharArray();
        signatureChars[0] = signatureChars[0] == 'a' ? 'b' : 'a';
        return parts[0] + "." + parts[1] + "." + new String(signatureChars);
    }
}
