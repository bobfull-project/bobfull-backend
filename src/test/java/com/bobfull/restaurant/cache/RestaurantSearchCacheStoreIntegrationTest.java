package com.bobfull.restaurant.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Redis(Docker)로 저장·조회·버전 무효화·Redis 장애 Fail-open을 검증하는 선택적
 * 통합 테스트다. BOBFULL_REDIS_INTEGRATION_TEST=true 일 때만 실행하며, Spring 전체 컨텍스트
 * 없이 RestaurantSearchCacheStore를 직접 구성해 Redis 연결만으로 검증한다(Issue #62).
 */
@EnabledIfEnvironmentVariable(named = "BOBFULL_REDIS_INTEGRATION_TEST", matches = "true")
class RestaurantSearchCacheStoreIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void connect() {
        String host = System.getenv().getOrDefault("BOBFULL_TEST_REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("BOBFULL_TEST_REDIS_PORT", "6379"));
        connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void 저장한_검색_결과를_같은_Key로_조회하면_그대로_반환된다() {
        RestaurantSearchCacheStore store = store(60);
        RestaurantSearchCacheKey key = key("맛집", 0);
        CachedRestaurantSearchResult result = result(3001L);

        store.put(key, result);

        assertThat(store.find(key)).isPresent().get().isEqualTo(result);
    }

    @Test
    void 저장하지_않은_Key는_조회되지_않는다() {
        RestaurantSearchCacheStore store = store(60);

        assertThat(store.find(key("존재하지-않음", 0))).isEmpty();
    }

    @Test
    void bumpVersion_이후에는_기존에_저장한_결과가_더이상_조회되지_않는다() {
        RestaurantSearchCacheStore store = store(60);
        RestaurantSearchCacheKey key = key("한식", 0);
        store.put(key, result(3002L));
        assertThat(store.find(key)).isPresent();

        store.bumpVersion();

        assertThat(store.find(key)).isEmpty();
    }

    @Test
    void TTL이_지나면_저장한_결과가_자동으로_사라진다() throws InterruptedException {
        RestaurantSearchCacheStore store = store(1);
        RestaurantSearchCacheKey key = key("일식", 0);
        store.put(key, result(3003L));

        Thread.sleep(1500);

        assertThat(store.find(key)).isEmpty();
    }

    @Test
    void Redis에_연결할_수_없으면_예외_대신_빈_결과를_반환한다() {
        LettuceConnectionFactory unreachableFactory = new LettuceConnectionFactory("localhost", 1);
        unreachableFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redisTemplate = new StringRedisTemplate(unreachableFactory);
            redisTemplate.afterPropertiesSet();
            RestaurantSearchCacheStore store =
                    new RestaurantSearchCacheStore(redisTemplate, JsonMapper.builder().build(), 60);

            assertThat(store.find(key("무관", 0))).isEmpty();
            store.put(key("무관", 0), result(3004L));
            store.bumpVersion();
        } finally {
            unreachableFactory.destroy();
        }
    }

    private RestaurantSearchCacheKey key(String keyword, int page) {
        return new RestaurantSearchCacheKey(keyword.trim().toLowerCase(), "", "", page, 20);
    }

    private CachedRestaurantSearchResult result(long restaurantId) {
        CachedRestaurantSearchResult.Item item = new CachedRestaurantSearchResult.Item(
                restaurantId, "밥풀식당", "제주시", "한식", "맛집", 10000, null);
        return new CachedRestaurantSearchResult(List.of(item), 0, 20, 1, 1);
    }

    private RestaurantSearchCacheStore store(long ttlSeconds) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return new RestaurantSearchCacheStore(redisTemplate, JsonMapper.builder().build(), ttlSeconds);
    }
}
