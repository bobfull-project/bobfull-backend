# BobFull V3 Operations Flow Lab

## Phase 1 범위

이 Lab은 코드·Evidence를 재생하는 정적 인터랙티브 시뮬레이터다. 실제 JVM, Kafka, Redis, AWS를 제어하거나 실시간 runtime 상태를 표시하지 않는다.

- Chapter 1: V2 `AFTER_COMMIT`과 V3 Transactional Outbox의 동일 failure boundary를 동기화된 two-lane 진행 상태(4-stage lane strip)로 비교
- Chapter 2: `ChatMessage → Outbox → Kafka → AI Moderation`과 `NORMAL`, `PUBLISH_FAILURE`, `DUPLICATE_DELIVERY`, `AI_TRANSIENT_FAILURE`, `RETRY_EXHAUSTED_DLT`, `ACK_THEN_CRASH`
- Chapter 3: `LOCAL_TWO_INSTANCE_NORMAL`과 `REDIS_DELIVERY_MISS`
- Chapter 4: 인기 회차 조회 Hot-path 병목 개선(#142 발견 → #235 분리·배치 개선 → 동일 조건 Before/After → 남은 한계)

Canvas는 `Client → Web/STOMP → Application → DB` 뒤에 Outbox/Kafka/DLT Topic/AI와 Redis/App A·B/Local STOMP를 별도 lane으로 둔다. connector는 고정되고, 활성 path 위의 token만 이동한다. 이미 커밋되어 여전히 유효한 노드(예: 장애 발생 순간의 ChatMessage)는 `committed` 상태(초록 점선)로 dim과 구분해 지속 표시하며, `retryOwner`는 Step 데이터에 명시적으로 선언한다(추론하지 않음).

## 구조

- `scenario-data.js`: Chapter, Scenario, Step, 코드/Evidence 참조와 사실성 상태
- `app.js`: 재생 상태와 UI 렌더링
- `style.css`: Canvas와 발표/학습 모드 스타일

## 사실성 상태와 Source of Truth

- `verified`: 테스트 또는 직접 검증 Evidence가 있다.
- `design interpretation`: 코드·Evidence 경계를 바탕으로 한 설명이며 실제 runtime 재현이 아니다.
- `future / not verified`: 구현 또는 검증 전 항목이다.

근거는 [#176 ChatRoom Outbox](../../../evidence/v3/176-chatroom-outbox/README.md), [#183 Email Outbox](../../../evidence/v3/183-email-outbox/README.md), [#59 Kafka AI Pipeline](../../../evidence/v3/59-kafka-ai-pipeline/README.md), [#66 AI Moderation](../../../evidence/v3/66-ai-moderation/README.md), [#170 Redis Pub/Sub](../../../evidence/v3/170-chat-redis-pubsub/README.md)다.

`AI_TRANSIENT_FAILURE`(구 `AI_TIMEOUT`)는 #59 Evidence가 실제로 검증한 "AI 호출 1회 강제 실패 → Kafka Retry로 2회차 성공"만 `verified`로 표시한다. 실제 timeout 주입은 검증하지 않았으므로 이름·narration 어디에도 "timeout"을 사용하지 않는다.

`RETRY_EXHAUSTED_DLT`는 `ChatModerationDltRecoverer`가 실제로 DLT 토픽에 발행한 뒤 Kafka Consumer 경로를 거치지 않고 `ChatModerationService.recordFinalFailure`를 직접 호출하는 코드 구조를 그대로 반영해, Canvas에 별도 `DLT Topic` 노드와 `Kafka → DLT → DB` 경로를 명시한다.

`LOCAL_TWO_INSTANCE_NORMAL`은 local App A:8080 ↔ App B:8081 STOMP fan-out만 `verified`로 표시한다. Redis 중단·복구, cursor N/N 실제 복구, ALB/WSS 및 cross-EC2 검증은 완료로 표현하지 않는다. Redis Pub/Sub은 best-effort real-time fan-out이고 DB가 Source of Truth이며, 단절 중 메시지는 자동 replay되지 않고 cursor 조회가 복구 계약이다.

Chapter 4의 모든 수치는 [#142 인기 회차 예약 부하 측정](../../../evidence/v3/142-reservation-peak/README.md), [#235 Hot-path 병목 개선](../../../evidence/v3/restaurant-view-hotpath/README.md), [#62 검색 Redis Cache 판단](../../../evidence/v3/62-search-cache/README.md)의 실측값을 그대로 인용한다(`factStatus=measured`). "병목 완전 제거"라고 쓰지 않고 "포화 시작 임계점이 약 40 iter/s에서 약 320 iter/s로 8배 이동했으며, 최고 부하 단계에서는 CPU·HikariCP Pool이 다시 포화된다"고 명시한다. #62(검색 Redis Cache)는 별도 Chapter가 아니라 Chapter 4 학습 상세의 "다른 성능 의사결정" 카드로만 짧게 연결한다.

`#169`, `#191`, `#192`는 아직 `future / not verified` Evidence Gate다. 실제 Evidence가 생길 때만 Scenario 또는 Chapter로 승격한다. 발표 모드에서는 지금 보고 있는 Chapter와 무관하므로 학습 모드에서만 노출한다.

## 알려진 UX 한계

- Canvas는 데스크톱 발표 화면을 우선한다. 작은 화면에서는 topology 라벨의 가독성이 낮아질 수 있다(모바일 폭에서는 `min-width:760px`로 가로 스크롤이 발생한다).
- 발표 모드에서 Pause/Prev/Next/Speed는 시각적으로만 축소되고 기능은 모든 모드에서 동일하게 동작한다.
