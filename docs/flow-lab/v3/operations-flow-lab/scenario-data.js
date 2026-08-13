/* Evidence-backed data only. Every step must declare factStatus and visual state. */
const FACT = { MERGED: "merged", VERIFIED: "verified", MEASURED: "measured", DESIGN: "design interpretation", FUTURE: "future / not verified" };
const ref = (label, href) => ({ label, href });
const evidence = {
  chatroom: ref("#176 ChatRoom Outbox Evidence", "../../../evidence/v3/176-chatroom-outbox/README.md"),
  email: ref("#183 Email Outbox Evidence", "../../../evidence/v3/183-email-outbox/README.md"),
  pipeline: ref("#59 Kafka AI Pipeline Evidence", "../../../evidence/v3/59-kafka-ai-pipeline/README.md"),
  moderation: ref("#66 AI Moderation Evidence", "../../../evidence/v3/66-ai-moderation/README.md"),
  redis: ref("#170 Redis Pub/Sub Evidence", "../../../evidence/v3/170-chat-redis-pubsub/README.md")
};
const visual = (activeNodes, activeEdges, token, outcome, branch) => ({ activeNodes, activeEdges, token, outcome, branch });
const step = (id, actor, target, action, narration, details) => {
  if (!details.factStatus || !details.visual) throw new Error(`Step ${id} requires factStatus and visual`);
  return { id, actor, target, action, narration, domainState: null, transaction: null, lock: null, outbox: null, kafka: null, consumer: null, redis: null, logs: null, metrics: null, codeReferences: [], evidenceReferences: [], limits: null, ...details };
};
const core = visual(["client", "web", "app", "db"], ["request", "persist"], "request", null, "core");
const chapters = [
  { id: "outbox", title: "Chapter 1 — AFTER_COMMIT에서 Transactional Outbox로", summary: "같은 failure boundary에서 V2 메모리 후속 처리와 V3 영속 Outbox를 동기화해 비교한다.", scenarios: [{ id: "chatroom-outbox", title: "ChatRoom 생성: Before / After", comparison: true, steps: [
    step("commit", "Payment completion", "Core transaction", "✓ core COMMIT", "같은 핵심 거래가 먼저 확정된다.", { domainState: "Payment / Reservation / Participant COMMITTED", transaction: "V2/V3 모두 핵심 DB transaction COMMIT", factStatus: FACT.VERIFIED, visual: core, comparison: { v2: "COMMIT", v3: "business + Outbox COMMIT" }, evidenceReferences: [evidence.chatroom] }),
    step("after-commit", "V2 listener / V3 processor", "Follow-up work", "◆ follow-up starts", "동일한 후속 ChatRoom 생성 실패 경계로 진입한다.", { outbox: "V3 PENDING", factStatus: FACT.VERIFIED, visual: visual(["db", "outbox", "app"], ["outbox-claim"], "event", null, "outbox"), comparison: { v2: "AFTER_COMMIT (memory)", v3: "PENDING → PROCESSING" }, evidenceReferences: [evidence.chatroom] }),
    step("failure", "Follow-up work", "ChatRoom service", "× creation failure", "후속 생성 실패가 이미 확정된 핵심 상태를 되돌리지는 않는다.", { outbox: "V3 PENDING for retry", factStatus: FACT.VERIFIED, visual: visual(["app", "outbox"], ["outbox-claim"], "failure", "failure", "outbox"), comparison: { v2: "failure → durable retry basis 없음", v3: "failure → PENDING preserved" }, limits: "V2 BEFORE는 #176 baseline Evidence의 AFTER_COMMIT 실패 검증이다. 실제 JVM kill 재현은 아니다.", evidenceReferences: [evidence.chatroom] }),
    step("retry", "Outbox processor", "ChatRoom service", "↻ retry → COMPLETED", "V3만 DB에 남은 의도를 다시 claim하여 ChatRoom을 안전하게 생성한다.", { outbox: "PENDING → PROCESSING → COMPLETED", lock: "조건부 PENDING → PROCESSING claim", transaction: "짧은 claim/complete transaction", factStatus: FACT.VERIFIED, visual: visual(["outbox", "app", "db"], ["outbox-claim", "outbox-complete"], "retry", "completed", "outbox"), comparison: { v2: "durable retry basis 없음", v3: "retry → COMPLETED" }, codeReferences: ["ChatRoomOutboxProcessor", "ChatRoomCreationService.createIfAbsent"], evidenceReferences: [evidence.chatroom, evidence.email] })
  ]}] },
  { id: "kafka-ai", title: "Chapter 2 — ChatMessage → Outbox → Kafka → AI Moderation", summary: "Outbox는 DB→Kafka 전달, Kafka는 AI Consumer retry/DLT를 책임진다.", scenarios: [
    { id: "normal", title: "NORMAL", steps: [
      step("send", "Client", "ChatMessageCommandService", "● STOMP SEND", "메시지 저장 요청이 Application으로 들어온다.", { transaction: "ChatMessage + CHAT_MESSAGE_CREATED Outbox transaction", factStatus: FACT.VERIFIED, visual: core, codeReferences: ["ChatMessageCommandService.send"], evidenceReferences: [evidence.pipeline] }),
      step("commit", "Application", "DB", "✓ COMMIT", "ChatMessage와 AI 분석 의도가 함께 확정된다.", { domainState: "ChatMessage COMMITTED", transaction: "COMMITTED", outbox: "PENDING", factStatus: FACT.VERIFIED, visual: visual(["app", "db", "outbox"], ["persist", "outbox-write"], "commit", "committed", "outbox"), evidenceReferences: [evidence.pipeline] }),
      step("publish", "Outbox processor", "Kafka", "◆ publish / broker ACK", "Outbox Processor가 Broker ACK 후 Outbox를 COMPLETED로 기록한다.", { outbox: "PROCESSING → COMPLETED", kafka: "published", factStatus: FACT.VERIFIED, visual: visual(["outbox", "kafka"], ["outbox-publish"], "event", "acknowledged", "outbox"), codeReferences: ["ChatMessageOutboxProcessor"], evidenceReferences: [evidence.pipeline] }),
      step("analyze", "Kafka consumer", "LLM provider", "◆ consume → analyze", "Consumer가 AI Structured Output을 검증하고 moderation을 저장한다.", { consumer: "ChatModerationConsumer", domainState: "ChatModeration stored", factStatus: FACT.VERIFIED, visual: visual(["kafka", "consumer", "llm", "db"], ["kafka-consume", "ai-call"], "event", "completed", "kafka"), codeReferences: ["ChatModerationConsumer", "ChatModerationService.analyze", "AiModerationPort", "SpringAiModerationAdapter"], evidenceReferences: [evidence.pipeline, evidence.moderation] })
    ]},
    { id: "publish-failure", title: "PUBLISH_FAILURE", steps: [
      step("commit", "Application", "DB", "✓ ChatMessage + Outbox COMMIT", "채팅과 전달 의도는 먼저 확정된다.", { domainState: "ChatMessage COMMITTED", outbox: "PENDING", transaction: "COMMITTED", factStatus: FACT.VERIFIED, visual: visual(["app", "db", "outbox"], ["persist", "outbox-write"], "commit", "committed", "outbox"), evidenceReferences: [evidence.pipeline] }),
      step("failure", "Outbox processor", "Kafka publish", "× publish failure injection", "발행 강제 실패 후에도 ChatMessage는 유지되고 Outbox가 전달 책임을 보유한다.", { outbox: "PENDING · attemptCount 증가", kafka: "publish failed", logs: "event=OUTBOX_RETRY_SCHEDULED", factStatus: FACT.VERIFIED, visual: visual(["outbox", "kafka"], ["outbox-publish"], "failure", "failure", "outbox"), limits: "verified — fault injection. actual broker outage / Kafka HA는 검증하지 않았다.", evidenceReferences: [evidence.pipeline] }),
      step("retry", "Outbox processor", "Kafka", "↻ backoff → publish", "backoff 뒤 재발행하고 Broker ACK를 받으면 COMPLETED가 된다.", { outbox: "PENDING → PROCESSING → COMPLETED", kafka: "published", factStatus: FACT.VERIFIED, visual: visual(["outbox", "kafka"], ["outbox-publish"], "retry", "acknowledged", "outbox"), evidenceReferences: [evidence.pipeline] })
    ]},
    { id: "duplicate", title: "DUPLICATE_DELIVERY", steps: [
      step("delivery", "Kafka", "ChatModerationConsumer", "◆ same event delivered again", "at-least-once 전달은 같은 이벤트를 다시 전달할 수 있다.", { kafka: "duplicate delivery", consumer: "second receive", factStatus: FACT.VERIFIED, visual: visual(["kafka", "consumer"], ["kafka-consume"], "event", null, "kafka"), evidenceReferences: [evidence.pipeline] }),
      step("guard", "ChatModerationService", "DB", "✓ idempotent guard", "analyze 안의 완료 확인과 ChatModeration.isCompleted()가 AI 재호출·중복 저장을 막는다.", { domainState: "ChatModeration 1건 유지", consumer: "idempotent", factStatus: FACT.VERIFIED, visual: visual(["consumer", "db"], ["ai-call"], "commit", "completed", "kafka"), codeReferences: ["ChatModerationService.analyze", "ChatModeration.isCompleted()", "chat_moderation UNIQUE"], evidenceReferences: [evidence.pipeline] })
    ]},
    { id: "ai-timeout", title: "AI_TIMEOUT", steps: [
      step("call", "Kafka consumer", "LLM provider", "◆ AI call", "AI timeout/5xx는 Consumer 처리 경계에서 발생한다.", { consumer: "processing failed", kafka: "retry eligible", factStatus: FACT.VERIFIED, visual: visual(["consumer", "llm"], ["ai-call"], "event", "failure", "kafka"), evidenceReferences: [evidence.pipeline, evidence.moderation] }),
      step("retry", "Kafka retry", "Consumer", "↻ next attempt", "Spring AI 내부는 1회로 제한하고 Kafka Consumer가 전체 retry를 소유한다.", { consumer: "retry", kafka: "최초 처리 포함 최대 3회", factStatus: FACT.VERIFIED, visual: visual(["kafka", "consumer"], ["kafka-consume"], "retry", null, "kafka"), codeReferences: ["FixedBackOff", "spring.ai.retry.max-attempts=1"], evidenceReferences: [evidence.pipeline, evidence.moderation] })
    ]},
    { id: "retry-exhausted-dlt", title: "RETRY_EXHAUSTED_DLT", steps: [
      step("retries", "Kafka consumer", "AI moderation", "↻ retries exhausted", "반복 AI 실패는 ChatMessage를 롤백하지 않고 retry를 소진한다.", { domainState: "ChatMessage remains COMMITTED", consumer: "3/3 attempts", kafka: "retry exhausted", factStatus: FACT.VERIFIED, visual: visual(["kafka", "consumer", "llm"], ["kafka-consume", "ai-call"], "retry", "failure", "kafka"), evidenceReferences: [evidence.pipeline] }),
      step("dlt", "Kafka", "DLT + service", "↓ DLT / final failure", "DLT 발행 뒤에만 ANALYSIS_FAILED를 한 번 기록한다.", { kafka: "DLT", consumer: "recordFinalFailure once", domainState: "ChatModeration ANALYSIS_FAILED", factStatus: FACT.VERIFIED, visual: visual(["kafka", "consumer", "db"], ["kafka-dlt"], "dlt", "dlt", "kafka"), evidenceReferences: [evidence.pipeline] })
    ]},
    { id: "ack-then-crash", title: "ACK_THEN_CRASH", steps: [
      step("boundary", "Outbox processor", "Kafka ACK → Outbox completion", "◆ acknowledged before completion record", "ACK와 Outbox 완료 기록 사이에는 중단 시 중복 전달 가능성 경계가 있다.", { outbox: "completion not yet recorded", kafka: "broker ACK", factStatus: FACT.DESIGN, visual: visual(["outbox", "kafka"], ["outbox-publish"], "event", "acknowledged", "outbox"), limits: "실제 process kill Evidence는 없다. 동일 이벤트 2회 전달 멱등성 검증을 대체 근거로 사용한다.", evidenceReferences: [evidence.pipeline] }),
      step("safe-repeat", "Consumer", "ChatModerationService", "✓ replay remains safe", "재발행 가능성 때문에 Consumer 멱등성이 필요하다.", { consumer: "idempotent guard", factStatus: FACT.DESIGN, visual: visual(["kafka", "consumer", "db"], ["kafka-consume"], "event", "completed", "kafka"), evidenceReferences: [evidence.pipeline] })
    ]}
  ]},
  { id: "redis", title: "Chapter 3 — Redis Pub/Sub cross-instance 실시간 채팅", summary: "Redis는 best-effort fan-out이며 DB cursor가 공식 복구 경로다.", scenarios: [
    { id: "local-two-instance-normal", title: "LOCAL_TWO_INSTANCE_NORMAL", steps: [
      step("save", "Client A → App A", "DB", "● SEND → ✓ COMMIT", "App A가 ChatMessage를 저장한다. DB가 Source of Truth다.", { domainState: "ChatMessage COMMITTED", transaction: "ChatMessage + AI Outbox transaction", factStatus: FACT.VERIFIED, visual: visual(["client", "web", "app", "db"], ["request", "persist"], "request", "committed", "core"), evidenceReferences: [evidence.redis] }),
      step("broadcast", "App A", "Redis Pub/Sub", "↠ publish once", "커밋 뒤 Redis에 한 번 발행한다. Controller 직접 local STOMP 발행은 없다.", { redis: "best-effort broadcast", factStatus: FACT.VERIFIED, visual: visual(["app", "db", "redis"], ["redis-publish"], "broadcast", null, "redis"), evidenceReferences: [evidence.redis] }),
      step("fanout", "Redis subscribers", "App A / App B", "↠ local STOMP fan-out", "각 Subscriber가 자기 local STOMP에 한 번 fan-out한다. DB 재저장·Redis 재발행은 하지 않는다.", { redis: "App A / App B local fan-out", domainState: "DB row remains one", factStatus: FACT.VERIFIED, visual: visual(["redis", "app-a", "app-b", "stomp"], ["redis-app-a", "redis-app-b", "local-stomp", "local-stomp-b"], "broadcast", "delivered", "redis"), evidenceReferences: [evidence.redis] })
    ]},
    { id: "redis-delivery-miss", title: "REDIS_DELIVERY_MISS", steps: [
      step("commit", "Application", "DB", "✓ ChatMessage COMMIT", "DB 저장은 성공하지만 Redis delivery 검증은 별도 경계다.", { domainState: "ChatMessage COMMITTED", factStatus: FACT.DESIGN, visual: visual(["app", "db"], ["persist"], "commit", "committed", "core"), limits: "Redis 중단·복구와 cursor N/N 실제 복구는 NOT_RUN이다.", evidenceReferences: [evidence.redis] }),
      step("miss", "Redis disconnect/failure", "Realtime fan-out", "× delivery may be missed", "Pub/Sub 누락은 자동 재전송하지 않으며 실시간 전달이 빠질 수 있다.", { redis: "no replay / no retry", logs: "CHAT_REALTIME_PUBLISH_FAILED", metrics: "bobfull_business_events{event=CHAT_REALTIME_PUBLISH_FAILED}", factStatus: FACT.DESIGN, visual: visual(["redis"], ["redis-publish"], "failure", "failure", "redis"), limits: "설계 해석: Redis 중단·복구 실험은 NOT_RUN.", evidenceReferences: [evidence.redis] }),
      step("recover", "Client", "DB cursor query", "● cursor recovery path", "복구 계약은 DB cursor 조회다. 이 화면은 실제 N/N cursor 복구 검증을 주장하지 않는다.", { domainState: "DB is Source of Truth", redis: "no automatic replay", factStatus: FACT.FUTURE, visual: visual(["client", "web", "app", "db"], ["request"], "request", "not verified", "core"), limits: "cursor N/N actual recovery와 ALB/WSS는 NOT_RUN.", evidenceReferences: [evidence.redis] })
    ]}
  ]}
];
