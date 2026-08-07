package com.bobfull.common.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * V1 Access Token(JWT)의 발급과 검증을 담당한다.
 * jjwt(jjwt-api/jjwt-impl/jjwt-gson)로 서명·검증을 위임한다.
 * jjwt-jackson 대신 jjwt-gson을 쓰는 이유는, 이 프로젝트가 Jackson 3(tools.jackson)을 쓰는데
 * jjwt-jackson은 Jackson 2(com.fasterxml.jackson)에 의존해 버전이 충돌하기 때문이다.
 */
public class JwtTokenProvider {

    private static final String CLAIM_MEMBER_ID = "memberId";
    private static final String CLAIM_ROLE = "role";

    private final Clock clock;
    private final SecretKey secretKey;
    private final long accessTokenExpirationSeconds;

    public JwtTokenProvider(Clock clock, String secret, long accessTokenExpirationSeconds) {
        this.clock = clock;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        Instant now = clock.instant();
        Instant expiration = now.plusSeconds(accessTokenExpirationSeconds);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_MEMBER_ID, memberId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 서명·형식·만료를 검증하고 AuthMember를 구성한다.
     * 검증에 실패하는 모든 경우(형식 오류, 서명 불일치, 만료, Claim 손상)를
     * InvalidJwtException 하나로 통일해 필터가 단일 처리로 401로 이어지게 한다.
     */
    public AuthMember parseAccessToken(String token) {
        return parseAccessTokenClaims(token).authMember();
    }

    /**
     * Access Token Blacklist 등록(로그아웃)·조회(인증 필터)에 필요한 jti·만료 시각까지
     * 함께 반환한다(Issue #186). 검증 실패 처리는 {@link #parseAccessToken}과 동일하다.
     */
    public AccessTokenClaims parseAccessTokenClaims(String token) {
        try {
            var claims = Jwts.parser()
                    .clock(() -> Date.from(clock.instant()))
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number memberId = claims.get(CLAIM_MEMBER_ID, Number.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            String jti = claims.getId();
            Date expiration = claims.getExpiration();
            if (memberId == null || role == null || jti == null || expiration == null) {
                throw new InvalidJwtException("토큰에 필수 Claim이 없습니다.");
            }

            AuthMember authMember = new AuthMember(memberId.longValue(), MemberRole.valueOf(role));
            return new AccessTokenClaims(authMember, jti, expiration.toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidJwtException("토큰을 검증할 수 없습니다.", e);
        }
    }

    public record AccessTokenClaims(AuthMember authMember, String jti, Instant expiresAt) {
    }
}
