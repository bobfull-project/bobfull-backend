/* Evidence-backed data only. Every step must declare factStatus and visual state. */
const FACT = { MERGED: "merged", VERIFIED: "verified", MEASURED: "measured", DESIGN: "design interpretation", FUTURE: "future / not verified" };
const ref = (label, href) => ({ label, href });
const evidence = {
  chatroom: ref("#176 ChatRoom Outbox Evidence", "../../../evidence/v3/176-chatroom-outbox/README.md"),
  email: ref("#183 Email Outbox Evidence", "../../../evidence/v3/183-email-outbox/README.md"),
  pipeline: ref("#59 Kafka AI Pipeline Evidence", "../../../evidence/v3/59-kafka-ai-pipeline/README.md"),
  moderation: ref("#66 AI Moderation Evidence", "../../../evidence/v3/66-ai-moderation/README.md"),
  redis: ref("#170 Redis Pub/Sub Evidence", "../../../evidence/v3/170-chat-redis-pubsub/README.md"),
  peak: ref("#142 인기 회차 예약 부하 측정", "../../../evidence/v3/142-reservation-peak/README.md"),
  hotpath: ref("#235 Hot-path 병목 개선", "../../../evidence/v3/restaurant-view-hotpath/README.md"),
  searchCache: ref("#62 검색 Redis Cache 판단", "../../../evidence/v3/62-search-cache/README.md")
};
/* committedNodes: 현재 active path에 없어도 여전히 유효한(dim과 구별되는) 이미 커밋된 노드.
   badge: 특정 노드 옆에 짧은 텍스트 배지(성능 수치 등)를 표시한다. */
const visual = (activeNodes, activeEdges, token, outcome, branch, committedNodes, badge) =>
  ({ activeNodes, activeEdges, token, outcome, branch, committedNodes: committedNodes || [], badge: badge || null });
const step = (id, actor, target, action, narration, details) => {
  if (!details.factStatus || !details.visual) throw new Error(`Step ${id} requires factStatus and visual`);
  return { id, actor, target, action, narration, domainState: null, transaction: null, lock: null, outbox: null,
    kafka: null, consumer: null, redis: null, logs: null, metrics: null, retryOwner: null, performance: null,
    sideNote: null, codeReferences: [], evidenceReferences: [], limits: null, ...details };
};
/* Client -> Web/STOMP -> Application -> DB 전체 경로. 세 edge 모두 포함해야 token이 중간에서
   순간이동하지 않는다(request-app 누락은 독립 리뷰에서 확인된 실제 버그였다). */
const core = visual(["client", "web", "app", "db"], ["request", "request-app", "persist"], "request", null, "core");
const topology = {
  nodes: [["client", "Client"], ["web", "Web / STOMP"], ["app", "Application"], ["db", "DB"], ["outbox", "Outbox"],
    ["kafka", "Kafka"], ["dlt", "DLT Topic"], ["consumer", "AI Consumer"], ["llm", "LLM"], ["redis", "Redis Pub/Sub"],
    ["app-a", "App A"], ["app-b", "App B"], ["stomp", "Local STOMP"]],
  nodePositions: { client: [25, 190], web: [180, 190], app: [335, 190], db: [500, 190], outbox: [670, 35],
    kafka: [825, 35], dlt: [825, 145], consumer: [980, 35], llm: [1135, 35], redis: [670, 350],
    "app-a": [850, 290], "app-b": [850, 390], stomp: [1050, 340] },
  edges: {
    request: "M125 225 H180", "request-app": "M280 225 H335", persist: "M435 225 H500",
    "outbox-write": "M600 225 H630 V70 H670", "outbox-claim": "M670 70 H630 V160 H435", "outbox-complete": "M435 160 H630 V70 H670",
    "outbox-publish": "M770 70 H825", "kafka-consume": "M925 70 H980", "ai-call": "M1080 70 H1135",
    "kafka-dlt": "M875 105 V145", "dlt-db": "M825 180 H630 V225 H600",
    "redis-publish": "M600 225 H630 V385 H670", "redis-app-a": "M770 385 H810 V325 H850", "redis-app-b": "M770 385 H850",
    "local-stomp": "M950 325 H1000 V375 H1050", "local-stomp-b": "M950 425 H1000 V375 H1050"
  },
  labels: { request: [135, 180], "request-app": [285, 180], persist: [440, 180], "outbox-write": [620, 112],
    "outbox-publish": [775, 24], "kafka-consume": [930, 24], "ai-call": [1085, 24], "kafka-dlt": [880, 128],
    "dlt-db": [700, 216], "redis-publish": [620, 332], "redis-app-a": [780, 286], "redis-app-b": [780, 402],
    "local-stomp": [970, 305], "local-stomp-b": [970, 445] }
};
const stageLabels1 = ["CORE COMMIT", "FOLLOW-UP", "FAILURE", "OUTCOME"];
const chapters = [
  { id: "outbox", shortLabel: "Ch1 — AFTER_COMMIT → Outbox",
    title: "핵심 작업은 끝났는데 후속 작업이 사라진다면?", subtitle: "AFTER_COMMIT → Transactional Outbox",
    summary: "같은 failure boundary에서 V2 메모리 후속 처리와 V3 영속 Outbox를 동기화해 비교한다.",
    stageLabels: stageLabels1,
    scenarios: [{ id: "chatroom-outbox", title: "ChatRoom 생성: Before / After", comparison: true, steps: [
    step("commit", "Payment completion", "Core transaction", "✓ core COMMIT", "같은 핵심 거래가 먼저 확정된다.",
      { domainState: "Payment / Reservation / Participant COMMITTED", transaction: "V2/V3 모두 핵심 DB transaction COMMIT",
        factStatus: FACT.VERIFIED, visual: core,
        comparison: { v2: "COMMIT", v3: "business + Outbox COMMIT",
          v2States: ["active", "pending", "pending", "pending"], v3States: ["active", "pending", "pending", "pending"] },
        evidenceReferences: [evidence.chatroom] }),
    step("after-commit", "V2 listener / V3 processor", "Follow-up work", "◆ follow-up starts", "동일한 후속 ChatRoom 생성 실패 경계로 진입한다.",
      { outbox: "V3 PENDING", factStatus: FACT.VERIFIED,
        visual: visual(["db", "outbox", "app"], ["outbox-claim"], "event", null, "outbox", ["db"]),
        comparison: { v2: "AFTER_COMMIT (memory)", v3: "PENDING → PROCESSING",
          v2States: ["done", "active", "pending", "pending"], v3States: ["done", "active", "pending", "pending"] },
        evidenceReferences: [evidence.chatroom] }),
    step("failure", "Follow-up work", "ChatRoom service", "× creation failure", "후속 생성 실패가 이미 확정된 핵심 상태를 되돌리지는 않는다.",
      { domainState: "Payment / Reservation / Participant COMMITTED", outbox: "V3 PENDING for retry", retryOwner: "Outbox",
        factStatus: FACT.VERIFIED, visual: visual(["app", "outbox"], ["outbox-claim"], "failure", "failure", "outbox", ["db"]),
        comparison: { v2: "failure → durable retry basis 없음", v3: "failure → PENDING preserved",
          v2States: ["done", "done", "active", "blocked"], v3States: ["done", "done", "active", "pending"] },
        limits: "V2 BEFORE는 #176 baseline Evidence의 AFTER_COMMIT 실패 검증이다. 실제 JVM kill 재현은 아니다.",
        evidenceReferences: [evidence.chatroom] }),
    step("retry", "Outbox processor", "ChatRoom service", "↻ retry → COMPLETED", "V3만 DB에 남은 의도를 다시 claim하여 ChatRoom을 안전하게 생성한다.",
      { domainState: "Payment / Reservation / Participant COMMITTED", outbox: "PENDING → PROCESSING → COMPLETED",
        lock: "조건부 PENDING → PROCESSING claim", transaction: "짧은 claim/complete transaction", retryOwner: "Outbox",
        factStatus: FACT.VERIFIED, visual: visual(["outbox", "app", "db"], ["outbox-claim", "outbox-complete"], "retry", "completed", "outbox"),
        comparison: { v2: "durable retry basis 없음", v3: "retry → COMPLETED",
          v2States: ["done", "done", "done", "blocked"], v3States: ["done", "done", "done", "done"] },
        codeReferences: ["ChatRoomOutboxProcessor", "ChatRoomCreationService.createIfAbsent"],
        evidenceReferences: [evidence.chatroom, evidence.email] })
  ]}] },
  { id: "kafka-ai", shortLabel: "Ch2 — Outbox → Kafka → AI",
    title: "메시지는 저장됐는데 Kafka나 AI가 실패한다면?", subtitle: "Outbox → Kafka → AI Moderation",
    summary: "Outbox는 DB→Kafka 전달, Kafka는 AI Consumer retry/DLT를 책임진다.", scenarios: [
    { id: "normal", title: "NORMAL", steps: [
      step("send", "Client", "ChatMessageCommandService", "● STOMP SEND", "메시지 저장 요청이 Application으로 들어온다.",
        { transaction: "ChatMessage + CHAT_MESSAGE_CREATED Outbox transaction", factStatus: FACT.VERIFIED, visual: core,
          codeReferences: ["ChatMessageCommandService.send"], evidenceReferences: [evidence.pipeline] }),
      step("commit", "Application", "DB", "✓ COMMIT", "ChatMessage와 AI 분석 의도가 함께 확정된다.",
        { domainState: "ChatMessage COMMITTED", transaction: "COMMITTED", outbox: "PENDING", factStatus: FACT.VERIFIED,
          visual: visual(["app", "db", "outbox"], ["persist", "outbox-write"], "commit", "committed", "outbox"),
          evidenceReferences: [evidence.pipeline] }),
      step("publish", "Outbox processor", "Kafka", "◆ publish / broker ACK", "Outbox Processor가 Broker ACK 후 Outbox를 COMPLETED로 기록한다.",
        { domainState: "ChatMessage COMMITTED", outbox: "PROCESSING → COMPLETED", kafka: "published", factStatus: FACT.VERIFIED,
          visual: visual(["outbox", "kafka"], ["outbox-publish"], "event", "acknowledged", "outbox", ["db"]),
          codeReferences: ["ChatMessageOutboxProcessor"], evidenceReferences: [evidence.pipeline] }),
      step("analyze", "Kafka consumer", "LLM provider", "◆ consume → analyze", "Consumer가 AI Structured Output을 검증하고 moderation을 저장한다.",
        { domainState: "ChatMessage COMMITTED", consumer: "ChatModerationConsumer", factStatus: FACT.VERIFIED,
          visual: visual(["kafka", "consumer", "llm", "db"], ["kafka-consume", "ai-call"], "event", "completed", "kafka"),
          codeReferences: ["ChatModerationConsumer", "ChatModerationService.analyze", "AiModerationPort", "SpringAiModerationAdapter"],
          evidenceReferences: [evidence.pipeline, evidence.moderation] })
    ]},
    { id: "publish-failure", title: "PUBLISH_FAILURE", steps: [
      step("commit", "Application", "DB", "✓ ChatMessage + Outbox COMMIT", "채팅과 전달 의도는 먼저 확정된다.",
        { domainState: "ChatMessage COMMITTED", outbox: "PENDING", transaction: "COMMITTED", factStatus: FACT.VERIFIED,
          visual: visual(["app", "db", "outbox"], ["persist", "outbox-write"], "commit", "committed", "outbox"),
          evidenceReferences: [evidence.pipeline] }),
      step("failure", "Outbox processor", "Kafka publish", "× publish failure injection", "발행 강제 실패 후에도 ChatMessage는 유지되고 Outbox가 전달 책임을 보유한다.",
        { domainState: "ChatMessage remains COMMITTED", outbox: "PENDING · attemptCount 증가", kafka: "publish failed",
          retryOwner: "Outbox", logs: "event=OUTBOX_RETRY_SCHEDULED", factStatus: FACT.VERIFIED,
          visual: visual(["outbox", "kafka"], ["outbox-publish"], "failure", "failure", "outbox", ["db"]),
          limits: "verified — fault injection. actual broker outage / Kafka HA는 검증하지 않았다.", evidenceReferences: [evidence.pipeline] }),
      step("retry", "Outbox processor", "Kafka", "↻ backoff → publish", "backoff 뒤 재발행하고 Broker ACK를 받으면 COMPLETED가 된다.",
        { domainState: "ChatMessage remains COMMITTED", outbox: "PENDING → PROCESSING → COMPLETED", kafka: "published",
          retryOwner: "Outbox", factStatus: FACT.VERIFIED,
          visual: visual(["outbox", "kafka"], ["outbox-publish"], "retry", "acknowledged", "outbox", ["db"]),
          evidenceReferences: [evidence.pipeline] })
    ]},
    { id: "duplicate", title: "DUPLICATE_DELIVERY", steps: [
      step("delivery", "Kafka", "ChatModerationConsumer", "◆ same event delivered again", "at-least-once 전달은 같은 이벤트를 다시 전달할 수 있다.",
        { domainState: "ChatMessage remains COMMITTED", kafka: "duplicate delivery", consumer: "second receive", factStatus: FACT.VERIFIED,
          visual: visual(["kafka", "consumer"], ["kafka-consume"], "event", null, "kafka", ["db"]), evidenceReferences: [evidence.pipeline] }),
      step("guard", "ChatModerationService", "DB", "⏭ AI 호출 스킵", "analyze 안의 완료 확인과 ChatModeration.isCompleted()가 AI 재호출·중복 저장을 막는다 — Canvas에 LLM으로의 새 이동이 없는 것이 그 증거다.",
        { domainState: "ChatModeration 1건 유지", consumer: "idempotent · AI 호출 없음", factStatus: FACT.VERIFIED,
          visual: visual(["consumer", "db"], [], "commit", "skipped", "kafka", ["db"]),
          codeReferences: ["ChatModerationService.analyze", "ChatModeration.isCompleted()", "chat_moderation UNIQUE"],
          evidenceReferences: [evidence.pipeline] })
    ]},
    { id: "ai-transient-failure", title: "AI_TRANSIENT_FAILURE", steps: [
      step("call", "Kafka consumer", "LLM provider", "◆ AI call", "AI 호출이 처리 경계에서 한 번 강제 실패한다(#59 실제 fault injection).",
        { domainState: "ChatMessage remains COMMITTED", consumer: "processing failed", kafka: "retry eligible", factStatus: FACT.VERIFIED,
          visual: visual(["consumer", "llm"], ["ai-call"], "event", "failure", "kafka", ["db"]),
          evidenceReferences: [evidence.pipeline, evidence.moderation] }),
      step("retry", "Kafka retry", "Consumer", "↻ next attempt", "Spring AI 내부는 1회로 제한하고 Kafka Consumer가 전체 retry를 소유한다 — 2회차에 성공한다.",
        { domainState: "ChatMessage remains COMMITTED", consumer: "retry → success", kafka: "최초 처리 포함 최대 3회",
          retryOwner: "Kafka Consumer", factStatus: FACT.VERIFIED,
          visual: visual(["kafka", "consumer"], ["kafka-consume"], "retry", null, "kafka", ["db"]),
          codeReferences: ["FixedBackOff", "spring.ai.retry.max-attempts=1"], evidenceReferences: [evidence.pipeline, evidence.moderation] })
    ]},
    { id: "retry-exhausted-dlt", title: "RETRY_EXHAUSTED_DLT", steps: [
      step("retries", "Kafka consumer", "AI moderation", "↻ retries exhausted", "반복 AI 실패는 ChatMessage를 롤백하지 않고 retry를 소진한다.",
        { domainState: "ChatMessage remains COMMITTED", consumer: "3/3 attempts", kafka: "retry exhausted",
          retryOwner: "Kafka Consumer", factStatus: FACT.VERIFIED,
          visual: visual(["kafka", "consumer", "llm"], ["kafka-consume", "ai-call"], "retry", "failure", "kafka", ["db"]),
          evidenceReferences: [evidence.pipeline] }),
      step("dlt", "Kafka", "DLT topic → ChatModerationDltRecoverer", "↓ DLT / final failure", "Retry 소진 뒤 DLT로 옮기고, recordFinalFailure가 ANALYSIS_FAILED를 한 번만 기록한다.",
        { kafka: "DLT", domainState: "ChatMessage remains COMMITTED · ChatModeration ANALYSIS_FAILED", factStatus: FACT.VERIFIED,
          visual: visual(["kafka", "dlt", "db"], ["kafka-dlt", "dlt-db"], "dlt", "dlt", "kafka"),
          codeReferences: ["ChatModerationDltRecoverer", "ChatModerationService.recordFinalFailure"], evidenceReferences: [evidence.pipeline] })
    ]},
    { id: "ack-then-crash", title: "ACK_THEN_CRASH", steps: [
      step("boundary", "Outbox processor", "Kafka ACK → Outbox completion", "◆ acknowledged before completion record", "ACK와 Outbox 완료 기록 사이에는 중단 시 중복 전달 가능성 경계가 있다.",
        { domainState: "ChatMessage remains COMMITTED", outbox: "completion not yet recorded", kafka: "broker ACK", factStatus: FACT.DESIGN,
          visual: visual(["outbox", "kafka"], ["outbox-publish"], "event", "acknowledged", "outbox", ["db"]),
          limits: "실제 process kill Evidence는 없다. 동일 이벤트 2회 전달 멱등성 검증을 대체 근거로 사용한다.", evidenceReferences: [evidence.pipeline] }),
      step("safe-repeat", "Consumer", "ChatModerationService", "✓ replay remains safe", "재발행 가능성 때문에 Consumer 멱등성이 필요하다.",
        { domainState: "ChatMessage remains COMMITTED", consumer: "idempotent guard", factStatus: FACT.DESIGN,
          visual: visual(["kafka", "consumer", "db"], ["kafka-consume"], "event", "completed", "kafka"), evidenceReferences: [evidence.pipeline] })
    ]}
  ]},
  { id: "redis", shortLabel: "Ch3 — Redis Pub/Sub fan-out",
    title: "서버가 달라도 같은 채팅방 메시지를 어떻게 받는가?", subtitle: "Redis Pub/Sub cross-instance fan-out",
    summary: "Redis는 best-effort fan-out이며 DB cursor가 공식 복구 경로다.", scenarios: [
    { id: "local-two-instance-normal", title: "LOCAL_TWO_INSTANCE_NORMAL", steps: [
      step("save", "Client A → App A", "DB", "● SEND → ✓ COMMIT", "App A가 ChatMessage를 저장한다. DB가 Source of Truth다.",
        { domainState: "ChatMessage COMMITTED", transaction: "ChatMessage + AI Outbox transaction", factStatus: FACT.VERIFIED,
          visual: core, evidenceReferences: [evidence.redis] }),
      step("broadcast", "App A", "Redis Pub/Sub", "↠ publish once", "커밋 뒤 Redis에 한 번 발행한다. Controller 직접 local STOMP 발행은 없다.",
        { domainState: "ChatMessage COMMITTED", redis: "best-effort broadcast", factStatus: FACT.VERIFIED,
          visual: visual(["app", "db", "redis"], ["redis-publish"], "broadcast", null, "redis", ["db"]), evidenceReferences: [evidence.redis] }),
      step("fanout", "Redis subscribers", "App A / App B", "↠ local STOMP fan-out", "각 Subscriber가 자기 local STOMP에 한 번 fan-out한다. DB 재저장·Redis 재발행은 하지 않는다.",
        { domainState: "DB row remains one", redis: "App A / App B local fan-out", factStatus: FACT.VERIFIED,
          visual: visual(["redis", "app-a", "app-b", "stomp"], ["redis-app-a", "redis-app-b", "local-stomp", "local-stomp-b"], "broadcast", "delivered", "redis", ["db"]),
          evidenceReferences: [evidence.redis] })
    ]},
    { id: "redis-delivery-miss", title: "REDIS_DELIVERY_MISS", steps: [
      step("commit", "Application", "DB", "✓ ChatMessage COMMIT", "DB 저장은 성공하지만 Redis delivery 검증은 별도 경계다.",
        { domainState: "ChatMessage COMMITTED", factStatus: FACT.DESIGN, visual: visual(["app", "db"], ["persist"], "commit", "committed", "core"),
          limits: "Redis 중단·복구와 cursor N/N 실제 복구는 NOT_RUN이다.", evidenceReferences: [evidence.redis] }),
      step("miss", "Redis disconnect/failure", "Realtime fan-out", "× delivery may be missed", "Pub/Sub 누락은 자동 재전송하지 않으며 실시간 전달이 빠질 수 있다.",
        { domainState: "ChatMessage remains COMMITTED", redis: "no replay / no retry", retryOwner: "none (best-effort)",
          logs: "CHAT_REALTIME_PUBLISH_FAILED", metrics: "bobfull_business_events{event=CHAT_REALTIME_PUBLISH_FAILED}",
          factStatus: FACT.DESIGN, visual: visual(["redis"], ["redis-publish"], "failure", "failure", "redis", ["db"]),
          limits: "설계 해석: Redis 중단·복구 실험은 NOT_RUN.", evidenceReferences: [evidence.redis] }),
      step("recover", "Client", "DB cursor query", "● cursor recovery path", "복구 계약은 DB cursor 조회다. 이 화면은 실제 N/N cursor 복구 검증을 주장하지 않는다.",
        { domainState: "DB is Source of Truth", redis: "no automatic replay", factStatus: FACT.FUTURE,
          visual: visual(["client", "web", "app", "db"], ["request", "request-app", "persist"], "request", "not verified", "core", [], { nodeId: "client", text: "cursor 조회" }),
          limits: "cursor N/N actual recovery와 ALB/WSS는 NOT_RUN.", evidenceReferences: [evidence.redis] })
    ]}
  ]},
  { id: "hotpath-performance", shortLabel: "Ch4 — Hot-path Query Batch",
    title: "조회가 몰리면 어디가 병목이고, 어떻게 줄였는가?", subtitle: "Hot-path Query Batch Optimization",
    summary: "문제 발견 → 병목 분리 → 최소 변경 → 동일 조건 Before/After → 남은 한계.", scenarios: [
    { id: "batch-optimization", title: "인기 회차 조회 병목 개선", steps: [
      step("saturation", "K6 Load/Stress", "bobfull-k6-test-app", "▲ 조회 폭주", "인기 회차 조회가 몰리자 CPU와 DB Connection Pool이 거의 동시에 포화됐다(#142 실측, Stress 20→320 iter/s 계단식).",
        { factStatus: FACT.MEASURED, visual: visual(["client", "web", "app", "db"], ["request", "request-app", "persist"], "event", "failure", "core", [], { nodeId: "db", text: "CPU 88~98% · Pool 10/10" }),
          performance: [{ metric: "p95(Stress 최고 단계)", before: "13.14s" }, { metric: "CPU(최고 단계)", before: "88~98%" },
            { metric: "HikariCP pending(최고 단계)", before: "~190건" }, { metric: "dropped_iterations", before: "61,851건(78.2/s)" }],
          logs: "요청이 에러 없이 쌓여 점점 느려지는 saturation 패턴(#142)", evidenceReferences: [evidence.peak] }),
      step("split-detail", "K6 restaurant-view-hotpath", "GET /api/restaurants/{id}", "● 식당 상세 조회", "둘 중 어디가 느린가? — 식당 상세는 이미 단일 쿼리라 병목이 아니다.",
        { factStatus: FACT.MEASURED, visual: visual(["client", "web", "app", "db"], ["request", "request-app", "persist"], "request", "committed", "core", [], { nodeId: "db", text: "p95 16.5ms · 오류율 0%" }),
          performance: [{ metric: "p95(단독 Load 20 iter/s)", before: "16.5ms" }],
          codeReferences: ["RestaurantService.getRestaurantDetail"], evidenceReferences: [evidence.hotpath] }),
      step("split-sessions", "K6 restaurant-view-hotpath", "GET .../dining-sessions", "● 회차 조회", "회차 조회(dining-sessions) 혼자서도 HikariCP를 100% 채운다 — 주 병목으로 확인됐다.",
        { factStatus: FACT.MEASURED, visual: visual(["client", "web", "app", "db"], ["request", "request-app", "persist"], "event", "failure", "core", [], { nodeId: "db", text: "p95 1.15~4.03s · Pool 10/10" }),
          performance: [{ metric: "p95(단독 Load 20 iter/s)", before: "1.15s~4.03s" }, { metric: "HikariCP active", before: "10/10(100%)" }],
          codeReferences: ["TimeSlotService.getAvailableDiningSessions"], evidenceReferences: [evidence.hotpath] }),
      step("root-cause", "TimeSlotService", "회차별 반복 쿼리", "× 회차마다 4개 쿼리 반복", "회차(TimeSlot)마다 활성 예약·참여자 합계·CLOSED 여부·READY 선점 합계 4개 쿼리를 반복 실행했다(3 + N×4).",
        { factStatus: FACT.MEASURED, visual: visual(["app", "db"], ["persist"], "retry", "failure", "core", [], { nodeId: "db", text: "83 SQL(TimeSlot 20건)" }),
          performance: [{ metric: "SQL 실행 수(TimeSlot 20건)", before: "83개" }],
          codeReferences: ["TimeSlotService.loadAvailableDiningSessionBatchContext(개선 전 구조)"], evidenceReferences: [evidence.hotpath] }),
      step("batch-fix", "TimeSlotService", "배치 쿼리(GROUP BY / IN)", "✓ 4종 쿼리를 배치로 전환", "회차 ID를 이미 다 알고 있으므로 4개 쿼리를 회차 수와 무관한 고정 배치 쿼리로 바꿨다.",
        { factStatus: FACT.MEASURED, visual: visual(["app", "db"], ["persist"], "commit", "completed", "core", [], { nodeId: "db", text: "7 SQL(고정)" }),
          performance: [{ metric: "SQL 실행 수(TimeSlot 20건)", before: "83개", after: "7개", improvement: "고정값, 회차 수와 무관" }],
          codeReferences: ["ReservationRepository.findAllByTimeSlotIdInAndReservationStatusIn",
            "ReservationParticipantRepository.sumPartySizeByReservationIdsAndStatuses",
            "PaymentRepository.sumPartySizeByTimeSlotIdsAndStatusAndExpiresAtAfter",
            "PaymentHoldReader.sumActiveReadyPartySizeByTimeSlotIds"],
          evidenceReferences: [evidence.hotpath] }),
      step("same-load-result", "K6 Load(20 iter/s)", "동일 조건 재측정", "✓ 동일 부하 재측정", "동일 부하(Load 20 iter/s, 워밍업 후)에서 지연·CPU·DB Pool 세 지표 모두 뚜렷이 개선됐다.",
        { factStatus: FACT.MEASURED, visual: visual(["client", "web", "app", "db"], ["request", "request-app", "persist"], "commit", "completed", "core", [], { nodeId: "db", text: "p95 60.27ms" }),
          performance: [{ metric: "p95 응답시간", before: "802.66ms", after: "60.27ms", improvement: "92.5% 개선" },
            { metric: "p99 응답시간", before: "1.706s", after: "265.54ms", improvement: "84.4% 개선" },
            { metric: "CPU(최대/평균)", before: "91.7% / 70.0%", after: "21.2% / 11.6%" },
            { metric: "HikariCP active(scrape 최대)", before: "10(=풀 100%)", after: "0" }],
          logs: "이 Load 구간·scrape 간격에서는 포화가 관측되지 않음(완전 해소 아님, 아래 한계 참고)",
          evidenceReferences: [evidence.hotpath] }),
      step("stress-result", "K6 peak-restaurant-view.js(#142 원본)", "동일 Stress 스크립트 재실행", "✓ Stress 동일 조건 재측정", "#142와 동일한 Stress 스크립트로 재측정하면 처리량이 3.8배 늘고 dropped_iterations가 90.5% 줄어든다.",
        { factStatus: FACT.MEASURED, visual: visual(["client", "web", "app", "db"], ["request", "request-app", "persist"], "commit", "completed", "core", [], { nodeId: "db", text: "RPS 195.3(3.8x)" }),
          performance: [{ metric: "p95 응답시간(Stress 최고단계)", before: "13.14s", after: "1.34s", improvement: "89.8% 개선" },
            { metric: "HTTP RPS", before: "51.4 req/s", after: "195.3 req/s", improvement: "3.8배 증가" },
            { metric: "dropped_iterations", before: "61,851건(78.2/s)", after: "5,886건(7.5/s)", improvement: "90.5% 감소" }],
          evidenceReferences: [evidence.hotpath, evidence.peak] }),
      step("limits", "Human 판단", "포화 임계점 재평가", "▲ 임계점 이동, 완전 제거 아님", "병목이 사라진 게 아니라 임계점이 약 40 iter/s에서 약 320 iter/s로 8배 밀렸을 뿐이다 — 최고 부하에서는 CPU·Pool이 다시 포화된다.",
        { factStatus: FACT.MEASURED, visual: visual(["app", "db"], ["persist"], "failure", "failure", "core", [], { nodeId: "db", text: "40 → 320 iter/s" }),
          performance: [{ metric: "포화 시작 임계점(iter/s)", before: "~40", after: "~320", improvement: "8배 상승(완전 제거 아님)" }],
          limits: "Stress 최고 단계(320 iter/s)에서는 CPU 96~98%, HikariCP active 10/10, pending ~190건이 다시 관측된다(#235 4절). 320 iter/s를 넘는 부하는 측정하지 않았다. Pool 크기를 10→30으로 늘려도(#142 재검증) CPU가 부족한 상태에서는 개선되지 않고 오히려 악화됐다.",
          sideNote: { title: "다른 성능 의사결정 — #62 검색 Redis Cache",
            body: "회차 조회와는 별도로, 동일 검색 반복 시 DB Connection Pool 포화가 실측됐다(No Cache 동시 30×5: p50 32ms/p95 43ms/max 56ms, Pool active 10/10·awaiting 20, 요청당 쿼리 2). Warm Cache Hit은 p50 10ms/p95 14ms/max 19ms, Pool active/awaiting 0/0, 쿼리 0으로 DB를 완전히 우회했다. 캐시는 기술 스택을 늘리려고 넣은 게 아니라 반복 조회의 DB Connection 병목을 실측한 뒤 제한적으로(date/time 없는 검색만, TTL 60초) 적용했다. Redis Cache ≠ 예약 정합성 — 예약 성공 여부는 항상 DB가 최종 판단한다." },
          evidenceReferences: [evidence.hotpath, evidence.peak, evidence.searchCache] })
    ]}
  ]}
];
