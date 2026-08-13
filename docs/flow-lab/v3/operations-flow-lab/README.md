# BobFull V3 Operations Flow Lab

## Phase 1 범위

이 Lab은 코드·Evidence를 재생하는 정적 인터랙티브 시뮬레이터다. 실제 JVM, Kafka, Redis, AWS를 제어하거나 실시간 runtime 상태를 표시하지 않는다.

- Chapter 1: V2 `AFTER_COMMIT`과 V3 Transactional Outbox의 동일 failure boundary 비교
- Chapter 2: `ChatMessage → Outbox → Kafka → AI Moderation`과 `NORMAL`, `PUBLISH_FAILURE`, `DUPLICATE_DELIVERY`, `AI_TIMEOUT`, `RETRY_EXHAUSTED_DLT`, `ACK_THEN_CRASH`
- Chapter 3: `LOCAL_TWO_INSTANCE_NORMAL`과 `REDIS_DELIVERY_MISS`

Canvas는 `Client → Web/STOMP → Application → DB` 뒤에 Outbox/Kafka/AI와 Redis/App A·B/Local STOMP를 별도 lane으로 둔다. connector는 고정되고, 활성 path 위의 token만 이동한다.

## 구조

- `scenario-data.js`: Chapter, Scenario, Step, 코드/Evidence 참조와 사실성 상태
- `app.js`: 재생 상태와 UI 렌더링
- `style.css`: Canvas와 발표/학습 모드 스타일

## 사실성 상태와 Source of Truth

- `verified`: 테스트 또는 직접 검증 Evidence가 있다.
- `design interpretation`: 코드·Evidence 경계를 바탕으로 한 설명이며 실제 runtime 재현이 아니다.
- `future / not verified`: 구현 또는 검증 전 항목이다.

근거는 [#176 ChatRoom Outbox](../../../evidence/v3/176-chatroom-outbox/README.md), [#183 Email Outbox](../../../evidence/v3/183-email-outbox/README.md), [#59 Kafka AI Pipeline](../../../evidence/v3/59-kafka-ai-pipeline/README.md), [#66 AI Moderation](../../../evidence/v3/66-ai-moderation/README.md), [#170 Redis Pub/Sub](../../../evidence/v3/170-chat-redis-pubsub/README.md)다.

`LOCAL_TWO_INSTANCE_NORMAL`은 local App A:8080 ↔ App B:8081 STOMP fan-out만 `verified`로 표시한다. Redis 중단·복구, cursor N/N 실제 복구, ALB/WSS 및 cross-EC2 검증은 완료로 표현하지 않는다. Redis Pub/Sub은 best-effort real-time fan-out이고 DB가 Source of Truth이며, 단절 중 메시지는 자동 replay되지 않고 cursor 조회가 복구 계약이다.

`#169`, `#191`, `#192`는 아직 `future / not verified` Evidence Gate다. 실제 Evidence가 생길 때만 Scenario 또는 Chapter로 승격한다.

## 알려진 UX 한계

- Canvas는 데스크톱 발표 화면을 우선한다. 작은 화면에서는 topology 라벨의 가독성이 낮아질 수 있다.
- Chapter 1은 동기화된 two-lane 상태를 표시하지만, 더 강한 lane별 애니메이션은 후속 개선 후보다.
