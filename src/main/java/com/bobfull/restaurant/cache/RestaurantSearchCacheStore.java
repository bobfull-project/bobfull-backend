package com.bobfull.restaurant.cache;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 식당 검색 결과를 Redis에 캐시한다(Issue #62). 인증 Redis(RefreshTokenStore,
 * AccessTokenBlacklistStore)와 같은 Redis 인프라를 재사용하되 key prefix(`bobfull:search:`)를
 * 분리해 책임을 나눈다(Human 결정 Q3).
 *
 * <p>무효화는 개별 key 삭제 대신 버전 번호(namespace 방식)로 처리한다. 검색 결과 하나가 어떤
 * Restaurant 변경에 영향을 받는지 역추적할 수 없으므로(해시된 key), Restaurant 생성·수정·삭제
 * 시 {@link #bumpVersion()}으로 전체 검색 캐시를 한 번에 무효화한다(Issue "TTL과 무효화 계약").
 * 이전 버전 key는 명시적으로 지우지 않고 TTL 만료로 자연 소멸한다.
 *
 * <p>Redis 장애 시 캐시 조회·저장·버전 증가 모두 예외를 삼키고 로그만 남긴다(Human 결정 Q2
 * Fail-open) — 검색 캐시 장애가 검색 API 자체를 막지 않는다. 로그인·토큰 등 인증 Redis 실패
 * 정책과는 무관하게 독립적으로 동작한다.</p>
 */
@Component
public class RestaurantSearchCacheStore {

    private static final Logger log = LoggerFactory.getLogger(RestaurantSearchCacheStore.class);

    private static final String VERSION_KEY = "bobfull:search:restaurants:version";
    private static final String RESULT_KEY_PREFIX = "bobfull:search:restaurants:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RestaurantSearchCacheStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${restaurant.search-cache.ttl-seconds:60}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<CachedRestaurantSearchResult> find(RestaurantSearchCacheKey key) {
        try {
            String value = redisTemplate.opsForValue().get(resultKey(currentVersion(), key));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, CachedRestaurantSearchResult.class));
        } catch (RuntimeException e) {
            log.warn("event=RESTAURANT_SEARCH_CACHE_READ_FAILED reason={}", e.getClass().getSimpleName(), e);
            return Optional.empty();
        }
    }

    public void put(RestaurantSearchCacheKey key, CachedRestaurantSearchResult result) {
        try {
            String value = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(resultKey(currentVersion(), key), value, ttl);
        } catch (RuntimeException e) {
            log.warn("event=RESTAURANT_SEARCH_CACHE_WRITE_FAILED reason={}", e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Restaurant 생성·수정·삭제 시 호출해 이후 조회부터 새 버전 key를 쓰게 만든다. 기존에
     * 캐시된 검색 결과는 더 이상 조회되지 않고 TTL이 지나면 자연 삭제된다.
     */
    public void bumpVersion() {
        try {
            redisTemplate.opsForValue().increment(VERSION_KEY);
        } catch (RuntimeException e) {
            log.warn("event=RESTAURANT_SEARCH_CACHE_VERSION_BUMP_FAILED reason={}", e.getClass().getSimpleName(), e);
        }
    }

    private long currentVersion() {
        String value = redisTemplate.opsForValue().get(VERSION_KEY);
        return value == null ? 0L : Long.parseLong(value);
    }

    private String resultKey(long version, RestaurantSearchCacheKey key) {
        return RESULT_KEY_PREFIX + version + ":" + key.digest();
    }
}
