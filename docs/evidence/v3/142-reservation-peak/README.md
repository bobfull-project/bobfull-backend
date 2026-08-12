# #142 인기 회차 예약 부하 측정 — 시나리오 A·B (코드 + 로컬/AWS 기능 검증)

이번 실행 범위는 **코드 + 기능 검증(로컬, 이어서 AWS 연결 확인)**까지다. #207(AWS 실행 환경
준비, `bobfull-k6-test-app` EC2)이 완료돼 실제 AWS 대상으로도 실행해봤지만, 아래 AWS 실행은
여전히 2~50명 수준의 **동작 검증**이다 — 관측 시간축 연결(Prometheus/Grafana), 진짜 Load/Stress
규모, 결과 표·병목 전환점 기록은 별도로 이어서 진행한다. 성능 결론으로 쓰지 않는다.

시나리오 B의 결과는 Issue #142 "보강 계약"의 #60(예약 좌석 락 전략 재설계, 2026-08-11 기준
DRAFT) 선행조건이 아직 확정되지 않아 **현재 락 전략 기준의 임시 결과**다. #60이 끝나면 동일
조건으로 재검증한다.

## 측정·재현 환경

- 기준 Branch: `feature/142-peak-load-test`
- 로컬 환경: `./gradlew bootRun`(`spring.profiles.active=local`), `docker-compose`(MySQL 8.4,
  Redis 7) — 운영 환경과 CPU/네트워크 특성이 다르므로 병목 전환점 근거로 쓰지 않는다.
- AWS 환경: `bobfull-k6-test-app` EC2(`http://15.164.48.39:8080`, `/actuator/health` UP 확인) —
  실행자 로컬 PC에서 직접 실행. Prometheus/Grafana 시간축 연결은 아직 하지 않았다.
- Fixture: 시나리오별 `setup()`에서 API로 직접 생성(운영 데이터 없음, 합성 데이터만 사용).

## 시나리오 A — 예약 페이지 조회 폭주 (`peak-restaurant-view.js`)

같은 식당·같은 날짜를 반복 조회하는 hot-key 패턴을 모사한다(#63의 `restaurant-search.js`는
무작위 키워드로 여러 식당에 걸친 균등 부하를 모사하는 것과 대조적).

| 실행 | 결과 | Raw |
|---|---|---|
| 로컬 Smoke(`STAGE=smoke`) | PASS — checks 84/84(100%), http_req_failed 0% | `raw/A-peak-restaurant-view-smoke.log`, `.json` |
| AWS Smoke(`BASE_URL=http://15.164.48.39:8080`) | PASS — checks 84/84(100%), http_req_failed 0%, p95≈434ms(로컬 대비 네트워크 왕복 포함) | `raw/A-peak-restaurant-view-AWS-smoke.log`, `.json` |

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

### 결과 — AWS(`bobfull-k6-test-app`, 동일 동시성 단계)

| 동시 사용자 수 | 성공(200) | 충돌(409) | 예상 밖 응답 | threshold Gate | teardown 독립 검증 | Raw |
|---:|---:|---:|---:|---|---|---|
| 2 | 1 | 1 | 0 | PASS | PASS | `raw/B-create-race-2users-AWS.*` |
| 5 | 1 | 4 | 0 | PASS | PASS | `raw/B-create-race-5users-AWS.*` |
| 10 | 1 | 9 | 0 | PASS | PASS | `raw/B-create-race-10users-AWS.*` |
| 20 | 1 | 19 | 0 | PASS | PASS | `raw/B-create-race-20users-AWS.*` |
| 50 | 1 | 49 | 0 | PASS | PASS | `raw/B-create-race-50users-AWS.*` |

로컬·AWS 모든 동시성 단계에서 정확히 1명만 성공하고 나머지는 전부
`409 ACTIVE_RESERVATION_ALREADY_EXISTS`였다. `checks_succeeded`는 매 실행 100%였고
`peak_create_race_unexpected` 카운터는 항상 0이었다. `teardown()`에서 새 회원으로 같은
회차에 CREATE를 한 번 더 시도해도 409가 나, 경쟁 종료 후에도 배타 선점이 깨지지 않음을
독립적으로 재확인했다. AWS 실행에서는 동시성이 올라갈수록 iteration_duration이 늘었다
(2명 128ms → 50명 233ms 평균) — 로컬보다 네트워크 왕복이 포함돼 그런 것으로 보이며, 이 규모
(최대 50)에서는 DB Pool pending/lock wait 등 실제 병목 신호는 관측하지 않았다(Prometheus/Grafana
미연결, 별도 진행 필요).

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

## 남은 작업

- #60(예약 좌석 락 전략 재설계) 결과가 나오면 시나리오 B를 동일 조건으로 재검증
- 동일 시나리오를 AWS 대상으로 Load/Stress 규모(2~50명이 아니라 #63 "부하 모델" 계단식)까지 실행
- Prometheus/Grafana와 같은 시간축으로 Reservation·TimeSlot 락 대기시간, DB Pool active/pending 기록
- 결과 표(p95/p99/RPS/오류율)와 병목 전환점 기록
- JOIN 기반 좌석초과 테스트는 Fake 결제 확인 어댑터 도입 여부를 별도 결정한 뒤 진행
