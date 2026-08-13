# Issue #146 — 환불 완료 동기 경로 K6 측정 및 Spring Event 전환 판단

## 기준

- 기준 Branch: `perf/146-refund-completion-k6`(PR #250)
- 측정 Commit SHA: `83d424b`
- 측정일: 2026-08-13
- 선행 Evidence: `docs/evidence/v3/142-reservation-peak/README.md`(#142), `docs/evidence/v3/restaurant-view-hotpath/README.md`(#235) — 동일 AWS 테스트 인스턴스·Prometheus 스크래핑 방식 재사용
- Human 결정 계약: Issue #146 댓글(2026-08-13T03:34:40Z, `sighingpotato`) "Human 결정 필요 — 답변 확정" 7개 항목을 기준으로 측정했다.

## 요약

시나리오 A(기준선)·B(락 경쟁)·C(중복 웹훅)·E(완료 실패)는 AWS 실측(Load, 20 iter/s 또는 그룹 10/s)에서 목표(p95 300ms·p99 800ms)를 여유 있게 충족했다. CPU는 6~16%, HikariCP active는 최대 7건(풀 10 중), pending은 전 구간 0으로 DB Pool 병목은 관측되지 않았다.

시나리오 F(PortOne 외부 지연 3초 주입)는 응답시간이 그대로 3초대로 늘었지만 HikariCP active는 여전히 최대 2건으로, **외부 API 지연이 Reservation 락이나 DB Connection Pool로 새지 않는다**는 Port·Adapter 분리 설계를 실측으로 확인했다.

시나리오 D(완료 로직 지연 주입 300ms)는 p95가 401ms로 목표(300ms)를 넘겼다 — 현재는 문제가 아니지만, 향후 예약 완료 후속 처리(통계·알림 등)가 무거워지면 웹훅 p95가 목표를 벗어날 수 있다는 조기 경고 신호다.

**결론(§8 참고): A안(현재 동기 Port·Adapter 구조 유지)**. Spring Event 전환 게이트(p95 600ms 초과 + 원인이 예약 완료 비중 + 락·Pool pending 30% 이상)에 해당하는 조건은 이번 측정 범위 안에서 하나도 확인되지 않았다.

**이번 측정의 한계(§7)**: 실메일 발송 지연으로 `setup()` 자체가 오래 걸려, 이번 측정은 Issue 본문이 제시한 1/10/30/50/100 VU 단계적 확대와 Stress 단계를 전부 수행하지 못하고 **Load 단계 1개 지점**만 실측했다. InnoDB 레벨 실제 row lock wait(`performance_schema.data_lock_waits`)도 MySQL exporter 부재로 수집하지 못해 HikariCP active/pending을 대리 지표로 사용했다(#235와 동일한 기존 한계).

## 측정 환경

- 애플리케이션 인스턴스: `bobfull-k6-test-app` EC2 1대, t3.small(2 vCPU, 버스터블) — `#142`/`#235`와 동일 인스턴스
- `SPRING_PROFILES_ACTIVE=prod,performance` — `performance` 프로파일 fake Bean(`PerformanceTestPaymentReader`/`PerformanceTestRefundRequester`/`PerformanceTestReservationCompletionHook`)으로 PortOne 실호출을 완전히 대체. 나머지 설정(JWT·PortOne webhook secret 등)은 인스턴스 기존 `prod` 환경변수를 그대로 사용(`application-performance.yml`은 배포 아티팩트에 미포함, PR #250 댓글 2026-08-13T06:09:28Z 참고)
- MySQL: Test RDS(`bobfull_test` 스키마), 버전 미확인 — `#235`와 동일한 기존 한계
- DB Connection Pool: HikariCP `maximum-pool-size=10`(`#142`/`#235`와 동일, 이번 측정에서 별도로 늘리지 않음)
- Redis: 인스턴스 로컬 docker 컨테이너(운영 ElastiCache와 별도, `#146` 측정 중 확인됨) — 환불 완료 경로는 Redis에 의존하지 않아 이번 측정과 무관
- 모니터링: 로컬 Prometheus(`monitoring/docker-compose.yml`)가 `http://15.164.234.170:8080/actuator/prometheus`를 15초 간격으로 직접 스크래핑(`#235`와 동일 방식)
- 테스트 데이터 규모: 시나리오별 `setup()`이 그때그때 만드는 합성 데이터(수백~천 건, 대량 사전 시딩 없음) — 정확한 수치는 §4 표 참고
- H2 vs MySQL: 이 문서의 모든 수치는 실제 MySQL(AWS) 기준이다. H2는 이번 측정에 사용하지 않았고, PR #250의 유닛/통합 테스트(회귀 검증)에서만 사용했다.

### 측정 중 발견한 인프라 이슈 — 실메일 발송이 Fixture 준비를 지연시킴

`setup()`이 재사용하는 `POST /api/payments/{id}/complete`(예약 확정)는 `ReservationConfirmationService`를 통해 **실제 SMTP 메일 발송을 요청 스레드에서 동기 실행**한다(`EmailOutboxEventService.enqueue()` → `afterCommit` 콜백 내 동기 처리, 기존 아키텍처). `application*.yml`에 `spring.mail.*` 타임아웃 설정이 없어 메일 서버 응답이 늦어지면 요청이 사실상 무제한 대기한다.

측정 도중 이 경로가 원인으로 추정되는 애플리케이션 헬스 다운(CPU·DB Pool은 모두 idle인데 `/actuator/health`만 DOWN)이 실제로 발생했고, 인프라 담당자가 메일 로그인 문제를 확인·조치한 뒤 재개했다. 조치 후에도 이메일 발송 자체는 평균 500ms~1초대로 느려(p99 1.5초대) `setup()` 소요 시간이 예상보다 훨씬 길었다(시나리오당 7~15분).

다만 **실제 측정 대상인 취소(`accept()`/`acceptEntireReservationCancellation`) 경로에는 이메일 발송이 없다**(코드 확인 완료 — 이메일은 결제 확정과 모집 마감 스케줄러 경로에서만 발생). 따라서 아래 시나리오별 Trend 지표(즉시응답/웹훅 완료 시간 등)는 메일 지연에 오염되지 않았다. 다만 이 문제로 인해 `setup()`이 감당 가능한 시간 안에 끝나도록 각 시나리오의 픽스처 풀을 대폭 축소하고 측정 시간도 30초로 단축했다(§7 한계 참고).

## 시나리오별 결과 (AWS, Load 단계 1개 지점, 워밍업 후 측정)

| 시나리오 | 부하 | 반복 수 | 지표 | avg | p95 | p99 | 실패율 |
|---|---|---:|---|---:|---:|---:|---:|
| A 기준선 | 20 iter/s×30s | 601 | 즉시응답 완료 | 45.2ms | 83ms | 179ms | 0% |
| A 기준선 | 〃 | 〃 | 웹훅 완료 | 21.1ms | 40.3ms | 81ms | 0% |
| B 동일예약 경쟁(3인×300그룹) | 10 group/s×30s | 300그룹 | 그룹 배치 전체 | 34.7ms | 45.1ms | 69.1ms | 0% |
| B 〃 | 〃 | 〃 | 참여자별 개별 | 25.8ms | 37.5ms | 54.4ms | 0% |
| C 중복·경쟁 | 20 iter/s×30s | 600 | 동시 경쟁(즉시+웹훅) | 46.8ms | 115ms | 272ms | 0% |
| C 〃 | 〃 | 〃 | 순차 중복(웹훅만) | 12.2ms | 26ms | 53.1ms | 0% |
| D 내부 지연 300ms | 20 iter/s×30s | 601 | 지연 주입 응답 | 346ms | 401ms | 510ms | 0% |
| E 완료 실패 | 20 iter/s×30s | 600 | 즉시응답 경로 실패(500) | 54.9ms | 78.5ms | 551ms | 0%(의도된 500) |
| E 〃 | 〃 | 〃 | 웹훅 경로 실패(500) | 16.6ms | 24ms | 80.4ms | 0%(의도된 500) |
| F 외부 지연 3000ms | 20 iter/s×30s | 576 | 취소 응답(외부 지연 포함) | 3054.7ms | 3.1s | 3.2s | 0% |

실패율은 "실제 오류(5xx·타임아웃)" 기준이며, E의 500·F의 지연은 의도적으로 주입한 결과다(Issue 본문 판정 방식과 동일, 모든 시나리오 `checks_succeeded` 100%).

## 락·Connection Pool 분석 (Prometheus, 측정 구간)

| 시나리오 | CPU 최대 | HikariCP active 최대 | HikariCP pending 최대 |
|---|---:|---:|---:|
| A | 15.7% | 2 | 0 |
| B | 13.1% | 2 | 0 |
| C | 11.7% | 2 | 0 |
| D(지연 300ms) | 8.8% | **7** | 0 |
| E | 11.6% | 1 | 0 |
| F(외부 지연 3000ms) | 9.9% | 2 | 0 |

D에서만 HikariCP active가 눈에 띄게 늘었다(2→7) — Reservation 락을 인위적으로 더 오래 쥐고 있는 만큼 동시 커넥션 점유가 늘어난 것으로, 스크립트 설계 의도와 일치한다. 다만 pending은 전 시나리오에서 0으로, 이번 부하 수준(20 iter/s)에서는 풀(10) 포화까지 상당한 여유가 있다.

InnoDB 레벨 실제 row lock wait 카운터(`performance_schema.data_lock_waits`)는 MySQL exporter가 없어 수집하지 못했다 — `#235`에서 이미 확인된 동일한 인프라 한계이며, HikariCP active/pending을 락 경합의 대리 지표로 사용했다.

## 외부 API 지연과 내부 처리 지연 비교

| 구분 | 주입 지연 | 측정 응답시간 | HikariCP active 최대 | 해석 |
|---|---:|---:|---:|---|
| D(내부 Reservation 완료 지연) | 300ms | avg 346ms | **7** | 내부 지연은 그만큼 DB 커넥션 점유 시간을 늘린다 |
| F(외부 PortOne 지연) | 3000ms | avg 3055ms | **2** | 외부 지연은 응답시간만 그대로 늘릴 뿐 DB 자원은 전혀 늘리지 않는다 |

같은 수준(수백ms~수초)의 지연이라도 **어디서 발생하느냐에 따라 DB 자원 영향이 완전히 다르다**는 것이 이번 측정의 핵심 확인 사항이다. PortOne 요청이 Reservation 락·내부 완료 트랜잭션 밖(두 `REQUIRES_NEW` 트랜잭션 사이의 평범한 메서드 호출)에서 실행되도록 만든 기존 Port·Adapter 분리 설계가 실측으로 검증됐다.

## 이번 측정의 한계

1. **Load 단계 1개 지점만 측정**했다. Issue 본문이 요구한 1/10/30/50/100 VU 단계적 확대나 Stress 단계는 수행하지 못했다 — `setup()`이 실메일 발송에 의존해 픽스처 규모를 키울수록 준비 시간이 비현실적으로 늘어났기 때문이다(§측정 환경 "인프라 이슈" 참고). 현재 결과는 "20 iter/s(그룹 10/s) 수준에서 여유가 크다"는 것만 확인하며, 그보다 훨씬 높은 동시성에서 락 경쟁이나 Pool 포화가 나타나는지는 확인하지 못했다.
2. **InnoDB 레벨 row lock wait 실측치 없음** — HikariCP active/pending을 대리 지표로 사용했다(`#235`와 동일한 기존 한계).
3. **D/F는 각각 1개 지연값만 측정**했다(D: 300ms, F: 3000ms). Issue 본문이 제시한 나머지 단계(D: 100/500/1000ms, F: 500ms/1s/timeout/connection reset)는 이번 라운드에서 실행하지 않았다 — 필요하면 후속으로 추가 실행 가능하다(스크립트는 이미 파라미터화돼 있음, §9 참고).
4. 이번 측정 도중 실메일 발송 지연으로 인한 애플리케이션 헬스 다운이 실제로 발생했다 — 재발 방지책(메일 타임아웃 설정 등)은 이 Issue 범위 밖이라 별도 후속 이슈로 분리한다(§9).

## Spring Event 전환 판단

Issue 본문 "Spring Event 전환 최소 성능 악화 기준"(Human 확정 계약, 2026-08-13T03:34:40Z):

> 환불 완료 웹훅 p95가 목표(300ms) 대비 2배(600ms) 초과 + 원인이 예약 완료 처리 비중이며, Reservation 락 대기 또는 DB Pool pending이 전체 처리 시간의 30% 이상을 차지할 때 전환 검토

이번 측정 범위(Load 1개 지점) 안에서는:

- A/B/C/E 시나리오는 p95가 모두 115ms 이하로, 600ms는커녕 목표(300ms)에도 크게 못 미친다.
- D(내부 지연 300ms 주입)에서도 p95 401ms로 600ms 기준에 도달하지 않았다.
- HikariCP pending은 전 시나리오 0 — "락 대기·Pool pending이 30% 이상"에 해당하는 사례가 없다.

**전환 게이트 조건이 하나도 충족되지 않는다.**

### 결론: A안 — 현재 동기 Port·Adapter 구조 유지

- 이번에 실측한 부하 수준(20 iter/s, 그룹 10/s)에서 목표 성능(p95 300ms·p99 800ms)을 여유 있게 충족한다.
- 락·DB Pool 병목이 관측되지 않았다(pending 항상 0, active 최대 7/10).
- 구조 단순성과 즉시 정합성의 이점이 이번 측정 범위 안에서는 여전히 더 크다.

다만 D의 결과(300ms 내부 지연만으로 p95가 목표를 넘김)는 "향후 예약 완료 후속 처리가 실제로 무거워지면 재검토가 필요하다"는 조기 경고로 기록해 둔다 — 지금 당장 B안(내부 최적화)이나 C안(이벤트 전환)을 선택할 근거는 아니다.

**한계 고지**: 이 결론은 §7에서 밝힌 대로 Load 1개 지점 실측에 근거한다. 실제 운영 트래픽이 이번에 측정한 동시성 수준을 크게 초과하는 경우(예: 인기 회차 대량 취소가 동시에 몰리는 상황), 이 결론을 그대로 적용하기 전에 Stress 단계 재측정이 필요하다.

## 후속 조치 제안

1. **(별도 Issue, #146 범위 밖) 메일 발송 타임아웃 설정**: `spring.mail.properties.mail.smtp.connectiontimeout`/`timeout`/`writetimeout`이 설정돼 있지 않아, 메일 서버가 멈추면 예약 확정·모집 마감 요청 스레드가 무제한 대기할 수 있는 운영 위험이 있다. 이번 측정 중 실제로 재현됐다.
2. **(선택) Stress 단계 재측정**: 위 메일 타임아웃이 해결되거나 `setup()`을 위한 fake 메일 Bean이 추가되면, Issue 본문이 요구한 30/50/100 VU·Stress 단계를 마저 실측할 수 있다.
3. **(선택) D/F 추가 지연값 측정**: 스크립트가 이미 파라미터화돼 있어(`RESERVATION_COMPLETION_DELAY_MS`, `PORTONE_EXTERNAL_DELAY_MS`/`PORTONE_EXTERNAL_RESULT`), 필요 시 나머지 단계만 추가로 실행하면 된다.

## 실행 방법

```bash
# 로컬 Prometheus를 AWS 인스턴스에 연결(모니터링 준비)
cd monitoring
BOBFULL_BACKEND_METRICS_TARGET=15.164.234.170:8080 \
  GRAFANA_ADMIN_PASSWORD=<placeholder> GRAFANA_SLACK_WEBHOOK_URL=<placeholder> GRAFANA_SLACK_RECIPIENT=<placeholder> \
  docker compose up -d prometheus

# 시나리오 A 예시(워밍업 → 실측)
k6 run -e STAGE=load -e BASE_URL=http://15.164.234.170:8080 \
  -e PORTONE_WEBHOOK_SECRET="<실제 배포 환경의 webhook secret>" \
  -e LOAD_DURATION=15s -e LOAD_RATE=20 -e RESERVATION_POOL_SIZE=350 -e SETUP_TIMEOUT=1200s \
  k6/scenarios/refund-completion-baseline.js

k6 run -e STAGE=load -e BASE_URL=http://15.164.234.170:8080 \
  -e PORTONE_WEBHOOK_SECRET="<실제 배포 환경의 webhook secret>" \
  -e LOAD_DURATION=30s -e LOAD_RATE=20 -e RESERVATION_POOL_SIZE=700 -e SETUP_TIMEOUT=1800s \
  --summary-export=A-result.json \
  k6/scenarios/refund-completion-baseline.js

# 시나리오 D/F는 지연값을 -e RESERVATION_COMPLETION_DELAY_MS / -e PORTONE_EXTERNAL_DELAY_MS 로 변경해 반복 실행
```

원본 로그·Summary JSON·Prometheus 스냅샷: `raw/`(시나리오별 `{A..F}-AWS-load-{warmup,after-batch}.{log,json}`), `raw/prometheus/`(시나리오별 `{A..F}-{hikari_active,hikari_pending,cpu}.json`).
