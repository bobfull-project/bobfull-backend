package com.bobfull.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

/**
 * V1 Access Token(JWT)의 발급과 검증을 담당한다.
 * HS256 서명을 JDK 표준 API로 직접 구현해 별도 JWT 라이브러리를 추가하지 않는다.
 * 회원가입·로그인 Issue는 이 컴포넌트의 createAccessToken만 호출하고,
 * 서명·클레임 구조는 이 클래스 안에서만 관리한다.
 */
public class JwtTokenProvider {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String CLAIM_MEMBER_ID = "memberId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ISSUED_AT = "iat";
    private static final String CLAIM_EXPIRATION = "exp";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecretKeySpec secretKey;
    private final long accessTokenExpirationSeconds;
    private final String encodedHeader;

    public JwtTokenProvider(
            ObjectMapper objectMapper,
            Clock clock,
            String secret,
            long accessTokenExpirationSeconds
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.encodedHeader = encode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        Instant now = clock.instant();
        Instant expiration = now.plusSeconds(accessTokenExpirationSeconds);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(CLAIM_MEMBER_ID, memberId);
        claims.put(CLAIM_ROLE, role.name());
        claims.put(CLAIM_ISSUED_AT, now.getEpochSecond());
        claims.put(CLAIM_EXPIRATION, expiration.getEpochSecond());

        String encodedPayload = encode(objectMapper.writeValueAsBytes(claims));
        String signingInput = encodedHeader + "." + encodedPayload;

        return signingInput + "." + sign(signingInput);
    }

    /**
     * 서명·형식·만료를 검증하고 AuthMember를 구성한다.
     * 검증에 실패하는 모든 경우(형식 오류, 서명 불일치, 만료, Claim 손상)를
     * InvalidJwtException 하나로 통일해 필터가 단일 처리로 401로 이어지게 한다.
     */
    public AuthMember parseAccessToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new InvalidJwtException("토큰 형식이 올바르지 않습니다.");
            }

            String signingInput = parts[0] + "." + parts[1];
            if (!isSignatureValid(signingInput, parts[2])) {
                throw new InvalidJwtException("토큰 서명이 유효하지 않습니다.");
            }

            Map<?, ?> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);

            long expiration = ((Number) claims.get(CLAIM_EXPIRATION)).longValue();
            if (clock.instant().getEpochSecond() >= expiration) {
                throw new InvalidJwtException("토큰이 만료됐습니다.");
            }

            Long memberId = ((Number) claims.get(CLAIM_MEMBER_ID)).longValue();
            MemberRole role = MemberRole.valueOf((String) claims.get(CLAIM_ROLE));

            return new AuthMember(memberId, role);
        } catch (InvalidJwtException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidJwtException("토큰을 검증할 수 없습니다.", e);
        }
    }

    private boolean isSignatureValid(String signingInput, String signature) {
        String expectedSignature = sign(signingInput);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return encode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JWT 서명 처리 중 오류가 발생했습니다.", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
