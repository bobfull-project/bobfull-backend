# #142 인기 회차 예약 부하 측정 — 시나리오 A·B (코드 + 로컬 검증)

이번 실행 범위는 **코드 + 로컬 검증**까지다. 실제 AWS 위에서의 Load/Stress 실행, 관측 시간축
연결(Prometheus/Grafana), 결과 표·병목 전환점 기록은 #207(AWS 실행 환경 준비)이 끝난 뒤 이어서
진행한다. 아래는 스크립트가 실제로 의도대로 동작하는지 로컬에서 검증한 기록이다 — 성능 결론으로
쓰지 않는다.

## 측정·재현 환경

- 기준 Branch/Commit: `feature/142-peak-load-test`
- 환경: 로컬(`./gradlew bootRun`, `spring.profiles.active=local`), `docker-compose`(MySQL 8.4,
  Redis 7) — 운영 환경과 CPU/네트워크 특성이 다르므로 병목 전환점 근거로 쓰지 않는다.
- Fixture: 시나리오별 `setup()`에서 API로 직접 생성(운영 데이터 없음, 합성 데이터만 사용).

## 시나리오 A — 예약 페이지 조회 폭주 (`peak-restaurant-view.js`)

같은 식당·같은 날짜를 반복 조회하는 hot-key 패턴을 모사한다(#63의 `restaurant-search.js`는
무작위 키워드로 여러 식당에 걸친 균등 부하를 모사하는 것과 대조적).

| 실행 | 결과 | Raw |
|---|---|---|
| Smoke(`STAGE=smoke`) | PASS — checks 84/84(100%), http_req_failed 0% | `raw/A-peak-restaurant-view-smoke.log`, `.json` |

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

### 결과 (Issue #142 "초기 후보 단계": 2 → 5 → 10 → 20 → 50)

| 동시 사용자 수 | 성공(200) | 충돌(409 ACTIVE_RESERVATION_ALREADY_EXISTS) | 예상 밖 응답 | teardown 독립 검증 | Raw |
|---:|---:|---:|---:|---|---|
| 2 | 1 | 1 | 0 | PASS(배타 선점 유지 확인) | `raw/B-create-race-2users.*` |
| 5 | 1 | 4 | 0 | PASS | `raw/B-create-race-5users.*` |
| 10 | 1 | 9 | 0 | PASS | `raw/B-create-race-10users.*` |
| 20 | 1 | 19 | 0 | PASS | `raw/B-create-race-20users.*` |
| 50 | 1 | 49 | 0 | PASS | `raw/B-create-race-50users.*` |

모든 동시성 단계에서 정확히 1명만 성공하고 나머지는 전부 `409 ACTIVE_RESERVATION_ALREADY_EXISTS`였다.
`checks_succeeded`는 매 실행 100%(성공 응답의 `body.success` 확인)였고 `peak_create_race_unexpected`
카운터는 항상 0이었다. `teardown()`에서 새 회원으로 같은 회차에 CREATE를 한 번 더 시도해도
409가 나, 경쟁 종료 후에도 배타 선점이 깨지지 않음을 독립적으로 재확인했다.

### 트러블슈팅 — teardown 검증 로직 최초 버그

처음 작성한 `teardown()`은 GET 회차 조회 응답의 `reservationId` 필드가 채워졌는지로 성공을
판정했는데, CREATE prepare 성공(200)은 결제 완료 전이라 `Reservation`이 아직 생성되지 않은
상태다(위 "범위 한계" 참고) — 그래서 실제로는 정상 동작(1명 성공)인데도 매번 "예약이 생성되지
않았다"는 오탐 에러가 찍혔다. 새 회원으로 CREATE를 한 번 더 시도해 409를 받는지로 검증하는
방식으로 고쳤다(`peak-reservation-create-race.js` 참고).

## 남은 작업 (#207 AWS 실행 후)

- 동일 시나리오를 AWS 테스트 스택 대상으로 Load/Stress 규모까지 실행
- Prometheus/Grafana와 같은 시간축으로 Reservation·TimeSlot 락 대기시간, DB Pool active/pending 기록
- 결과 표(p95/p99/RPS/오류율)와 병목 전환점 기록
- JOIN 기반 좌석초과 테스트는 Fake 결제 확인 어댑터 도입 여부를 별도 결정한 뒤 진행
