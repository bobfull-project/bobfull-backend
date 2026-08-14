# Issue #256 이메일 Outbox 요청 스레드 격리 Evidence

## 검증 대상

이메일 Outbox가 커밋 뒤 즉시 SMTP 처리를 시작하더라도, 요청 스레드는 전용 bounded executor에 작업을
제출한 뒤 반환해야 한다. 공통 `AfterCommitExecutor`와 ChatRoom AFTER_COMMIT 경로는 변경하지 않는다.

## 측정 계약

- Primary KPI: 느린 SMTP를 모사한 `processor.signal()`이 있어도 `dispatch()`가 500ms 미만에 반환한다.
- Secondary KPI: executor 제출 거부 시 `processor.signal()`이 호출되지 않는다.
- Guardrail: 이메일 발송 실패에도 결제·예약·참여자 트랜잭션은 커밋되고 이메일 Outbox는 `PENDING`으로 재시도된다.

## 기준 코드

- Before SHA: `e108c72faef56f4ee7708951f1f885a234f19044`
- After SHA: Draft PR 생성 전 최종 커밋으로 갱신 예정

## 환경·데이터·실행 조건

- 로컬 macOS, Java 17, Gradle 9.5.1
- `EmailOutboxSignalDispatcherTest`는 Mockito로 `processor.signal()`을 latch로 대기시켜 느린 SMTP I/O를 모사했다.
- `PaymentReservationConfirmationTransactionIntegrationTest`는 H2(MySQL mode)와 Fake 알림 adapter를 사용했다.

## Before 결과

코드 정적 확인 결과 `EmailOutboxEventService.enqueue()`의 AFTER_COMMIT 콜백이
`EmailOutboxProcessor.signal()`을 직접 호출했다. 따라서 SMTP 처리 시간이 요청 스레드 반환 시간에 포함됐다.
실환경 K6 재측정이나 실제 SMTP 지연 주입은 이번 작업에서 수행하지 않았다.

## 변경 내용

- 이메일 전용 bounded `ThreadPoolTaskExecutor`를 추가했다.
- AFTER_COMMIT에서는 `EmailOutboxSignalDispatcher.dispatch()`만 호출하고, 실제 Processor/SMTP는 executor 스레드에서 실행한다.
- 제출 거부 시 claim·complete를 호출하지 않고 경고 로그만 남겨 `PENDING` 이벤트를 기존 Scheduler가 회수할 수 있게 했다.
- production/local SMTP connection/read/write timeout을 5초 기본값으로 설정했다.

## After 결과

| 지표·현상 | Before | After | 판정 |
|---|---|---|---|
| 느린 signal 중 dispatch 반환 | 요청 스레드에서 직접 실행 | 500ms 미만 반환 단위 테스트 통과 | PASS |
| executor 제출 거부 | 전용 경계 없음 | processor 미호출 단위 테스트 통과 | PASS |
| 이메일 실패 시 핵심 트랜잭션 | #183 계약 유지 필요 | 결제 PAID·예약/참여자 저장, Email Outbox PENDING 확인 | PASS |
| 공통 AfterCommitExecutor | 동기 실행 | 기존 동기 회귀 테스트 통과 | PASS |

## 정합성 회귀 검증

- `AfterCommitExecutorTest`: 트랜잭션 동기화가 있으면 afterCommit에서 동기 실행되는 기존 계약을 확인했다.
- `PaymentReservationConfirmationTransactionIntegrationTest`: 이메일 실패가 핵심 결제·예약 트랜잭션을 롤백하지 않으며 PENDING 재시도 상태를 남기는 것을 확인했다.
- ChatRoom 경로의 구현 파일은 변경하지 않았고, 전체 build에 기존 ChatRoom 테스트가 포함된다.

## 구조화 로그·메트릭

제출 거부 시 `event=EMAIL_OUTBOX_SIGNAL_REJECTED`, `outboxEventId`, `status=PENDING`을 남긴다. 새 메트릭은 추가하지 않았다.

## 결과 해석

이번 검증은 요청 스레드와 SMTP I/O의 코드 경계 및 실패 격리를 확인한다. Outbox의 내구성·stale 복구·수신자별 멱등성은 #183 공통 구현과 기존 Scheduler 정책을 재사용한다.

## 검증 한계

- 실제 SMTP 서버, 실제 HTTP 요청, K6/AWS 환경에서 timeout 및 health 회복을 재측정하지 않았다.
- latch/Fake 기반 테스트는 executor 제출 경계와 상태 분리를 검증하지만 SMTP 프로토콜 자체의 timeout 동작은 검증하지 않는다.

## 관련

- Issue: #256
- PR: Draft PR 생성 후 갱신 예정
- ADR: 해당 없음
- 기존 Evidence: `docs/evidence/v3/183-email-outbox/README.md`
