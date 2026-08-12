# #142 인기 회차 예약 부하 측정 — 시나리오 A·B

#207(AWS 실행 환경 준비, `bobfull-k6-test-app` EC2)이 완료돼 실제 AWS 대상으로 시나리오 A는
Load/Stress까지 실행해 첫 병목 전환점을 확인했다(아래 "결과 표 비교" 참고). 로컬 Prometheus를
이 인스턴스의 `/actuator/prometheus`에 직접 연결해 CPU/DB Pool 지표까지 같은 시간축으로
확인했다(Issue #142 "K6 summary만으로 병목을 단정하지 않는다" 원칙 준수). Prometheus
`query_range` 원본 응답은 `raw/prometheus/`에 저장했다. Grafana 시각화는 admin 비밀번호·
Slack 연동이 필요해 이번 범위에서는 붙이지 않고 Prometheus HTTP API로 직접 쿼리했다.

시나리오 B는 팀 합의(Human 결정, Issue #142 댓글)에 따라 **현재 비관적 락 전략 기준의 Before
Baseline**으로 측정한다. #60(예약 좌석 락 전략 재설계, 2026-08-11 기준 DRAFT)이 끝나 락 전략이
바뀌면, 이번 결과를 Before로 두고 동일 조건으로 After를 재측정한다. A는 #60과 무관해 최종
결과로 취급한다.

## 측정·재현 환경 (Issue #142 "측정 환경 계약")

- 기준 Branch·Commit: `feature/142-peak-load-test`, 이 문서 갱신 시점 최신 커밋(PR #220 Conversation 참고)
- 애플리케이션 인스턴스: `bobfull-k6-test-app` EC2 1대, t3.small(2 vCPU, 버스터블) — 인스턴스 CPU 크레딧 소진 여부는 미확인(위 "이 결론의 한계" 참고)
- MySQL: 버전·인스턴스 세부사항 미확인(Test RDS, `bobfull_test` 스키마 사용 — `docs/infra/K6_AWS_TEST_ENVIRONMENT.md` 참고). 테스트 데이터 규모는 시나리오별 `setup()`이 그때그때 만드는 합성 데이터뿐(수십~수백 건 수준, 대량 사전 시딩 없음)
- DB Connection Pool 크기: HikariCP 기본값 `maximum-pool-size=10`(코드에 명시적 설정이 없었음, 이번 PR에서 `DB_POOL_MAX_SIZE` env var로 오버라이드 가능하게 함 — 이번 측정 시점에는 여전히 10)
- K6 VU·arrival-rate·duration: 시나리오 A는 `constant-arrival-rate`(Load, 20 iter/s·5분)/`ramping-arrival-rate`(Stress, 20→320 iter/s 계단식·13분, `preAllocatedVUs=50~300`). 시나리오 B는 `per-vu-iterations`(VU=`CONCURRENT_USERS`, 1인당 1회)
- 워밍업: 별도 워밍업 단계 없음(Load/Stress 자체의 첫 구간이 사실상 워밍업 역할). 본 측정은 각 설정별 1~2회(재현성 확인용 반복 포함)
- 맛집·회차·좌석 수·동시 사용자 수: 시나리오 A는 식당 1개·회차 1일치(96슬롯), 시나리오 B는 식당 1개·좌석 4석 테이블 1개·회차 1개(경쟁 대상), 동시 사용자 수는 표 참고(A는 iter/s, B는 `CONCURRENT_USERS`)
- 캐시: 이번 두 시나리오 모두 앱의 기존 Redis 캐시(#62, 검색 결과 캐시) 대상 API를 직접 사용하지 않음 — A는 식당 상세/회차 조회(캐시 미적용 경로), B는 예약 준비(캐시 없음)
- Outbox·알림 Adapter: 이번 시나리오는 결제 완료·알림 발생 경로를 타지 않아 해당 없음
- 외부 결제: 호출하지 않음(CREATE는 READY Payment만 생성, 완료 처리 없음 — 위 "범위 한계" 참고)
- 로컬 환경(별도 비교용): `./gradlew bootRun`(`spring.profiles.active=local`), `docker-compose`(MySQL 8.4, Redis 7) — 운영/AWS 환경과 CPU·네트워크 특성이 달라 병목 전환점 근거로 쓰지 않는다.
- Fixture: 시나리오별 `setup()`에서 API로 직접 생성(운영 데이터 없음, 합성 데이터만 사용).

## 시나리오 A — 예약 페이지 조회 폭주 (`peak-restaurant-view.js`)

같은 식당·같은 날짜를 반복 조회하는 hot-key 패턴을 모사한다(#63의 `restaurant-search.js`는
무작위 키워드로 여러 식당에 걸친 균등 부하를 모사하는 것과 대조적).

| 실행 | 결과 | Raw |
|---|---|---|
| 로컬 Smoke(`STAGE=smoke`) | PASS — checks 84/84(100%), http_req_failed 0% | `raw/A-peak-restaurant-view-smoke.log`, `.json` |
| AWS Smoke(`BASE_URL=http://15.164.48.39:8080`) | PASS — checks 84/84(100%), http_req_failed 0%, p95≈434ms(로컬 대비 네트워크 왕복 포함) | `raw/A-peak-restaurant-view-AWS-smoke.log`, `.json` |
| AWS Load(`STAGE=load`, 20 RPS·5분, p99 포함 재실행) | 안정 처리 구간(실패율 0.00%)이지만 여유는 크지 않음 | `raw/A-peak-restaurant-view-AWS-load-2.log`, `.json`(최신, p99 포함) |
| AWS Stress(`STAGE=stress`, 20→320 RPS 계단식·13분, p99+Prometheus 포함 재실행) | **첫 병목 전환점 확인 + 근본 원인 확인** — 아래 "결과 표 비교" 참고 | `raw/A-peak-restaurant-view-AWS-stress-3.log`, `.json`(최신, p99+Prometheus 포함) |

이전 실행(`A-peak-restaurant-view-AWS-load.*`, `-stress.*`, `-stress-2.*`)은 p99가 없는 k6
기본 `summaryTrendStats`로 실행한 초기 기록이라 raw로만 남기고, 아래 비교표는 p99를 포함한
최신 실행(`-load-2`, `-stress-3`) 기준으로 작성한다. `http_reqs`(실제 HTTP 요청 수·속도)와
`iterations`(k6 반복 수·속도)를 구분해서 표기한다 — 이 시나리오는 반복(iteration) 1회당
GET 요청 2건(식당 상세 + 회차 조회)을 보내므로 두 값이 다르다.

### 결과 표 비교 — AWS Load vs Stress (`bobfull-k6-test-app`, t3.small)

| 단계 | 목표 부하(iteration 기준) | HTTP RPS(`http_reqs`) | Iteration/s(`iterations`) | p50 | p90 | p95 | p99 | max | 오류율(`http_req_failed`) | dropped_iterations | CPU/DB Pool | 정합성 | 병목 후보 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|
| Load | constant-arrival-rate 20 iter/s, 5분 | 39.8 req/s | 19.9 iter/s | 177ms | 261ms | 408ms | 962ms | 2.46s | 0.00%(0/11,973) | 16(0.05/s) | 미관측(이 실행은 Prometheus 연결 전) | checks 100% | 아직 없음 — 안정 구간이나 p99가 이미 1초에 근접 |
| Stress | ramping-arrival-rate 20→40→80→160→320 iter/s, 13분 | 51.4 req/s(목표 640 req/s 대비 크게 못 미침) | 25.7 iter/s | 2.30s | 10.15s | 13.14s | **19.62s** | 31.98s | 0.02%(9/40,701) | 61,851(78.2/s) | **CPU 88~98%, HikariCP active 10/10(고정), pending ~190건** | checks 99.98% | **CPU + DB Pool 동시 포화(아래 근본 원인 확인 참고)** |

**해석**: 오류율은 낮게 유지됐지만(0.00~0.02%), p95가 408ms → 13.14초로 32배, p99는 962ms →
19.62초로 20배 급증했고 `dropped_iterations`가 6만 건대까지 치솟았다. 서버가 **에러를 내며
실패하는 게 아니라, 요청이 쌓여 점점 느려지는 방식으로 포화**됐다 — 전형적인 자원 고갈
(saturation) 신호다. 이전 실행(p99 미포함, `raw/A-peak-restaurant-view-AWS-load.json`/
`-stress.json`/`-stress-2.json`)의 p50/p90/p95도 이번 재실행과 오차 범위 안에서 일치해
재현 가능함을 재확인했다(예: Stress p95 13.90s/12.98s(이전) vs 13.14s(이번)).

### 근본 원인 확인 — 로컬 Prometheus를 AWS 인스턴스에 연결해 실측

`monitoring/docker-compose.yml`의 Prometheus를 실행자 로컬에서 띄우고
`BOBFULL_BACKEND_METRICS_TARGET=15.164.48.39:8080`으로 지정해, AWS Test App EC2의
`/actuator/prometheus`를 직접 스크래핑했다. 아래 표는 최신 Stress(p99 포함, `-stress-3`)
실행 중 수집한 값이다. Prometheus `query_range` 원본 JSON은
`raw/prometheus/A-Stress-{cpu_process,cpu_system,hikari_active,hikari_pending,hikari_timeout,threads,lock_count,lock_sum}.json`,
Load 구간은 `raw/prometheus/A-Load-*.json`에 저장했다.

Stress 시작 시각(`05:49:44Z`) 기준 경과 시간별 관측값(`query_range`, step=15~30s):

| 경과 | `process_cpu_usage` | `hikaricp_connections_active` | `hikaricp_connections_pending` | `jvm_threads_live_threads` |
|---:|---:|---:|---:|---:|
| t+0s(20 iter/s 시작) | 52% | 0 | 0 | 66 |
| t+90s | 81% | 4 | 0 | 78 |
| t+150s(40 iter/s 단계 중) | **93%** | **10(=max)** | **45** | 88 |
| t+210s | 94% | 10 | 192 | 232 |
| t+420s(160 iter/s 단계) | 96% | 10 | 191 | 232 |
| t+780s(320 iter/s 단계) | 97% | 10 | 190 | 232 |

이전 실행(`-stress-2`, raw로 별도 보존)에서도 같은 시점 기준 CPU 88~98%, `active=10`(고정),
`pending` 190건 전후로 거의 동일한 값이 나와 재현 가능함을 확인했다.

**결론**: CPU와 DB Connection Pool(HikariCP `maximum-pool-size=10`)이 목표 부하가 40 RPS
단계에 도달한 시점(경과 약 2분 30초, 아직 Stress 최고 단계 320 RPS에 훨씬 못 미친 지점)부터
**거의 동시에 포화**되고, 이후 160·320 RPS 단계까지도 그 상태가 그대로 유지된다(더 나빠지지도
않음 — 이미 최대치라 더 나빠질 여지가 없다). Tomcat 스레드도 232에서 고정돼 요청이 스레드
단계에서부터 쌓이고 있음을 보여준다. 즉 **"어느 시점 이후 갑자기 무너진 병목"이 아니라
"t3.small(2 vCPU) 인스턴스 자체의 CPU·커넥션 풀 용량이 hot-key 조회 40 RPS 수준에서 이미
한계"**라는 게 이번 기록의 근거 있는 결론이다. `hikaricp_connections_pending`이 약 190건으로
안정된 것도 "더 큰 장애로 확산"되지 않고 일정한 대기 상태로 버티는 것으로 해석된다(요청이
실패하지 않고 느려지기만 하는 이유).

이 결론의 한계: (1) 커넥션 풀 크기(10)가 애플리케이션 기본값인지 이 테스트 인스턴스만의 설정인지
확인하지 않았다 — 운영 환경 설정과 다를 수 있다. (2) CPU 포화가 "쿼리 자체가 무겁다"(#61
영역)와 "단순히 요청량 대비 vCPU가 부족하다"(인스턴스 크기 문제) 중 어느 쪽이 더 큰 비중인지는
쿼리별 CPU 프로파일링 없이는 구분하지 못한다. (3) t3.small은 버스터블 인스턴스라 CPU 크레딧
소진 여부도 확인하지 않았다.

## 시나리오 B — 예약 버튼 동시 클릭 (`peak-reservation-create-race.js`)

**범위 한계(Human 결정, 대화창 논의)**: 이 시나리오는 **CREATE 경쟁만** 다룬다. Issue #142가
원래 언급하는 "이미 만들어진 예약에 JOIN으로 몰려 좌석초과가 막히는지"는 이번 범위에 없다 —
`ReservationPreparationService` 클래스 Javadoc에 "결제 성공 전에는 Reservation·
ReservationParticipant를 생성하지 않는다"고 명시돼 있어, JOIN 대상이 되려면 최초 참여자의
결제가 실제로 완료돼야 한다. 이 저장소엔 PortOne을 대신할 Fake 결제 확인 어댑터가 없고, 실제
결제 완료는 PortOne 결제창을 통한 카드 결제라 k6로 자동화할 수 없다(Issue #142 "제외 범위"의
"PortOne 실서비스 반복 결제 요청"과도 충돌). JOIN 기반 좌석초과 테스트는 Fake 결제 확인
어댑터가 별도 Issue로 추가되면 이어서 다룬다.

CREATE 경쟁은 결제 완료 없이도 완전히 검증 가능하고, "인기 회차가 열리는 순간 몰리는" 상황을
오히려 더 정확히 모사한다.

### 결과 — 로컬 (Issue #142 "초기 후보 단계": 2 → 5 → 10 → 20 → 50)

| 동시 사용자 수 | 성공(200) | 충돌(409 ACTIVE_RESERVATION_ALREADY_EXISTS) | 예상 밖 응답 | threshold Gate | teardown 독립 검증 | Raw |
|---:|---:|---:|---:|---|---|---|
| 2 | 1 | 1 | 0 | PASS | PASS(배타 선점 유지 확인) | `raw/B-create-race-2users.*` |
| 5 | 1 | 4 | 0 | PASS | PASS | `raw/B-create-race-5users.*` |
| 10 | 1 | 9 | 0 | PASS | PASS | `raw/B-create-race-10users.*` |
| 20 | 1 | 19 | 0 | PASS | PASS | `raw/B-create-race-20users.*` |
| 50 | 1 | 49 | 0 | PASS | PASS | `raw/B-create-race-50users.*` |

### 결과 — AWS(`bobfull-k6-test-app`, 동일 동시성 단계, p99 포함 재실행)

| 동시 사용자 수 | 성공(200) | 충돌(409) | 예상 밖 응답 | p95(`http_req_duration`) | p99 | threshold Gate | teardown 독립 검증 | Raw |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 2 | 1 | 1 | 0 | 102.8ms | 111.6ms | PASS | PASS | `raw/B-create-race-2users-AWS-v2.*` |
| 5 | 1 | 4 | 0 | 98.4ms | 100.6ms | PASS | PASS | `raw/B-create-race-5users-AWS-v2.*` |
| 10 | 1 | 9 | 0 | 94.6ms | 98.8ms | PASS | PASS | `raw/B-create-race-10users-AWS-v2.*` |
| 20 | 1 | 19 | 0 | 96.7ms | 98.0ms | PASS | PASS | `raw/B-create-race-20users-AWS-v2.*` |
| 50 | 1 | 49 | 0 | 162.4ms | 178.2ms | PASS | PASS | `raw/B-create-race-50users-AWS-v2.*` |
| 100 | 1 | 99 | 0 | 319.9ms | 361.0ms | PASS | — | `raw/B-create-race-100users-AWS-v2.*` |
| 200 | 1 | 199 | 0 | 553.8ms | 619.2ms | PASS | — | `raw/B-create-race-200users-AWS-v2.*` |
| 500 | 1 | 499 | 0 | 1.35s | 1.52s | PASS | — | `raw/B-create-race-500users-AWS-v2.*`(`setupTimeout=180s` 필요, 아래 참고) |

이전 실행(p99 미포함, `-AWS.*`/`-AWS.log` 접미사, `setupTimeout` 조정 전 500명 최초 실패
포함)은 raw로 보존하고, 위 표는 `summaryTrendStats`에 p99를 추가한 재실행(`-AWS-v2.*`)
기준으로 작성했다. p95/성공-충돌 수는 이전 실행과 동일해 재현 가능함을 재확인했다.

**동시 사용자 수가 늘수록 p95/p99가 함께 늘어난다**(2명 103ms → 500명 1.35s) — 이는 서버
지연이 아니라 **VU 준비·요청 발사 자체가 순차적으로 이뤄지는 k6 클라이언트 측 특성**과
네트워크 왕복이 섞인 결과로 보인다(아래 DB Pool/Lock 지표에서 서버 쪽은 여유가 있음을 확인).

### B의 DB Pool·Lock 지표 (Prometheus, 100/200명 및 500명 재실행 중 수집)

CREATE는 `TimeSlotRepository.findWithLockByIdAndDeletedAtIsNull`로 비관적 락을 건다(ADR
0001). 이 메서드의 Spring Data 호출 지표(`spring_data_repository_invocations_seconds_*`)를
호출 전후 델타로 계산해 "락 대기를 포함한 평균 처리 시간"의 대체 지표로 썼다(순수 InnoDB
`SHOW ENGINE INNODB STATUS` 레벨의 row lock wait/deadlock 카운터는 MySQL exporter가 없어
관측하지 못했다 — 아래 한계 참고).

| 동시 사용자 수 | `findWithLockBy...` 호출 수 | 호출당 평균 시간 | HikariCP active(최대) | HikariCP pending(최대) | CPU(최대) |
|---:|---:|---:|---:|---:|---:|
| 100 | 101건 | 40.0ms | 1 | 0 | 44% |
| 200 | 201건 | 35.1ms | 1 | 0 | 44% |
| 500 | 501건 | 27.8ms | 1 | 0 | 44% |

**해석**: 100→500명으로 동시성이 5배 늘어도 락 호출당 평균 시간이 오히려 낮거나 비슷하게
유지되고(40→35→28ms), `HikariCP active`는 최대 1(=풀의 10%), `pending`은 항상 0이었다 —
**락 경합이나 DB Pool 대기로 인한 병목 신호가 이 범위(최대 500명)에서는 관측되지 않았다.**
시나리오 A(지속적 조회 부하)와 뚜렷이 다른 점이다 — B는 총 쿼리 수 자체가 적은 짧은 burst라
동일 인스턴스에서도 CPU/Pool을 거의 쓰지 않는다.

**한계**: (1) InnoDB 레벨의 실제 row lock wait/deadlock 카운터는 MySQL exporter가 없어
관측하지 못했다 — 위 지표는 Spring Data 호출 시간(락 획득+쿼리 실행 포함)의 근사치다. (2)
500명을 넘는 동시성에서 락 경합이 나타나는지는 확인하지 못했다(아래 "환경 한계" 참고).

**환경 한계(서버가 아니라 하네스)**: `CONCURRENT_USERS=1000`은 `setup()`이 180초를 넘겨
실패했다(`setup() execution timed out after 180 seconds`). `setup()`이 회원가입·로그인을
순차로 하기 때문(1,000명이면 2,002회 순차 HTTP round-trip)이지, 서버가 느려진 게 아니다 —
그 180초 동안 서버는 초당 약 10~16 요청을 오류 없이 처리하고 있었다(`raw/B-create-race-1000users-AWS.log`
참고, `http_req_failed=0%`). 500명까지는 `setupTimeout`을 늘리는 것만으로 정확히 1명 성공을
재확인했다. 더 큰 동시성을 보려면 `setup()`을 병렬화하거나(예: 여러 회원을 동시에 가입) 미리
만들어둔 회원 풀을 재사용하는 방식으로 하네스를 고쳐야 한다(이번 범위 밖, 후속 개선 후보).

로컬·AWS 모든 동시성 단계에서 정확히 1명만 성공하고 나머지는 전부
`409 ACTIVE_RESERVATION_ALREADY_EXISTS`였다. `checks_succeeded`는 매 실행 100%였고
`peak_create_race_unexpected` 카운터는 항상 0이었다. `teardown()`에서 새 회원으로 같은
회차에 CREATE를 한 번 더 시도해도 409가 나, 경쟁 종료 후에도 배타 선점이 깨지지 않음을
독립적으로 재확인했다. AWS 실행에서는 동시성이 올라갈수록 iteration_duration이 늘었다
(2명 128ms → 50명 233ms 평균) — 로컬보다 네트워크 왕복이 포함돼 그런 것으로 보이며, 이 규모
(최대 50)에서는 DB Pool pending/lock wait 등 실제 병목 신호는 관측하지 않았다(Prometheus/Grafana
미연결, 별도 진행 필요).

**이 결과는 현재 비관적 락 전략 기준의 Before Baseline이다(팀 합의, Human 결정).** #60(예약
좌석 락 전략 재설계)이 끝나면 동일 조건(같은 동시성 단계 2/5/10/20/50, 같은 Fixture 방식)으로
After를 재측정해 이 표와 비교한다.

### 트러블슈팅 — teardown 검증 로직 최초 버그

처음 작성한 `teardown()`은 GET 회차 조회 응답의 `reservationId` 필드가 채워졌는지로 성공을
판정했는데, CREATE prepare 성공(200)은 결제 완료 전이라 `Reservation`이 아직 생성되지 않은
상태다(위 "범위 한계" 참고) — 그래서 실제로는 정상 동작(1명 성공)인데도 매번 "예약이 생성되지
않았다"는 오탐 에러가 찍혔다. 새 회원으로 CREATE를 한 번 더 시도해 409를 받는지로 검증하는
방식으로 고쳤다(`peak-reservation-create-race.js` 참고).

### 트러블슈팅 — 불변식 위반이 k6 실행 실패로 이어지지 않던 문제 (PR #220 리뷰, hyeonseung-dev)

처음 구현은 `peak_create_race_success`/`conflict`/`unexpected` Counter만 기록하고
`options.thresholds`로 묶지 않았다. 그래서 예를 들어 경쟁 버그로 2명이 200을 받아도 Counter
값만 달라질 뿐 k6는 종료 코드 0으로 끝날 수 있었다 — "정확히 1명만 성공"이라는 이 시나리오의
핵심 불변식을 자동으로 검증하는 Gate가 없었다. `teardown()`의 배타 선점 재검증도 `console.error`
로그만 남겨 사람이 로그를 읽어야만 위반을 발견할 수 있었다.

`options.thresholds`에 `peak_create_race_success: ['count==1']`,
`peak_create_race_conflict: ['count==CONCURRENT_USERS-1']`, `peak_create_race_unexpected: ['count==0']`,
`checks: ['rate==1.0']`를 추가하고, `teardown()`도 `check()`로 바꿔 같은 Gate에 걸리게 했다.
실제로 Gate가 동작하는지 별도 실험(threshold를 `count==0`으로 일부러 깨뜨림)으로 확인했다 —
정상 조건에서는 종료 코드 0, 불변식을 강제로 깨면 종료 코드 99(k6 표준 threshold 실패 코드)로
끝난다. 2/5/10/20/50 전 단계를 재실행해 모든 threshold가 PASS임을 다시 확인했다(위 결과 표).

## A 개선 조치 — HikariCP 커넥션 풀 크기 설정화

`hikaricp_connections_max=10`이 실측(위 "근본 원인 확인")으로 확인됐는데,
`application-prod.yml`/`application-local.yml.example`에는 `spring.datasource.hikari.*`
설정 자체가 없었다 — 즉 10은 애플리케이션이 의도적으로 튜닝한 값이 아니라 **HikariCP
자체의 기본값**이었다.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_POOL_MAX_SIZE:10}
```

기존 `REDIS_TIMEOUT` 등과 같은 패턴으로 env var 오버라이드를 추가했다. **기본값은 그대로
10으로 유지**해 이 변경만으로는 운영 동작이 바뀌지 않는다 — #142에서 발견한 값을 근거로
운영 풀 크기를 임의로 올리는 결정은 하지 않았다(쿼리·DB 자원 계획은 #61/#62·Human 판단
영역).

**검증 범위와 한계**: 이 커밋에서 한 것은 "설정을 외부화"한 것뿐이다. 실제로 더 큰 값(예:
`DB_POOL_MAX_SIZE=30`)으로 `bobfull-k6-test-app`을 재배포해 Stress를 다시 돌려보는
**검증까지는 하지 못했다** — 이 환경에서는 그 인스턴스를 재배포할 권한이 없다(AWS 계정이
분리돼 있음, Issue #207 참고). 재배포는 인프라 담당자(김홍기)와 조율이 필요하고, RDS의
`max_connections` 여유도 먼저 확인해야 한다(여러 개선 후보 중 하나가 다른 자원을 옮겨서
포화시킬 수 있음).

## 남은 작업

- (완료) 시나리오 A AWS Load/Stress 실행, 첫 병목 전환점(HTTP 레벨) 확인
- (완료) Prometheus 연결로 근본 원인 확인(CPU+DB Pool 동시 포화)
- (완료) HikariCP 풀 크기 env var 설정화(기본값 10 유지, 실제 조정은 후속)
- (완료) 시나리오 B 동시성 100/200/500까지 확장 확인, 1000은 하네스 setup 한계로 기록
- **재배포 후 재측정 필요**: `DB_POOL_MAX_SIZE`를 늘려 `bobfull-k6-test-app`을 재배포한 뒤
  같은 Stress를 재실행해 실제로 병목이 개선되는지 확인(김홍기 조율 필요, RDS `max_connections`
  여유 확인 필요)
- #60(예약 좌석 락 전략 재설계) 결과가 나오면 시나리오 B를 동일 조건으로 재검증(Before는 이번
  결과, After는 #60 이후 재실행)
- A/B 개선·재측정 결과가 정리되면 시나리오 C(Redis ZSet 대기열) 도입 여부 판단으로 이어감(팀 합의)
- JOIN 기반 좌석초과 테스트는 Fake 결제 확인 어댑터 도입 여부를 별도 결정한 뒤 진행
