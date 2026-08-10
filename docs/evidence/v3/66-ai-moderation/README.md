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

## Prompt·Policy·Provider

- provider: OpenAI
- model: `spring.ai.openai.chat.model` ← `OPENAI_CHAT_MODEL` 환경변수, 기본값 `gpt-4o-mini`
- local/prod API Key: `OPENAI_API_KEY` 환경변수. 실제 키는 `application-local.yml`·배포 설정·저장소에 저장하지 않는다.
- promptVersion: `moderation-prompt-v2`
- policyVersion: `moderation-policy-v1`
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

## BobFull 통합 후 결과

| 지표·현상 | 결과 | 판정 |
|---|---:|---|
| 실제 OpenAI 단건 호출 | `NOT_MEASURED` — 이 실행 환경에 `OPENAI_API_KEY` 없음 | NOT_RUN |
| 40건 고정 Dataset 재측정 | `NOT_MEASURED` — 실제 Provider 호출 미실행 | NOT_RUN |
| latency / token / 비용 | `NOT_MEASURED` — 실제 Provider 응답 없음 | NOT_RUN |
| Core 정상·검증실패·멱등성·AI 장애 격리 | `./gradlew :test --tests com.bobfull.chat.service.ChatModerationServiceTest` 성공 | PASS |
| 전체 build 및 테스트 | `./gradlew test` 성공 | PASS |

실제 단건 검증은 `OPENAI_API_KEY`가 있는 local 환경에서 다음처럼 별도로 실행한다. 이 테스트는 키가 없는 일반 build에서는 JUnit 조건으로 건너뛴다.

```bash
./gradlew :test --tests com.bobfull.chat.adapter.SpringAiModerationAdapterOpenAiEvaluationTest
```

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
- 실제 OpenAI 모델·Prompt 통합 재측정 및 운영 latency/token은 API Key가 있는 격리 환경에서 같은 40건 Dataset으로 별도 수행해야 한다.
- #59가 아직 `status:draft`이므로 Kafka Consumer/Retry/DLT 연결은 구현하지 않았다.

## 관련

- Issue: #66
- 선행/연결: #59
