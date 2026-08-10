# Issue #61 식당·회차 조회 SQL 실행계획 분석 및 쿼리·인덱스 개선 Evidence

## 검증 대상

- Track A: `GET /api/restaurants` → `RestaurantSearchRepositoryImpl.search(...)`
- Track B: 예약 가능 회차 조회 → `TimeSlotService.getAvailableDiningSessions(...)` (내부에서 `AvailableCapacityCalculator.calculate` 호출)

## 기준 코드

- Before SHA: `6f3ea78` (`develop` 최신, 이 Issue 브랜치 `feature/61-search-query-performance`의 분기점)
- After SHA: 이 Evidence를 포함한 커밋(PR 본문에 기록)

## 환경·데이터·실행 조건

- MySQL: `mysql:8.4` Docker 이미지, 버전 `8.4.10`. 개발 DB(`bobfull-mysql`, 포트 3307)와 완전히 분리된 전용 컨테이너(`bobfull-perf-mysql`, 포트 33061, DB `bobfull_perf`)를 이번 Issue 전용으로 새로 띄워 사용했다. 컨테이너는 Evidence 작성 후 폐기한다.
- 애플리케이션: 로컬 1 인스턴스, 기본 HikariCP 설정(별도 튜닝 없음). 부하 테스트(K6, #63)는 이번 Issue 범위가 아니며 단일 요청/단일 스레드 측정만 수행했다.
- `spring.jpa.hibernate.ddl-auto=update`(운영과 동일한 방식, Human 결정 Q3)로 스키마를 반영했다.
- Track A 데이터: Restaurant 5,000건, 각 Restaurant당 SharedTable 1건(총 5,000건), 각 SharedTable당 TimeSlot 2건(총 10,000건). keyword "맛집"이 실제로 포함된 Restaurant는 250건당 1건(20건/5,000건). category는 20종 균등 분포(`perf-category-0`~`perf-category-19`, 각 250건). TimeSlot의 `start_at`은 30일에 걸쳐 균등 분포(day = `i % 30`), 시(hour)도 24시간에 걸쳐 균등 분포(`i % 24`)시켜 date 필터는 약 1/30, time 필터는 약 1/24 선택도를 갖도록 구성했다. Reservation/ReservationParticipant/Payment는 Track A에는 사용하지 않았다(Track A는 `restaurant`·`shared_table`·`time_slot`만 조회).
- Track B 데이터: Restaurant 1건, SharedTable 1건, TimeSlot 20건, 각 TimeSlot마다 RECRUITING 상태 Reservation 1건 + ReservationParticipant 1건(partySize=2). READY Payment는 이번 측정에 포함하지 않았다(포함해도 duplicate 쿼리 구조 자체는 동일하게 나타난다).
- 데이터 규모는 `small`(Track B, 20 TimeSlot) 1종, `medium`(Track A, 5,000 Restaurant) 1종만 측정했다. 더 큰 규모(예: 수십만 Restaurant)는 이번 Issue에서 측정하지 않았다는 한계가 있다(아래 "검증 한계" 참고).
- 실행 방법 재현: `src/test/java/com/bobfull/timeslot/service/AvailableDiningSessionQueryCountInvestigationTest.java`, `src/test/java/com/bobfull/restaurant/repository/RestaurantSearchExplainInvestigationTest.java`. 둘 다 `BOBFULL_MYSQL_PERF_TEST=true` + `BOBFULL_TEST_MYSQL_URL`/`BOBFULL_TEST_MYSQL_USERNAME`/`BOBFULL_TEST_MYSQL_PASSWORD` 환경변수가 있을 때만 실행되며, `@AfterEach`에서 자신이 만든 데이터만 정리한다(개발 DB를 절대 가리키지 않아야 한다).

## Before 결과

### Track A — 필터별 실제 생성 SQL과 EXPLAIN ANALYZE

원본: [`raw/explain-before-trackA.txt`](raw/explain-before-trackA.txt)

| 조건 | 요청당 SQL 수 | 실제 사용 Index | full scan / filesort / temporary | actual time |
|---|---:|---|---|---:|
| 기본(필터 없음) | 2(content+count) | `PRIMARY`(index scan, ORDER BY+LIMIT로 조기 종료) | 없음 | 1.21ms |
| keyword LIKE(선두 와일드카드) | 2 | `PRIMARY`(index scan) | 없음(이 규모에서는 PK 순서+LIMIT로 조기 종료되어 전체 스캔까지 가지 않음) | 1.82ms |
| category 등치 | 2 | `PRIMARY`(index scan) | 없음 | 0.075ms |
| date 필터(3-way join) | 2 | `shared_table`: 없음(PRIMARY 외 인덱스 부재) / `time_slot`: `uk_time_slot_active_start` / `restaurant`: `PRIMARY` | **`shared_table` full scan(5,000행 전체) + Using temporary + Using filesort** | 11.5ms |
| time 필터(hour/minute, date 없음) | 2 | 위와 동일 | **`shared_table` full scan(5,000행 전체) + Using temporary + Using filesort** | 12.1ms |

핵심 발견: 처음 가설이었던 "`LIKE '%keyword%'` 선두 와일드카드"와 "`hour()/minute()` 함수 래핑"은 이 규모(5,000건)에서 실제 병목이 **아니었다**.

- keyword LIKE: `ORDER BY restaurant_id LIMIT 20`이 있어 옵티마이저가 `PRIMARY` 인덱스 순서로 스캔하며 조건을 만족하는 20건을 찾는 즉시 멈춘다. `actual rows=4751`(약 95% 스캔)까지 갔지만 이는 매치가 20건뿐이라 대부분을 훑어야 했던 것이지, 인덱스를 못 써서가 아니다. 매치가 0건에 가까울수록 이 방식은 전체 스캔에 근접하게 나빠질 수 있다는 위험은 남아 있으나, 이번 측정에서 확정 병목으로 판정하지 않는다(아래 "검증 한계" 참고).
- hour/minute 함수 래핑: `uk_time_slot_active_start(shared_table_id, active_start_at)`의 선두 컬럼이 `shared_table_id`라서, join으로 `shared_table_id`가 고정된 뒤에는 후보가 평균 2행뿐이라 함수 조건을 인덱스 없이 걸러도 비용이 미미하다.
- 실제 병목은 **`shared_table`에 `restaurant_id`용 인덱스가 전혀 없어, date/time 필터가 있는 3-way join마다 `shared_table` 전체를 풀스캔**하는 것이었다. 선택도(1/30, 1/24)와 무관하게 매 요청 고정 비용으로 발생한다.

### Track B — 예약 가능 회차 조회 쿼리 수

원본: 아래 "재현 명령" 실행 로그(수치만 기록, 전체 로그는 Git에 커밋하지 않음)

- TimeSlot 20건 조회에 실행된 SQL PreparedStatement 수: **123개**(TimeSlot당 평균 6.15개)
- 원인: `TimeSlotService.toAvailableDiningSessionResponse`가 활성 Reservation 조회 1회 + 참여자 합계 조회 1회를 실행한 뒤, `AvailableCapacityCalculator.calculate`가 CLOSED 여부 조회 1회 + **동일한 활성 Reservation 조회 1회(중복)** + **동일한 참여자 합계 조회 1회(중복)** + READY 선점 합계 조회 1회를 다시 실행한다. `ACTIVE_RESERVATION_STATUSES`/`OCCUPYING_PARTICIPATION_STATUSES`(TimeSlotService)와 `ACTIVE_STATUSES`/`OCCUPYING_STATUSES`(AvailableCapacityCalculator)가 완전히 동일한 값이라 진짜 중복임을 코드로 확인했다.

## 변경 내용

### Track B — Query 수준 (구현)

- `AvailableCapacityCalculator`에 `calculateWithKnownParticipantCount(timeSlotId, tableCapacity, currentParticipantCount)` 오버로드를 추가했다. 기존 `calculate(timeSlotId, tableCapacity)`는 `ReservationPreparationService`처럼 참여자 합계를 아직 모르는 호출자를 위해 그대로 유지한다.
- `TimeSlotService.toAvailableDiningSessionResponse`는 이미 계산해 둔 `currentParticipantCount`를 새 오버로드에 그대로 넘겨, `AvailableCapacityCalculator` 내부의 활성 Reservation 재조회·참여자 합계 재조회를 제거했다. CLOSED 여부 조회와 READY 선점 합계 조회는 그대로 유지한다(원래도 중복이 아니었던 별개 조회).
- `availableCapacity` 계산식(`ReservationCapacityPolicy.availableCapacity`) 자체는 바꾸지 않았다 — 순수 중복 조회 제거 리팩터링이다.

### Track A — Index 수준 (구현)

- `SharedTable` 엔티티에 `@Table(indexes = @Index(name = "idx_shared_table_restaurant_id", columnList = "restaurant_id"))`를 추가했다.
- 컬럼 순서: 단일 컬럼 인덱스라 순서 이슈는 없다. `restaurant_id`를 선택한 이유는 date/time 필터 3-way join의 실제 join 조건(`shared_table.restaurant_id = restaurant.id`)이 EXPLAIN에서 확인된 그 컬럼이기 때문이다.
- 중복 Index 여부: `shared_table`의 기존 인덱스는 `PRIMARY(shared_table_id)` 하나뿐이었다. `restaurant_id`를 커버하는 인덱스가 없었으므로 중복이 아니다.
- 이 인덱스는 `docs/ERD.md` §10 "인덱스 후보"에 이미 `shared_table (restaurant_id)`로 후보 등재되어 있었다(이번에 실제 실행 계획 근거로 확정·구현).
- 반영 방식: Human 결정 Q3에 따라 `@Table(indexes=...)` 선언 + 기존 `ddl-auto: update`. 별도 마이그레이션 스크립트·Flyway/Liquibase 도입 없음.
- keyword LIKE·hour/minute 함수 래핑에는 Before 측정 결과 실제 병목 근거가 없어 **인덱스·쿼리 변경을 적용하지 않았다**(Issue 원칙: "실제 병목이 없으면 Index/Query 미변경 결론도 허용한다"). Full Text Search 등 검색 의미 변경은 Human 결정 Q2에 따라 이번 Issue에서 다루지 않고 후속 검토로 남긴다.
- 쓰기 비용·저장 공간: 단일 컬럼 비고유 BTree 인덱스라 INSERT/UPDATE마다 리프 엔트리 갱신 비용이 추가되지만, 이번 Issue에서 별도 쓰기-처리량 벤치마크는 수행하지 않았다(측정하지 않은 값을 있는 것처럼 기록하지 않는다). `shared_table`은 OWNER가 테이블을 등록할 때만 쓰는 낮은 쓰기 빈도 테이블이라 조회 개선 대비 쓰기 비용 증가가 클 것으로 보이지 않는다는 정성적 판단만 남긴다.

## After 결과

### Track A

원본: [`raw/explain-after-trackA.txt`](raw/explain-after-trackA.txt)

| 조건 | 실제 사용 Index | full scan / filesort / temporary | actual time | Before 대비 |
|---|---|---|---:|---:|
| 기본(필터 없음) | `PRIMARY`(동일) | 없음(동일) | 0.171ms | 변화 없음(측정 변동 범위) |
| keyword LIKE | `PRIMARY`(동일) | 없음(동일) | 1.58ms | 변화 없음(측정 변동 범위) |
| category 등치 | `PRIMARY`(동일) | 없음(동일) | 0.144ms | 변화 없음(측정 변동 범위) |
| date 필터(3-way join) | `restaurant.PRIMARY`(선행) → `idx_shared_table_restaurant_id`(신규) → `time_slot.uk_time_slot_active_start` | **shared_table full scan 제거.** join 순서가 `restaurant` 선행으로 바뀌어 `r` 스캔도 751행에서 조기 종료 | **3.03ms** | 11.5ms → 3.03ms (약 3.8배) |
| time 필터(hour/minute) | 위와 동일 순서 | shared_table full scan 제거, `r` 스캔 474행에서 조기 종료 | **1.33ms** | 12.1ms → 1.33ms (약 9.1배) |

기본/keyword/category 3가지는 이번 인덱스와 무관한 단일 테이블 조회라 계획이 그대로다(의도된 결과 — 이 변경이 다른 조회에 영향을 주지 않았다는 근거).

### Track B

- TimeSlot 20건 조회에 실행된 SQL PreparedStatement 수: **83개**(TimeSlot당 평균 4.15개), Before 123개 대비 40개(정확히 TimeSlot당 2개 × 20) 감소.
- `AvailableDiningSessionQueryCountInvestigationTest`에서 `queryCount == 3 + TIME_SLOT_COUNT * 4`(83)로 고정해 회귀를 방지한다.

## 정합성 회귀 검증

- 전체 테스트: `./gradlew clean :test` → **705개 중 705 PASS, 0 실패, 0 에러**(30개는 환경변수 게이트 통합 테스트로 이번 실행에서는 스킵, 실패 아님).
- `TimeSlotServiceTest`의 `사용자용_예약_가능_회차는_availableCapacity를_현재_capacity로_반환하고_partySize로_필터한다`가 새 `calculateWithKnownParticipantCount` 호출로 여전히 PASS — `availableCapacity` 계산 결과가 바뀌지 않았음을 확인.
- `AvailableCapacityCalculatorTest`, `ReservationPreparationServiceTest`, `ReservationPreparationConcurrencyIntegrationTest`(기존 2-인자 `calculate` 경로) 모두 PASS — READY Payment 임시 선점 포함 좌석 계산 회귀 없음.
- Track A 인덱스 추가는 실행 계획만 바꾸는 순수 인덱스 추가라 검색 결과·정렬·페이지 결과에 영향이 없다(동일 WHERE/ORDER BY, 결과 집합 불변). 별도 결과 동일성 비교 테스트는 추가하지 않았다 — 기존 `RestaurantSearchRepositoryImplTest` 등 결과 검증 테스트가 그대로 PASS하는 것으로 충분하다고 판단했다.

## 구조화 로그·메트릭

- Track B 쿼리 수: `SessionFactory.getStatistics().getPrepareStatementCount()` (Hibernate Statistics, `hibernate.generate_statistics=true`)로 측정.
- Track A 실행 계획: 실제 MySQL 8.4.10에 대해 `EXPLAIN ANALYZE`(H2가 아님) 원본 로그를 `raw/`에 보관.

## 결과 해석

- Track B는 측정 즉시 명확한 구조적 중복이 확인되어 Query 수준 개선을 적용했다. 계산식은 그대로이므로 기능 리스크가 낮다.
- Track A는 최초 가설(keyword LIKE, hour/minute 함수)이 **실제로는 이 규모에서 병목이 아니었다** — 측정 없이 바로 "함수 조건이니 인덱스가 안 먹는다"고 인덱스를 추가했다면 틀린 지점에 인덱스를 추가했을 것이다. 실제 병목은 예상 밖의 지점(`shared_table`의 조인 인덱스 부재)이었고, 이는 이 Issue의 "측정 후에만 변경을 선택한다" 원칙이 실제로 값을 낸 사례다.
- date/time 필터가 있는 검색은 3.8~9배 개선되었지만, 이 개선율은 5,000건 규모의 합성 데이터에서 측정한 것이며 실제 운영 데이터 분포·규모에서는 다를 수 있다.

## 검증 한계

- 데이터 규모는 Track A 5,000건, Track B 20건 각 1종만 측정했다(Issue 권장 `small/medium/large` 중 medium 1종 위주). 수십만 건 규모의 실제 운영 데이터에서는 옵티마이저의 판단이 달라질 수 있다.
- HTTP 레벨 p50/p95/p99는 측정하지 않았다 — 이번 Issue는 DB 레벨(SQL/EXPLAIN/쿼리 수)에 한정했고, 부하·응답시간 지표는 #63 K6 Harness의 몫으로 남긴다.
- keyword LIKE의 "실제 매치가 0건에 가까울 때 전체 스캔에 근접한다"는 위험은 이론적으로는 남아 있으나, 이번 측정에서 확정 병목으로 재현하지 않았다 — Full Text Search 등 검색 방식 변경은 Human 결정 Q2에 따라 이번 Issue에서 다루지 않는다.
- `idx_shared_table_restaurant_id`의 쓰기 비용·저장 공간 증가는 정량 측정하지 않았다(위 "변경 내용" 참고).
- Track A 데이터 생성 시 `SharedTable`은 Restaurant마다 정확히 1건만 만들어 1:1에 가까운 분포로 구성했다 — 실제 운영에서 한 Restaurant이 여러 SharedTable을 가지는 경우의 선택도는 별도로 확인하지 않았다.

## 재현 명령

```bash
docker run -d --name bobfull-perf-mysql -e MYSQL_ROOT_PASSWORD=perfpass \
  -e MYSQL_DATABASE=bobfull_perf -p 33061:3306 mysql:8.4 --sql_mode=STRICT_TRANS_TABLES

BOBFULL_MYSQL_PERF_TEST=true \
BOBFULL_TEST_MYSQL_URL="jdbc:mysql://localhost:33061/bobfull_perf?useSSL=false&allowPublicKeyRetrieval=true" \
BOBFULL_TEST_MYSQL_USERNAME=root \
BOBFULL_TEST_MYSQL_PASSWORD=perfpass \
./gradlew :test --tests "com.bobfull.timeslot.service.AvailableDiningSessionQueryCountInvestigationTest" \
  --tests "com.bobfull.restaurant.repository.RestaurantSearchExplainInvestigationTest" --info
```

## 관련

- Issue: #61
- PR: (구현 완료 후 연결)
- ADR: 불필요(Issue 본문 "ADR 판단" 참고)
- Troubleshooting: (필요 시 keyword LIKE 관련 후속 Issue에서 별도 작성)
