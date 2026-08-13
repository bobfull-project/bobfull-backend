# Issue #235 — 인기 회차 조회 Hot-path 병목 개선 및 K6 재측정

## 기준

- 기준 Branch: `perf/235-restaurant-view-hotpath`(코드, PR #242) → `docs/235-hotpath-after-remeasure`(After 재측정, 이 PR)
- 선행 Evidence: `docs/evidence/v3/142-reservation-peak/README.md`(#142, Before 원본)
- Before 코드 SHA: PR #242 머지 전 develop 기준(HikariCP `maximum-pool-size` env var 외 회차 조회 구조는 #61 이후 그대로)
- After 코드 SHA: PR #242 머지 커밋(TimeSlotService 회차별 반복 쿼리 4종 배치 전환)
- 측정일: 2026-08-13

## 요약

#142는 "인기 회차 예약 오픈" 상황에서 조회 폭주(A)가 CPU·DB Connection Pool을 동시에 포화시킨다는
걸 확인했지만, 그 조회가 식당 상세(detail)와 회차 조회(dining-sessions) 중 어느 쪽 때문인지는
분리하지 못했다. 이 Issue는 그 Hot-path를 분리 측정하고, 확인된 병목을 최소 변경으로 개선한
뒤 동일 조건으로 재측정했다.

**결론**: dining-sessions API가 회차마다 반복하던 쿼리 4종(활성 예약·참여자 합계·CLOSED 여부·
READY Payment 선점 합계)을 배치 쿼리로 전환한 것만으로 p95 92%, CPU 사용률 사실상 전부,
HikariCP Pool 포화를 완전히 해소했다. Pool 크기 조정(3단계)이나 인스턴스 확장은 필요하지
않았다 — 진짜 원인은 쿼리 구조였다.

## 핵심 원칙 준수 확인 (Issue #235)

- #142 Evidence를 Before 기준선으로 사용했다.
- Hikari Pool 크기를 먼저 늘리지 않고, 요청당 DB 비용(쿼리 구조)부터 확인했다.
- 정확한 병목 API(1단계)를 확인하기 전에는 Query/Index/Cache를 미리 정답으로 확정하지 않았다.
- 한 번에 여러 기술을 넣지 않고, 쿼리 구조 개선 하나만 적용한 뒤 같은 조건으로 재측정했다.
- Redis ZSet 대기열은 이 Issue에서 선도입하지 않았다.

## 1. 병목 Hot-path 분리

`peak-restaurant-view.js`(#142)는 한 iteration에서 식당 상세와 회차 조회를 함께 호출해 어느 쪽이
병목인지 분리하지 못했다. `restaurant-view-hotpath.js`(`TARGET=detail|sessions`)로 AWS에서
각각 독립 측정했다(Load, 20 iter/s·5분, 인스턴스 t3.small·풀=10, #142와 동일 조건).

| API | p95 | 오류율 | CPU·DB Pool 영향 |
|---|---:|---:|---|
| 식당 상세(`GET /api/restaurants/{id}`) | 16.5ms | 0% | 거의 없음(단일 쿼리 확인, 코드 리뷰로 검증) |
| 회차 조회(`GET .../dining-sessions`) | 1.15s~4.03s(측정 시점별 변동, 아래 "트러블슈팅" 참고) | 0% | 단독 20 req/s에도 HikariCP active 10/10(=풀 100%) 포화 |

Raw: `raw/detail-AWS-load.{log,json}`, `raw/sessions-AWS-load{,-postcleanup}.{log,json}`

**판정**: dining-sessions가 주 병목이다(Issue #235 "1. 병목 Hot-path 분리" 완료). 식당 상세는
이미 단일 쿼리(코드 리뷰로 확인, `RestaurantService.getRestaurantDetail` → `Restaurant` 엔티티에
`@OneToMany`/컬렉션 필드 없음)라 이번 개선 대상에서 제외했다.

## 2. 회차 조회 반복 Query 개선

`TimeSlotService.getAvailableDiningSessions`가 회차(TimeSlot)마다 아래 4개 쿼리를 반복 실행하고
있었다(회차 N건 기준 `3 + N*4` 쿼리, #61 이후 구조):

1. 활성 예약(RECRUITING/CONFIRMED/CANCELLING) 조회
2. 그 예약의 참여자 partySize 합계
3. CLOSED 예약 존재 여부(`availableCapacity` 0 처리용)
4. 만료되지 않은 READY Payment 선점 partySize 합계

회차 ID는 TimeSlot 목록 조회 시점에 이미 다 알고 있으므로, 4개 모두 `List<Long> timeSlotIds`
기준 배치 쿼리(GROUP BY 또는 IN)로 바꿔 회차 건수와 무관하게 한 번씩만 실행하도록 했다:

- `ReservationRepository.findAllByTimeSlotIdInAndReservationStatusIn`(활성/CLOSED 겸용)
- `ReservationParticipantRepository.sumPartySizeByReservationIdsAndStatuses`
- `PaymentRepository.sumPartySizeByTimeSlotIdsAndStatusAndExpiresAtAfter` +
  `PaymentHoldReader.sumActiveReadyPartySizeByTimeSlotIds`

`availableCapacity` 계산식(`ReservationCapacityPolicy.availableCapacity`, CLOSED면 0)은 그대로
유지했다 — 입력값을 가져오는 방식만 바꿨다.

**쿼리 수 변화**: `AvailableDiningSessionQueryCountInvestigationTest`(전용 MySQL 컨테이너,
TimeSlot 20건)로 실측 — **83개 → 7개**(고정값, 회차 건수와 무관). `PerformanceQueryCountIntegrationTest`
(TimeSlot 2건)에서도 동일하게 7개로 수렴함을 확인했다.

상세 코드 변경은 PR #242 참고.

## 3. DB Pool 설정 판단

아래 4절의 After 재측정에서 HikariCP `active`가 Load 구간 내내 0에 가깝게 유지되고 `pending`도
관측되지 않아, **쿼리 구조 개선만으로 DB Pool이 더 이상 첫 병목이 아님을 확인했다**. Pool 크기
조정(`DB_POOL_MAX_SIZE`)은 필요하지 않다고 판단해 진행하지 않았다 — 기본값(10) 그대로 둔다.

## 4. 동일 조건 K6 After 측정

### 측정 방법론 — 워밍업 트러블슈팅

3단계 진행 중, 인스턴스를 재시작한 직후 바로 측정하면 JVM JIT·커넥션 풀 콜드 스타트가 섞여
실제보다 나쁜 값이 나온다는 걸 직접 확인했다(재시작 직후 smoke 테스트 p95 1.11s → 90초
워밍업 트래픽을 흘려보낸 뒤 재측정하니 p95 290ms로 급격히 개선). 그래서 **Before/After 둘 다
30초 워밍업 트래픽(결과 폐기)을 먼저 흘려보낸 뒤** 실제 측정을 시작했다 — 이 원칙을 지키지
않은 이전 raw 파일(`sessions-AWS-load.log`, `sessions-AWS-load-postcleanup.log`)은 참고용으로만
남겨두고, 아래 비교표는 워밍업을 거친 `-before-warm`/`-after-batch` 실행 기준으로 작성한다.

### Before/After 비교 (AWS `bobfull-k6-test-app`, t3.small·풀=10, `STAGE=load` 20 iter/s·5분, 워밍업 후 측정)

| 지표 | Before(배치 전, PR #242 이전 코드) | After(배치 후, PR #242 코드) | 변화 |
|---|---:|---:|---|
| p95 응답시간 | 802.66ms | **60.27ms** | 개선 92.5% |
| p99 응답시간 | 1.706s | **265.54ms** | 개선 84.4% |
| 평균 응답시간 | 299.22ms | **35.41ms** | 개선 88.2% |
| HTTP RPS(`http_reqs`) | 19.7 req/s | 20.0 req/s | 동일(목표 부하 그대로 소화) |
| 오류율(`http_req_failed`) | 0% | 0% | 동일 |
| dropped_iterations | 25건 | 3건 | 감소 |
| CPU(최대/평균) | 91.7% / 70.0% | **21.2% / 11.6%** | 사실상 여유 확보 |
| HikariCP active(최대) | 10(=풀 100%) | **0** | 완전 해소 |
| HikariCP pending(최대) | 1 | **0** | 완전 해소 |

Raw: `raw/sessions-AWS-load-before-warm.{log,json}`, `raw/sessions-AWS-load-after-batch.{log,json}`,
`raw/prometheus/sessions-{before,after}-{cpu_process,hikari_active,hikari_pending}.json`

**해석**: 회차별 반복 쿼리를 배치로 묶은 것만으로 지연·CPU·DB Pool 세 지표 모두에서 뚜렷한
개선이 나타났다. 특히 HikariCP `active`가 Load 구간 전체에서 0으로 유지된 건, 쿼리 수가
줄면서 커넥션을 붙잡는 시간 자체가 짧아져 동시 사용 커넥션이 거의 필요 없어졌다는 뜻이다.
Pool 크기나 인스턴스 크기를 건드리지 않고도(3단계 불필요) 근본 원인(쿼리 구조)만 고쳐서
해결됐다는 게 이번 Issue의 핵심 성과다.

## 5. 정합성 회귀 확인

- `TimeSlotServiceTest` 신규 유닛 테스트: 활성 예약+참여자 3명+READY 선점 1명인 회차의
  `availableCapacity`가 배치 조립 전후로 동일한 공식(`ReservationCapacityPolicy.availableCapacity`)
  으로 계산됨을 확인. CLOSED 회차는 참여자·READY 선점과 무관하게 `availableCapacity=0`을
  유지함을 확인.
- 전체 유닛 테스트 814/818 통과(실패 4건은 무관한 `ManualSmtpSendVerification`, 실제 Gmail
  SMTP 자격증명 필요 — 이 변경과 무관).
- AWS 재측정에서도 `checks_succeeded` 100%, `http_req_failed` 0%로 응답 자체는 매번 정상이었다.

## 6. 검증 한계

- Stress(계단식 고부하) 재측정은 하지 않았다 — Load(20 iter/s) 수준에서 이미 CPU·Pool이
  완전히 여유로워져(21%, active=0), 더 높은 부하에서의 정확한 새 포화점은 확인하지 못했다.
  필요하면 후속으로 Stress 재측정을 진행할 수 있다.
- 워밍업 시간(30초)은 경험적으로 정한 값이라, 더 짧은/긴 워밍업이 결과에 영향을 주는지는
  별도로 확인하지 않았다.
- t3.small의 CPU 크레딧(버스터블) 소진 여부는 이번에도 확인하지 않았다 — 다만 After 측정에서
  CPU 사용률 자체가 낮아(평균 11.6%) 크레딧 소진 위험은 낮다고 판단한다.
- RDS 쪽 성능(쿼리 실행 시간 자체)은 App 쪽 Prometheus 지표로 간접 추정했을 뿐, RDS
  Performance Insights 등으로 직접 확인하지는 않았다.

## 최종 판단

**조회 개선만으로 병목이 충분히 완화됨 → 현재 구조(배치 쿼리) 유지, Pool 크기 조정 불필요,
Redis ZSet 대기열 미도입.**

- `#191`(Auto Scaling 판단) 연계는 필요하지 않다고 판단한다 — App CPU가 이미 여유롭다(평균
  11.6%).
- Redis ZSet 대기열은 Before에서도(#142 시나리오 B, 최대 500명 동시 CREATE 경쟁) 이미 병목
  신호가 없었고, 이번 조회 경로 개선까지 더해져 도입 조건(순간 유입량이 안전 처리량을
  지속적으로 초과)이 더더욱 충족되지 않는다고 판단한다.

## 후속 과제

- (선택) Stress 수준 재측정으로 새 포화점 확인.
- `docs/adr/README.md`에 이번 결정(회차 조회 배치화, Pool 미조정)을 ADR-0001 보강 근거로
  추가할지는 Human 판단 필요.
