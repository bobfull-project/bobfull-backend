package com.bobfull.common.support;

import java.time.OffsetDateTime;

/**
 * Instant를 Asia/Seoul 기준 OffsetDateTime으로 변환해 응답하는 패턴을 검증하기 위한 테스트 전용 응답 DTO다.
 */
public record TestTimeResponse(OffsetDateTime createdAt) {
}
