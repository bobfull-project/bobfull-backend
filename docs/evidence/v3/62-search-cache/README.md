# Issue #62 검색 Redis Cache 필요성 판단 및 제한적 적용 Evidence

## 검증 대상

- `GET /api/restaurants` (`RestaurantService.searchRestaurants`) — date/time이 없는 조합(기본/keyword/category/정렬/pagination)만 대상.
- date/time이 있는 검색은 이번 Issue의 캐시 대상에서 제외한다(아래 "제외 범위" 참고). #61 After 코드가 그대로 적용된다.

## 측정 계약

- Primary KPI: 반복 검색 시 요청당 DB Query 수(0으로 감소하는지), 동시 요청 시 DB Connection Pool active/awaiting.
- Secondary KPI: HTTP 왕복 지연시간(p50/p95/max, 단일 요청/JDK HttpClient 기준), Cache Hit/Miss 여부.
- Guardrail: `availableCapacity`·결제·예약 정합성 핵심 값은 캐시하지 않는다. Redis 장애가 검색 API 자체를 막지 않는다(Fail-open). Restaurant 변경 후 캐시된 검색 결과가 갱신된다(무효화).
- 이번 Issue는 #61에서 이미 낮춘 SQL 실행 계획 자체를 다시 검증하지 않는다 — DB Query/Connection Pool 수준의 반복 부하만 본다.

## 기준 코드

- Before SHA: `b482607`(`develop` 최신, 이 Issue 브랜치 `feature/62-search-redis-cache`의 분기점. `#61`의 Track A/B 개선이 모두 반영된 상태)
- #61 결과 링크: `docs/evidence/v3/61-search-query/README.md`
- After SHA: `a6a681a`(PR #202 리뷰의 MAJOR 2건·MINOR 1건 반영 포함. 최초 구현은 `c86da4f`)

## 환경·데이터·실행 조건

- MySQL: `mysql:8.4`(버전 8.4.10) 전용 Docker 컨테이너(`bobfull-perf-mysql`, 포트 33061). Redis: `redis:7-alpine` 전용 Docker 컨테이너(`bobfull-perf-redis`, 포트 63790). 둘 다 개발 인스턴스와 완전히 분리했고 측정 후 폐기했다.
- 애플리케이션: 로컬 1 인스턴스, 기본 HikariCP(pool size 10, 튜닝 없음), `spring.jpa.open-in-view=false`(운영과 동일, `application-prod.yml`/`application-local.yml` 기준).
- 데이터 규모: Restaurant 5,000건(각 category 한식/일식 절반, keyword 1/3은 "카페,제주" 나머지는 고유값, name 250건당 1건 "제주맛집" 포함) — #61 Track A와 동일 규모.
- 실행 방법 재현: `src/test/java/com/bobfull/restaurant/controller/RestaurantSearchNoCacheBaselineInvestigationTest.java`(시나리오 A, `BOBFULL_MYSQL_PERF_TEST=true`만 필요), `src/test/java/com/bobfull/restaurant/controller/RestaurantSearchCacheEffectInvestigationTest.java`(시나리오 B/C/D/F, `BOBFULL_MYSQL_PERF_TEST=true` + `BOBFULL_TEST_REDIS_HOST`/`PORT` 필요), `src/test/java/com/bobfull/restaurant/cache/RestaurantSearchCacheStoreIntegrationTest.java`(시나리오 E, `BOBFULL_REDIS_INTEGRATION_TEST=true`만 필요, Spring 컨텍스트 없이 컴포넌트 단위).
- No Cache 기준선은 캐시 코드가 아직 없던 시점(Before SHA)의 코드로 측정했다. 캐시를 "코드는 있지만 끈 상태"로 재측정한 것이 아니다 — 이번 구현은 별도 on/off 플래그를 두지 않았다(아래 "결과 해석" 참고).

## Before 결과 — 시나리오 A: No Cache 기준선

원본: `RestaurantSearchNoCacheBaselineInvestigationTest` 실행 로그(수치만 기록)

- 순차 반복(N=50, 동일 `keyword=맛집` 검색): **p50=7ms, p95=11ms, p99=16ms, max=16ms**. 요청당 평균 쿼리 수 2.00(content+count, #61에서 이미 확인한 그대로).
- 순차 종료 후 Pool: active=0, idle=10, awaiting=0, total=10 — 여유 있음.
- **동시 반복(30 threads × 5 = 150 요청, 동일 조건)**: 최대 active=**10**(Pool 전체 사용), **최대 awaiting(대기)=20**, p50=**32ms**, p95=**43ms**, p99=53ms, max=56ms. 요청당 평균 쿼리 수 2.00(변화 없음, 매 요청 DB를 다시 조회).

핵심 발견: #61 이후에도 "동일 검색이 반복되는 상황"에서는 실제 병목이 남아 있었다 — SQL 자체는 빠르지만(#61 EXPLAIN ANALYZE 기준 수 ms), 기본 HikariCP Pool(10개)로는 동시 30개 요청을 감당하지 못해 최대 20개 스레드가 Connection을 기다렸고, 그 결과 p50이 순차 대비 약 4.6배(7ms→32ms) 늘었다. 이것이 이번 Issue의 "Cache 적용 가치 판단"에서 실제 병목으로 인정한 근거다.

## 변경 내용

### Cache 대상과 제외 범위

- 대상: date/time이 없는 검색(기본/keyword/category/정렬/pagination). `RestaurantSearchCacheKey.isCacheEligible(request)`가 `date == null && time == null`일 때만 캐시를 사용한다.
- 제외: date/time이 있는 검색. TimeSlot 변경(회차 추가/삭제/시간 변경)도 검색 결과에 영향을 주는데, 이번 Issue의 무효화는 Restaurant 변경만 추적한다. TimeSlot까지 무효화 대상에 포함하려면 추적 범위가 커져 이번 최소 범위를 넘어선다 — 후속 검토 대상으로 명시한다.
- 제외(항상): `availableCapacity`, 현재 Participant 수, READY Payment 임시 선점 수, 예약/결제/환불 성공 여부 — 이번 Cache는 식당 목록 검색 결과에만 적용되고 이 값들을 다루지 않는다(회차 조회는 이번 Issue의 캐시 대상이 아니다).

### Cache Key

- `RestaurantSearchCacheKey`: `keyword`(trim+lowercase), `category`(trim+lowercase), `sort`(속성,방향 목록), `page`, `size`를 정규화해 SHA-256 해시로 축약한다. JWT·회원 개인정보·requestId·불필요한 timestamp는 key에 포함하지 않는다.
- 실제 Redis key: `bobfull:search:restaurants:v1:{version}:{digest}` — `v1`은 스키마 버전(캐시 페이로드 구조가 바뀌면 올린다), `{version}`은 아래 무효화용 버전 번호다.

### TTL과 무효화

- TTL: 60초(`restaurant.search-cache.ttl-seconds`, 기본값). 근거: OWNER의 식당 정보 수정은 낮은 빈도의 수동 작업이라 최대 60초 지연 노출은 사용자 경험에 실질적 영향이 적고, 60초는 이번 측정에서 확인한 반복 검색 burst(수 초~수십 초 단위) 구간을 충분히 커버한다. 정확한 "최적" TTL을 이론적으로 도출할 데이터는 없어 근사값으로 시작했다는 한계가 있다(아래 "검증 한계" 참고).
- 무효화 방식: 개별 key 삭제 대신 **버전 번호(namespace 방식)**. 해시된 key는 역추적이 불가능해 "이 Restaurant 변경이 어떤 캐시 key에 영향을 주는지"를 알 수 없다. `RestaurantService.register/update/delete`가 성공하면 `RestaurantSearchCacheStore.bumpVersion()`을 호출해 이후 조회부터 새 버전 key를 쓰게 만든다. 이전 버전 key는 명시적으로 지우지 않고 TTL로 자연 소멸한다(최대 60초간 메모리에 남지만 더 이상 조회되지 않는다).
- date/time이 있는 검색이 캐시 대상이 아니므로 TimeSlot 변경은 무효화 트리거에 포함하지 않는다.

### Redis 인프라 재사용과 책임 분리(Human 결정 Q3)

- 기존 인증 Redis(`RefreshTokenStore`, `AccessTokenBlacklistStore`, key prefix `auth:`)와 같은 Redis 인스턴스를 재사용한다.
- 검색 캐시는 `bobfull:search:` prefix로 완전히 분리했다. serializer는 `StringRedisTemplate` + `tools.jackson.databind.ObjectMapper`(JSON), 인증 쪽과 동일한 `StringRedisTemplate` 계열이라 별도 serializer 충돌이 없다.
- 장애 영향 분리: `RestaurantSearchCacheStore`는 Redis 예외(연결 실패 등)를 전부 삼키고 로그만 남긴다(Fail-open, Human 결정 Q2). 인증 Redis(`AccessTokenBlacklistStore` 조회는 Fail-open, `RefreshTokenStore`는 Fail-closed로 예외 전파)와는 독립적으로 동작하며, 검색 캐시 장애가 로그인·토큰 기능에 영향을 주지 않고 반대로도 마찬가지다 — 각자 다른 클래스, 다른 key prefix, 별도 예외 처리라 서로 결합돼 있지 않다.

### 성능 관련 부수 발견과 수정

- **`@Transactional` 제거**: `RestaurantService.searchRestaurants`에 `@Transactional(readOnly = true)`가 있으면, 캐시 Hit이라 DB를 전혀 조회하지 않아도 메서드에 진입하는 순간 Hikari Connection을 열고 닫는 것을 실측으로 확인했다(아래 "핵심 트러블슈팅" 참고). 제거 후 동시 Warm Hit 시나리오의 Pool 점유가 0으로 떨어졌다.
- **`RestaurantSearchRepositoryImpl.search()`에 명시적 `@Transactional(readOnly = true)` 추가(PR #202 리뷰 반영)**: 처음에는 "Spring Data JPA 저장소 프록시가 자체적으로 트랜잭션을 연다"고 판단해 바깥 메서드의 트랜잭션만 제거했는데, 리뷰에서 이 커스텀 fragment 구현(`RestaurantSearchRepositoryImpl`)은 `SimpleJpaRepository`를 상속하지 않아 그 기본 트랜잭션 advice가 자동으로 적용되지 않는다는 지적을 받았다. 이 경로가 실제로 트랜잭션 없이 실행되면 `contentQuery`와 `countQuery`가 서로 다른 시점의 데이터를 볼 수 있다(동시 쓰기 개입 시). 이를 막기 위해 `search()` 메서드 자체에 `@Transactional(readOnly = true)`를 명시적으로 추가해, 두 쿼리가 항상 하나의 읽기 전용 트랜잭션 안에서 실행되도록 보장했다. 이 트랜잭션은 캐시 Hit 경로와는 무관하다(Hit이면 이 메서드 자체를 호출하지 않는다) — 그래서 Warm Hit의 Pool 미점유(0/0)는 그대로 유지된다.
- **검색 캐시 버전 증가를 트랜잭션 커밋 후로 이동(PR #202 리뷰 반영)**: 처음 구현은 `register/update/delete`의 DB 트랜잭션이 커밋되기 *전에* `RestaurantSearchCacheStore.bumpVersion()`을 호출했다. 리뷰에서 이 순서가 실제로 경쟁을 유발한다는 지적을 받았다 — 자세한 시나리오는 아래 "핵심 트러블슈팅 2" 참고. `deletePreviousImageAfterCommit`과 같은 방식(`TransactionSynchronization.afterCommit`)으로 옮겨, DB 변경이 실제로 커밋된 뒤에만 버전을 올리도록 고쳤다.

## After 결과 — 시나리오 B/C/D: Cold/Warm/Mixed

원본: `RestaurantSearchCacheEffectInvestigationTest` 실행 로그(수치만 기록)

| 시나리오 | 요청당 DB 쿼리 수 | 지연시간 | Pool(동시 30×5) |
|---|---:|---|---|
| Cold(최초 1회, Miss) | 2(Before와 동일) | 12~13ms | — |
| Warm(순차 20회 반복, Hit) | **0** | 평균 3~4ms | — |
| Warm 동시(30×5=150, 전부 동일 key, Hit) | 0(합계) | p50=**10ms**, p95=14ms, max=19ms | 최대 active=**0**, 최대 awaiting=**0** |
| Mixed(서로 다른 5개 key, 1차 전부 Miss) | 10(=2×5) | — | — |
| Mixed(같은 5개 key, 2차 전부 Hit) | **0** | — | — |

핵심 비교(No Cache 기준선 vs Cache 적용, 동시 30×5=150 동일 조건):

| 지표 | No Cache | Cache(Warm Hit) | 변화 |
|---|---:|---:|---|
| p50 | 32ms | 10ms | 약 69% 감소 |
| p95 | 43ms | 14ms | 약 67% 감소 |
| max | 56ms | 19ms | 약 66% 감소 |
| Pool 최대 active | 10/10(전체 사용) | 0/10 | Pool 점유 완전히 사라짐 |
| Pool 최대 awaiting(대기) | 20 | 0 | 대기 완전히 사라짐 |
| 요청당 DB 쿼리 수 | 2(항상) | 0(Hit 시) | Hit 시 DB 완전히 우회 |

### 시나리오 F: 무효화(Cache stale 확인)

- `RestaurantService.update`로 Restaurant의 `depositPerPerson`을 10000→20000으로 변경한 뒤 같은 검색(같은 keyword)을 즉시 재조회하면 새 값(20000)이 바로 반영되고 이전 값(10000)은 더 이상 나타나지 않는다(`RestaurantSearchCacheEffectInvestigationTest#Restaurant_수정_후_같은_검색_결과가_최신값으로_갱신된다` PASS).
- 이는 TTL 만료를 기다리지 않고 버전 무효화로 즉시 반영된 것이다 — 무효화 정책이 문서 계약(namespace/version 방식)과 일치함을 확인했다.

### 시나리오 E: Redis 장애

원본: `RestaurantSearchCacheStoreIntegrationTest`(5개 테스트 모두 PASS)

- 존재하지 않는 포트(연결 자체가 실패하는 상황)로 `find`/`put`/`bumpVersion`을 호출해도 예외가 전파되지 않고 각각 빈 결과/no-op으로 처리됐다(Fail-open, Human 결정 Q2). 이 컴포넌트는 실제 HTTP 경로(`RestaurantService.searchRestaurants`)가 그대로 사용하는 클래스이므로, Redis 전체 장애 시에도 검색 API는 항상 DB 경로로 정상 응답한다(추가로 HTTP 레벨 전체 장애 재현은 하지 않았다 — 아래 "검증 한계" 참고).
- **Redis 명령 timeout 설정(PR #202 리뷰 반영)**: 처음에는 `spring.data.redis.timeout`을 설정하지 않아 Lettuce 기본값(command timeout 60초)을 그대로 썼다 — Redis가 연결은 받아주지만 응답하지 않는 상황(네트워크 블랙홀)에서는 요청이 최대 60초까지 걸릴 수 있어, "Redis timeout 때문에 DB보다 더 오래 대기하지 않는다"(Issue #62 Q2)는 계약을 실제로는 지키지 못하는 경로였다. `application-prod.yml`/`application-local.yml.example`에 `spring.data.redis.timeout: 2000ms`(환경변수 `REDIS_TIMEOUT`로 재정의 가능)를 명시했다. `RestaurantSearchCacheStoreIntegrationTest`에 연결은 받아주되 응답은 절대 보내지 않는 소켓(블랙홀)을 만들어 재현한 결과, 설정한 2초 근처에서 실제로 실패함을 확인했다(5초 이내 완료를 assert, PASS). 이 timeout은 인증 Redis(`RefreshTokenStore`/`AccessTokenBlacklistStore`)와 같은 `RedisConnectionFactory`를 공유해 전체 Redis 사용에 적용된다.

## 핵심 트러블슈팅

**증상**: Cache를 Hit해서 DB 쿼리가 0번인데도, 동시 30개 요청에서 HikariCP Pool의 active/awaiting이 여전히 최대치(10/20)를 찍었다.

**진단**: `RestaurantService.searchRestaurants`에 붙어 있던 `@Transactional(readOnly = true)`가 원인이었다. Spring이 이 메서드를 트랜잭션으로 감싸면, 실제로 SQL을 한 줄도 실행하지 않아도 트랜잭션을 시작·커밋하는 것만으로 Hikari Connection을 체크아웃·반납한다. Cache Hit 경로는 Redis만 조회하고 리턴하는데도 이 오버헤드를 피할 수 없었다.

**해결(1차)**: `searchRestaurants`에서 `@Transactional`을 제거했다. 제거 후 재측정한 결과 Pool 점유가 0/0으로 떨어졌다(위 "After 결과" 표). 다만 이 1차 해결에서 "이 메서드가 DB에 접근하는 유일한 경로(`restaurantRepository.search`)는 Spring Data JPA 저장소 프록시가 자체적으로 트랜잭션을 연다"고 판단해 그 경로의 트랜잭션 보장을 별도로 추가하지 않았는데, 이는 부정확했다(아래 트러블슈팅 2 참고).

**해결(2차, PR #202 리뷰 반영)**: 리뷰에서 `RestaurantSearchRepositoryImpl`은 커스텀 fragment 구현이라 `SimpleJpaRepository`의 기본 트랜잭션 advice를 상속받지 않는다는 지적을 받았다. `search()` 메서드에 명시적으로 `@Transactional(readOnly = true)`를 추가해 `contentQuery`/`countQuery`가 항상 하나의 트랜잭션에서 실행되도록 고쳤다. 이 트랜잭션은 캐시 Miss(또는 date/time 검색)에서만 열리고 Cache Hit 경로와는 무관해, Warm Hit의 Pool 미점유(0/0)는 그대로 유지된다.

## 핵심 트러블슈팅 2 — 검색 캐시 버전 무효화의 커밋 전/후 경쟁

**증상(리뷰에서 발견)**: 처음 구현은 `register/update/delete`의 DB 트랜잭션이 커밋되기 전에 `RestaurantSearchCacheStore.bumpVersion()`을 호출했다. 단일 스레드 순차 테스트(`Restaurant_수정_후_같은_검색_결과가_최신값으로_갱신된다`)는 이 문제를 잡지 못했다 — `update()` 호출 자체가 `@Transactional`이라 메서드가 반환할 때는 이미 커밋까지 끝난 뒤였기 때문이다.

**진단**: 실제로는 다음 순서가 가능했다.

1. Restaurant update 트랜잭션이 엔티티를 메모리에서 변경(아직 DB에 반영 전일 수 있음, flush는 커밋 시점)
2. **DB commit 전** `bumpVersion()`이 Redis 버전을 `N`→`N+1`로 올림
3. 동시 검색 요청이 버전 `N+1`로 캐시를 조회 → Miss(아직 아무도 이 버전으로 저장한 적 없음)
4. 그 검색이 DB를 조회하는데, update 트랜잭션이 아직 커밋 전이라 **격리 수준에 따라 이전 값**을 읽음
5. 그 이전 값을 버전 `N+1`(현재 버전) 아래 다시 캐시에 저장
6. update 트랜잭션 commit
7. 이후 요청들은 버전 `N+1`의 이 stale 캐시를 TTL(60초) 동안 "최신"으로 오인해 Hit

이러면 "Restaurant 변경 후 캐시된 검색 결과가 갱신된다"는 Evidence의 Guardrail이 최악의 경우 최대 60초간 깨질 수 있었다.

**해결**: `bumpVersion()` 호출을 `deletePreviousImageAfterCommit`과 동일한 패턴(`TransactionSynchronizationManager`의 `afterCommit()`)으로 옮겼다. DB 커밋이 실제로 끝난 뒤에만 버전을 올리므로, 그 시점 이후에 시작하는 모든 DB 조회는 이미 커밋된 최신 값을 본다 — 위 3번 단계에서 옛 값을 다시 캐시할 방법이 없어진다. `RestaurantServiceTest`에 `TransactionSynchronizationManager.initSynchronization()`으로 트랜잭션 동기화를 흉내 내어, 커밋 콜백을 실행하기 전에는 `bumpVersion()`이 호출되지 않고 콜백 실행 후에만 호출됨을 검증하는 테스트 3개(register/update/delete)를 추가했다.

## 정합성 회귀 검증

- 전체 테스트: `./gradlew clean :test` → **726개 중 726 PASS, 0 실패, 0 에러**(41개는 환경변수 게이트 통합 테스트로 스킵, 실패 아님).
- 검색 캐시 버전 무효화가 트랜잭션 커밋 후에만 실행되는지: `RestaurantServiceTest`에 register/update/delete 각각 "트랜잭션 안에서 ~하면 검색 캐시 버전 증가는 커밋 후에만 실행된다" 테스트 3개 추가, 모두 PASS(위 "핵심 트러블슈팅 2" 참고).
- Redis 명령 timeout이 실제로 적용되는지: `RestaurantSearchCacheStoreIntegrationTest`에 응답 없는 소켓(블랙홀)을 만들어 2초 command timeout 근처에서 실패함을 확인, PASS.
- 검색 결과: 캐시된 응답도 매번 presigned S3 URL을 새로 생성한다(`toPageResponse`가 캐시 히트 시에도 `restaurantImageService.createGetUrl(item.imageKey())`를 다시 호출) — presigned URL(기본 5분 만료)이 캐시 TTL(60초)보다 오래 노출되는 문제를 피했다. `RestaurantServiceTest`에 이 경로를 검증하는 테스트를 추가했다.
- date/time이 있는 검색은 캐시를 조회·저장하지 않는다(`RestaurantServiceTest#date나_time이_있는_검색은_캐시를_조회하거나_저장하지_않는다` PASS) — #61 After 코드 그대로 동작한다.
- `RestaurantService.register/update/delete` 성공 시 `bumpVersion()`이 호출된다(각각 테스트로 확인).
- 기존 `RestaurantServiceTest`의 검색 관련 테스트(`사용자용_식당_검색은_공개_목록_응답으로_변환한다`)는 캐시 로직 추가 후에도 그대로 PASS — 캐시 Miss 경로에서도 기존 응답 형태·이미지 URL 매핑이 동일함을 확인했다.

## 구조화 로그·메트릭

- Cache 조회·저장·버전 증가 실패는 `event=RESTAURANT_SEARCH_CACHE_READ_FAILED|WRITE_FAILED|VERSION_BUMP_FAILED` 형태로 로그에 남긴다(`RestaurantSearchCacheStore`).
- 별도 Cache Hit/Miss Counter 메트릭(Micrometer)은 이번 구현에 추가하지 않았다 — Hibernate Statistics 기반 쿼리 수 측정으로 Hit/Miss를 간접 확인했다. Prometheus에 Hit Ratio를 직접 노출하는 것은 #64 모니터링 고도화와 연계해 후속 검토한다(아래 "검증 한계" 참고).

## 다중 인스턴스 고려

- 현재는 단일 인스턴스 환경이다. Redis Cache를 도입하면 자연히 공유 Cache로 동작한다(로컬 인메모리 Cache가 아니라 처음부터 Redis를 썼기 때문에 인스턴스가 늘어나도 결과가 갈리지 않는다).
- serializer/version: `bobfull:search:restaurants:v1:...`의 `v1`이 페이로드 스키마 버전이다. `CachedRestaurantSearchResult`의 필드 구조가 바뀌면 `v1`을 올려 이전 인스턴스가 쓴 캐시와 섞이지 않게 한다(현재는 배포마다 이 값을 수동으로 올려야 한다 — 자동화하지 않았다는 한계가 있다).
- #169(다중 EC2) 전환 시 재검토 항목: (1) 배포 중 신·구 인스턴스가 동시에 존재하는 롤링 배포 상황에서 `v1` 스키마 버전이 실제로 안전한 하위 호환을 보장하는지, (2) TTL·무효화 타이밍이 인스턴스 간 시계 차이에 영향받지 않는지(Redis TTL은 Redis 서버 기준이라 애플리케이션 시계와 무관 — 영향 없음으로 판단).

## 도입 판단

**도입** — 제한적 범위(date/time 없는 검색만, TTL 60초, 버전 기반 무효화).

근거:

- #61 이후에도 동일 검색이 동시에 반복되는 상황에서 DB Connection Pool이 실제로 포화됨을 실측으로 확인했다(No Cache: 최대 active 10/10, awaiting 20).
- Cache 적용 후 그 포화가 완전히 사라졌고(0/0) latency도 p50 기준 약 69% 감소했다.
- Cold/Warm/Mixed/무효화/Redis 장애 시나리오 모두 실제로 재현·검증했고 정합성 회귀가 없다.
- 좌석·결제 정합성 핵심 값은 캐시 대상에서 제외했고, Redis 장애가 검색 API 자체를 막지 않는다(Fail-open).

## 검증 한계

- No Cache 기준선은 캐시 코드가 아직 없던 시점의 코드로 측정했다. on/off 플래그로 같은 배포에서 즉시 전환 비교한 것이 아니다 — 두 측정 사이에 Docker 컨테이너를 새로 띄웠다는 차이가 있다(둘 다 같은 시딩 코드·같은 규모의 새 컨테이너라 실질적 차이는 없다고 판단했지만, 완전히 동일한 프로세스 내 A/B는 아니다).
- 데이터 규모는 Restaurant 5,000건 1종만 측정했다(#61과 동일 규모 재사용). 더 큰 규모에서 Hit Ratio·메모리 사용량이 어떻게 달라지는지는 확인하지 않았다.
- 실제 운영 트래픽의 "반복 조회 비율"(동일 조건이 실제로 얼마나 자주 반복되는지)은 측정하지 못했다 — 이번 측정은 인위적으로 100% 동일 조건(Warm)과 완전히 다른 조건(Mixed 1차)의 두 극단만 봤다. 실제 반복률이 낮으면 Hit Ratio도 낮아 이번에 관측한 개선이 그대로 나타나지 않을 수 있다.
- TTL 60초는 근사값이다 — 실제 운영에서 식당 정보 변경 빈도·검색 반복 빈도를 지켜본 뒤 조정이 필요할 수 있다.
- Stampede(동일 hot key 만료 직후 동시 요청 몰림) 시나리오는 별도로 재현하지 않았다. 이번 규모(5,000건, 30 동시 요청)에서는 Cold Miss 자체도 12ms 수준으로 빨라(#61 개선 덕분) 만료 순간 동시 Miss가 몰려도 DB가 감당 못할 정도로 커지지 않을 것으로 판단하지만, 실제로 재현해 확인하지는 않았다. single-flight·TTL jitter 등은 도입하지 않았다.
- Redis 장애는 컴포넌트 단위(`RestaurantSearchCacheStore`)로 확인했고(연결 실패·명령 timeout 둘 다), 실제 HTTP 요청이 Redis 전체 장애 상황에서 어느 정도의 latency로 응답하는지는 end-to-end(전체 Spring 컨텍스트+실제 HTTP)로 재현하지 않았다. 컴포넌트 단위 결과(연결 실패는 즉시, 명령 timeout은 설정한 2초 근처)가 HTTP 경로에서도 그대로 유지될 것으로 판단하지만, HTTP 레벨에서 직접 측정하지는 않았다.
- Cache Hit/Miss를 Prometheus 메트릭으로 노출하지 않았다 — 운영 중 실제 Hit Ratio 관측은 로그 기반 분석이나 후속 Issue가 필요하다.
- date/time이 있는 검색의 캐시는 이번 Issue에서 다루지 않았다(TimeSlot 변경 무효화 추적 필요, 별도 후속 검토).
- 인덱스 스키마 버전(`v1`) 갱신은 수동이다 — 페이로드 구조 변경 시 사람이 직접 올려야 한다.

## 재현 명령

```bash
docker run -d --name bobfull-perf-mysql -e MYSQL_ROOT_PASSWORD=perfpass \
  -e MYSQL_DATABASE=bobfull_perf -p 33061:3306 mysql:8.4 --sql_mode=STRICT_TRANS_TABLES
docker run -d --name bobfull-perf-redis -p 63790:6379 redis:7-alpine

BOBFULL_MYSQL_PERF_TEST=true \
BOBFULL_TEST_MYSQL_URL="jdbc:mysql://localhost:33061/bobfull_perf?useSSL=false&allowPublicKeyRetrieval=true" \
BOBFULL_TEST_MYSQL_USERNAME=root \
BOBFULL_TEST_MYSQL_PASSWORD=perfpass \
./gradlew :test --tests "com.bobfull.restaurant.controller.RestaurantSearchNoCacheBaselineInvestigationTest" --info

BOBFULL_MYSQL_PERF_TEST=true \
BOBFULL_TEST_MYSQL_URL="jdbc:mysql://localhost:33061/bobfull_perf?useSSL=false&allowPublicKeyRetrieval=true" \
BOBFULL_TEST_MYSQL_USERNAME=root \
BOBFULL_TEST_MYSQL_PASSWORD=perfpass \
BOBFULL_TEST_REDIS_HOST=localhost \
BOBFULL_TEST_REDIS_PORT=63790 \
./gradlew :test --tests "com.bobfull.restaurant.controller.RestaurantSearchCacheEffectInvestigationTest" --info

BOBFULL_REDIS_INTEGRATION_TEST=true \
BOBFULL_TEST_REDIS_HOST=localhost \
BOBFULL_TEST_REDIS_PORT=63790 \
./gradlew :test --tests "com.bobfull.restaurant.cache.RestaurantSearchCacheStoreIntegrationTest"
```

## 관련

- Issue: #62
- PR: (구현 완료 후 연결)
- ADR: 불필요(제한적 실험, Human 결정 Q5). 장기 공통 캐시 정책으로 확정되면 재검토.
- Troubleshooting: `@Transactional` 제거 관련 트러블슈팅(위 "핵심 트러블슈팅" 참고, 별도 문서는 필요 시 후속 작성)
