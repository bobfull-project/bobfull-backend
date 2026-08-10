# Issue #66 AI 채팅 Moderation Evidence

## 검증 대상

`ChatMessage` 원문을 변경하지 않고, OpenAI Structured Output 결과를 검증해 `messageId` 기준으로 저장한다.
Kafka Consumer와 Retry/DLT는 Issue #59 범위이므로 이 Evidence에서 구현·측정하지 않는다.

## 측정 계약

- Primary KPI: 고정 Human-labeled 40건의 Review Actionability 정확도(LOW 제외, MEDIUM/HIGH 포함)
- Secondary KPI: OpenAI 호출 latency(avg/p95/p99), token usage, 오류 유형, messageId당 외부 호출 수
- Guardrail: AI 실패가 ChatMessage 저장·전달을 변경하지 않고, 완료된 messageId의 재호출과 결과 중복 생성이 없어야 한다.

## 기준 코드

- Before SHA: `2a01065146597fbc68d51dfb0f048807d95e31f4`
- After SHA: `7e710c129fb4588706619c2a85825708c889ff72`
- Prompt drift 복구 SHA: `375af8f`

## Prompt·Policy·Provider

- provider: OpenAI
- model: `spring.ai.openai.chat.model` ← `OPENAI_CHAT_MODEL` 환경변수, 기본값 `gpt-4o-mini`
- local/prod API Key: `OPENAI_API_KEY` 환경변수. 실제 키는 `application-local.yml`·배포 설정·저장소에 저장하지 않는다.
- promptVersion: `moderation-prompt-v2`
- policyVersion: `moderation-policy-v1`
- Prompt source: `ModerationPrompt`가 `PROMPT_VERSION`, `POLICY_VERSION`, `SYSTEM_PROMPT`를 함께 관리한다. Adapter와 저장 Service는 이 버전 상수를 참조한다.
- Structured Output: Provider Native Structured Output. Spring AI schema self-correction retry는 사용하지 않는다.
- Provider retry: `spring.ai.retry.max-attempts=1`. 전체 재시도는 #59 Kafka Consumer가 소유한다.

## 사전 sandbox validation

아래 값은 BobFull 통합 결과가 아니라 별도 sandbox에서 동일 Human-labeled 40건으로 수행한 사전 검증이다.

| 지표 | Prompt v1 | Prompt v2 |
|---|---:|---:|
| Result Accuracy | 40/40 | 40/40 |
| Category Accuracy | 40/40 | 40/40 |
| Risk Accuracy | 36/40 | 35/40 |
| Exact Match | 36/40 | 35/40 |
| Review Actionability | 37/40 | 39/40 |

Prompt v2는 Risk Exact Match를 위해 v3로 과적합하지 않고 현재 기준선으로 고정한다.

## BobFull 40건 Dataset 동기화

- Source of Truth: `demo2/src/test/java/com/example/demo/ModerationEvaluationTest.java`의 `testCases()`
- 추출 항목: case id, message content, expected result/categories/riskLevel
- BobFull 위치: `SpringAiModerationAdapterOpenAiEvaluationTest`
- 계약 검증: 별도 `SpringAiModerationEvaluationDatasetTest`가 총 40건과 SAFE/PROFANITY/PI/SPAM 각 10건을 확인한다.
- Prompt v2와 Human expected는 demo2 원본에서 변경하지 않았다.

## BobFull 통합 후 결과

| 지표·현상 | 결과 | 판정 |
|---|---:|---|
| 실제 OpenAI 단건 호출 | local IntelliJ 환경에서 Context 기동·Provider 호출·Structured Output 변환·SAFE 기대값 확인 | PASS (Human 실행 결과) |
| 40건 고정 Dataset 재측정 (Prompt version drift 발견 전 BobFull 실측) | Result 40/40, Category 40/40, Risk 17/40, Exact 17/40, Review Actionability 26/40 | BEFORE |
| drift 발견 전 Provider 관측 | Provider Failure 0, OpenAI Calls 40, latency avg 967.6ms / p95 1239ms / p99 3559ms, total token 14,256, total elapsed 38,723ms | BEFORE (Human 실행 결과) |
| 40건 고정 Dataset 재측정 (Prompt 복구 후) | Result 40/40, Category 40/40, Risk 35/40, Exact 35/40, Review Actionability 39/40 | AFTER (Human 실행 결과) |
| drift 복구 후 Provider 관측 | OpenAI Calls 40, latency avg 869.6ms / p95 1292ms / p99 1569ms, prompt/completion/total token 30,897 / 687 / 31,584, total elapsed 34,804ms | AFTER (Human 실행 결과) |
| Core 정상·검증실패·멱등성·AI 장애 격리 | `./gradlew :test --tests com.bobfull.chat.service.ChatModerationServiceTest` 성공 | PASS |
| 전체 build 및 테스트 | `./gradlew test` 성공 | PASS |

실제 단건 검증은 `OPENAI_API_KEY`가 있는 local 환경에서 다음처럼 별도로 실행한다. 이 테스트는 키가 없는 일반 build에서는 JUnit 조건으로 건너뛴다.

```bash
./gradlew :test --tests com.bobfull.chat.adapter.SpringAiModerationAdapterOpenAiEvaluationTest
```

Prompt version drift 발견 전 실측의 Risk FAIL은 PROFANITY MEDIUM→LOW 4건, PROFANITY HIGH→MEDIUM 1건, PERSONAL_INFORMATION MEDIUM→LOW 10건, SPAM HIGH→MEDIUM 8건이다. Review Actionability 실패 14건은 PROFANITY MEDIUM→LOW 4건과 PERSONAL_INFORMATION MEDIUM→LOW 10건에 정확히 대응한다. Prompt 복구 후에는 먼저 `Prompt_v2_대표_회귀_6건을_실제_OpenAI로_검증한다`로 경계 5건과 SAFE 1건을 확인하고, 그 다음에만 동일 40건을 실행한다.

Prompt 복구 후 FAIL은 `PROFANITY-07`의 MEDIUM→LOW 1건과 `SPAM-02`, `SPAM-05`, `SPAM-07`, `SPAM-09`의 HIGH→MEDIUM 4건이다. PROFANITY-07만 Review Actionability에 영향을 준다. SPAM 4건은 Exact Risk와 다르지만 예상·실제 모두 MEDIUM/HIGH이므로 REVIEW_REQUIRED 운영 행동은 동일하다. After Risk/Exact 35/40 및 Review Actionability 39/40은 sandbox Prompt v2 기준선과 동일하다. 따라서 이 비교는 Prompt version drift가 BobFull 품질 붕괴의 원인이었음을 뒷받침한다.

상세 boundary/few-shot 복구로 total token은 14,256에서 31,584로 증가했다. 반면 Review Actionability는 65.0%에서 97.5%로 회복됐다. latency와 총 경과 시간은 외부 OpenAI 실행 편차가 있으므로 Before 대비 성능 개선으로 주장하지 않고, 각 실행의 실제 관측값으로만 기록한다.

| After 40건 카테고리 | Result/Category | Risk/Exact | Review Actionability |
|---|---:|---:|---:|
| SAFE (10) | 10/10 | 10/10 | 10/10 |
| PROFANITY (10) | 10/10 | 9/10 | 9/10 |
| PERSONAL_INFORMATION (10) | 10/10 | 10/10 | 10/10 |
| SPAM (10) | 10/10 | 6/10 | 10/10 |

After의 Result/Category 기준 오탐·미탐은 각각 0건이다. token은 호출당 평균 prompt 772.4, completion 17.2, total 789.6이며, 이는 40회 순차 실행의 관측값이다.

40건 평가는 같은 클래스의 `Prompt_v2를_동일한_40건_Human_labeled_Dataset으로_측정한다` 테스트가 순차 호출로 수행한다. Result/Category/Risk/Exact/Review Actionability 정확도, FAIL case ID와 expected/actual, 요청별 latency의 avg/p95/p99, token 합계와 총 호출 수를 출력한다. 40건 누적 실행 시간은 참고값으로만 출력한다.

Evaluation Test는 H2와 JWT/PortOne/Mail 테스트값만 사용하고, payment·reservation scheduler 및 chat-room/email outbox background job을 모두 비활성화한다. 외부 의존은 OpenAI만 남긴다.

## 정합성·장애 격리

- `chat_moderation.chat_message_id`는 UNIQUE다.
- SAFE/FLAGGED 완료 결과가 있으면 AI를 재호출하지 않는다.
- 외부 AI 호출은 ChatMessage 조회·결과 저장의 짧은 DB 작업 사이에서 수행한다.
- Provider/구조 검증 실패는 저장 없이 예외를 전파한다. #59 Consumer가 Retry/DLT를 소유하며, Retry 소진 뒤에만 `recordFinalFailure`로 `ANALYSIS_FAILED`를 기록한다.
- ChatMessage.content를 수정·가림·삭제하지 않는다.

## 로그·메트릭

구조화 로그는 `messageId`, result/categories/riskLevel, latency, errorCode만 남긴다. ChatMessage 원문, Prompt, Completion, 개인정보, API Key는 로그·메트릭에 기록하지 않는다. messageId는 Micrometer label로 사용하지 않는다.

## Known Limitation

- sandbox v2에서 `이런 젠장`은 Human MEDIUM 기대와 달리 LOW였다. SPAM 4건은 HIGH/MEDIUM 차이지만 모두 REVIEW_REQUIRED 산정 대상이다.
- demo2가 Prompt v2를 `BobFull Moderation Policy v2`로 표기한 것은 promptVersion과 policyVersion을 혼용한 과거 sandbox 명명 오류다. BobFull은 Prompt 원문 경계·few-shot·출력 규칙을 복구하되, 공식 계약인 `moderation-prompt-v2`와 `moderation-policy-v1`을 유지한다.
- Prompt v2는 이 Dataset에 대해 추가 튜닝하거나 `moderation-prompt-v3`를 만들지 않는다. 현재 After 결과를 Prompt v2 기준선으로 동결한다.
- #59가 아직 `status:draft`이므로 Kafka Consumer/Retry/DLT 연결은 구현하지 않았다.

## 관련

- Issue: #66
- 선행/연결: #59
