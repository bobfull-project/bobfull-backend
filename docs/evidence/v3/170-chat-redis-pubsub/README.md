# Issue #170 — 다중 인스턴스 채팅 Redis Pub/Sub Evidence

## 검증 대상

`ChatMessage`를 DB에 저장·커밋한 뒤 Redis Pub/Sub으로 모든 애플리케이션 인스턴스에 전달하고, 각 인스턴스가 자신의 STOMP 세션으로 한 번 fan-out하는 경로다. AI Moderation의 Transactional Outbox→Kafka 경로는 별도이며 유지한다.

## 측정 계약

- Primary KPI: 서로 다른 인스턴스 A↔B의 N건 실시간 수신·누락·중복 수.
- Secondary KPI: DB commit→Redis publish→원격 STOMP 수신 latency, Redis publish 실패 수, subscriber 재연결 시간, messages/sec.
- Guardrail: ChatMessage DB 저장 1건, Redis subscriber의 DB 재저장·재발행 0건, 로컬 직접 STOMP+Redis 이중 전달 0건, 기존 참여 권한 유지.

## 기준 코드

- Before SHA: `a467fd9` (Issue #170 시작 시 최신 `develop`)
- After SHA: `e90edaf5e3f7ee2d5247b5a5545bb2ccc400dcd7`

## 환경·데이터·실행 조건

- Phase A: 로컬 JVM 단위 테스트, H2 기반 기존 테스트 설정. Redis 연결은 사용하지 않고 `StringRedisTemplate`과 `SimpMessagingTemplate`을 Mock으로 대체한다.
- Phase B: `NOT_RUN` — #169 또는 동등한 ALB/WSS 다중 EC2 환경이 아직 이 작업 트리에 제공되지 않았다.

## Before 결과

`a467fd9`의 Controller는 ChatMessage 저장 후 현재 인스턴스의 `SimpMessagingTemplate`에만 직접 발행한다. 다중 인스턴스 A→B 실측은 환경 부재로 `NOT_RUN`이며, 단일 Simple Broker 구조상 원격 인스턴스 세션을 알 수 없다는 코드 구조만 확인했다.

## 변경 내용

- DB 커밋 뒤 `bobfull:chat:messages`에 JSON payload를 한 번 발행한다.
- 모든 인스턴스는 Redis subscriber로 payload를 받아 자신의 `/sub/chat/rooms/{chatRoomId}`에만 전달한다.
- Controller의 직접 STOMP 발행을 제거해 발행 인스턴스도 Redis subscriber 경로를 한 번만 사용한다.
- Redis publish/subscribe 실패는 구조화 로그와 고정 enum 메트릭을 남기며 DB 메시지를 롤백하지 않는다.

## After 결과

| 지표·현상 | Before | After | 판정 |
|---|---|---|---|
| 저장 후 Redis payload 발행 | 없음 | 단위 테스트로 JSON/channel 확인 | PASS |
| 커밋 전·롤백 Redis 발행 | 해당 없음 | TransactionSynchronization 테스트에서 0회 | PASS |
| subscriber local STOMP fan-out | 없음 | destination 1회 단위 테스트 | PASS |
| Redis publish 실패 시 저장 경로 예외 전파 | 해당 없음 | publisher가 catch+metric | PASS |
| A→B / B→A 다중 인스턴스 수신·중복 | NOT_RUN | NOT_RUN | NOT_RUN |
| Redis 중단·복구, cursor N/N 복구, WSS 검증 | NOT_RUN | NOT_RUN | NOT_RUN |

## 정합성 회귀 검증

실행 명령:

```bash
./gradlew :test \
  --tests 'com.bobfull.chat.controller.ChatMessageControllerTest' \
  --tests 'com.bobfull.chat.service.ChatMessageCommandServiceTest' \
  --tests 'com.bobfull.chat.realtime.*' \
  --tests 'com.bobfull.outbox.service.ChatMessageOutboxSignalDispatcherTest' \
  --tests 'com.bobfull.outbox.service.ChatMessageOutboxProcessorIntegrationTest' \
  --rerun-tasks
```

결과: `BUILD SUCCESSFUL` (2026-08-12). 단일 인스턴스 자동 테스트는 Redis payload와 local fan-out, Redis 장애 격리, Controller 직접 발행 제거를 확인한다. 전체 `clean build`는 테스트 단계까지 실행했지만 도구가 종료 코드를 반환하지 않아 `NOT_RUN`으로 분리한다. 다중 인스턴스 전달률·latency·재연결과 WSS Upgrade는 검증하지 않았다.

## 구조화 로그·메트릭

- 로그: `CHAT_REALTIME_PUBLISH_FAILED`, `CHAT_REALTIME_SUBSCRIBE_FAILED`는 `messageId`·`chatRoomId` 또는 예외 유형만 기록하며 token·Authorization·원문은 기록하지 않는다.
- 메트릭: `bobfull_business_events{event=CHAT_REALTIME_PUBLISH_FAILED|CHAT_REALTIME_SUBSCRIBE_FAILED}`. 메시지 ID를 label로 사용하지 않는다.

## 결과 해석

Phase A는 단일 인스턴스에서 Redis 경로 전환과 실패 격리의 코드 계약만 검증한다. Redis Pub/Sub의 다중 인스턴스 실시간 전달 보장, 유실률, 성능 또는 운영 WSS 성공을 주장하지 않는다.

## 검증 한계

- #169 환경 부재로 서로 다른 EC2 A/B, ALB WSS, Redis 실제 재연결과 장애 중 cursor 복구는 `NOT_RUN`이다.
- 실제 Redis 서버를 사용하는 integration test와 반복 N건 latency/throughput 측정은 `NOT_RUN`이다.
- Pub/Sub 단절 구간의 메시지는 재생되지 않으며 DB cursor 조회가 복구 경로다.

## 관련

- Issue: #170
- ADR: [ADR 0011](../../../adr/0011-chat-redis-pubsub.md)
- AI Outbox/Kafka: [ADR 0010](../../../adr/0010-chat-message-outbox-kafka-pipeline.md)
