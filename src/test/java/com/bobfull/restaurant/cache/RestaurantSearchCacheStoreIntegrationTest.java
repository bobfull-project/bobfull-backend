package com.bobfull.restaurant.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
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

    @Test
    void Redis가_연결은_받지만_응답하지_않으면_설정된_command_timeout_이내에_실패한다() throws Exception {
        try (ServerSocket blackhole = new ServerSocket(0)) {
            Thread acceptThread = new Thread(() -> {
                try {
                    while (!blackhole.isClosed()) {
                        blackhole.accept();
                        // 연결은 받아주지만 응답은 절대 보내지 않는다(명령 자체가 멈춘 것처럼 재현).
                    }
                } catch (IOException ignored) {
                    // 테스트 종료로 소켓이 닫히면 accept()가 예외를 던진다 — 정상 종료.
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(2))
                    .build();
            LettuceConnectionFactory blackholeFactory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration("localhost", blackhole.getLocalPort()), clientConfiguration);
            blackholeFactory.afterPropertiesSet();
            try {
                StringRedisTemplate redisTemplate = new StringRedisTemplate(blackholeFactory);
                redisTemplate.afterPropertiesSet();
                RestaurantSearchCacheStore store =
                        new RestaurantSearchCacheStore(redisTemplate, JsonMapper.builder().build(), 60);

                long start = System.nanoTime();
                assertThat(store.find(key("블랙홀", 0))).isEmpty();
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                // 설정한 command timeout(2초) 근처에서 실패해야 한다 — DB 조회보다 오래 걸리는
                // 무한 대기가 되지 않는다는 것을 실측으로 확인한다(Issue #62 Q2, PR #202 리뷰 반영).
                assertThat(elapsedMs).isLessThan(5_000L);
            } finally {
                blackholeFactory.destroy();
            }
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
