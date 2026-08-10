# ADR 0009: AI Moderation Provider 및 모델 선택

- 상태: `Accepted`
- 작성일: `2026-08-10`
- 관련 Issue·PR: #66, PR #205

## 배경

BobFull 채팅은 PROFANITY, PERSONAL_INFORMATION, SPAM과 LOW/MEDIUM/HIGH 위험도를 자체 정책으로 분류한다. LOW는 자동 통과, MEDIUM/HIGH는 관리자 검토 대상으로 연결되므로 Structured Output과 Application Validation이 필요하다. 외부 AI 분석은 채팅 저장·전달과 분리되며, 비용·latency·품질을 함께 판단해야 한다.

## 고려한 대안

1. Regex/rule-only: 설명 가능성은 높지만 현재 BobFull taxonomy와 문맥·경계 분류를 단독으로 충족하기 어렵다.
2. OpenAI moderation 전용 API: 자체 taxonomy/risk 및 `ModerationResult` Structured Output 계약에 직접 맞추기 어렵다.
3. 범용 Chat Model + BobFull Prompt + Structured Output: 자체 정책과 Application Validation을 유지할 수 있다.

Provider로 OpenAI와 다른 상용 LLM Provider를 고려할 수 있다. 이번 범위는 Provider 벤치마크가 아니라 BobFull 정책·Structured Output·검증 경계를 확인하는 것이므로 Claude/Gemini 비교를 새로 수행하지 않았다. 다른 Provider가 기술적으로 불가능하다는 판단은 아니다.

## 결정

OpenAI를 단일 Provider로, production 기본 모델은 `gpt-4o-mini`로 유지한다. `AiModerationPort` 뒤의 Adapter 분리는 유지해 비용·품질·Rate Limit·장애 대응 필요가 생기면 Provider Adapter를 교체할 수 있게 한다. 멀티 Provider는 구현하지 않았다.

## 선택 이유

동일 Prompt v2와 Human-labeled 40건 Dataset에서 gpt-4o-mini와 gpt-5.4-nano는 Result/Category 100%, Review Actionability 95% 이상이라는 Primary Gate를 모두 통과했다. gpt-5.4-nano의 Actionability는 1건 높았으나 Risk/Exact는 1건 낮았고, 단일 외부 LLM run의 차이를 결정적 품질 우위로 해석하지 않았다.

이번 실측에서 gpt-4o-mini는 더 낮은 공개 가격 기반 추정 비용과 더 낮게 관측된 avg/p95/p99 latency를 보였다. gpt-4o-mini의 `maxTokens(128)` production option 계약도 이미 실제 Structured Output으로 검증됐다. 상세 수치·한계는 [#66 Evidence](../evidence/v3/66-ai-moderation/README.md)를 기준으로 한다.

`gpt-5-nano`는 Stage A에서 `max_tokens`를 거부하고 `max_completion_tokens`를 요구했다. 품질 탈락이 아니라 현재 option 계약과의 compatibility 확인 결과이며, 이번 결정에 맞춰 production option 분기를 추가하지 않는다.

## 장점

- 현재 요구사항에서 Primary Gate를 충족한다.
- 이번 실측 token 기준 추정 비용이 낮다.
- 이번 BobFull 평가 환경에서 latency가 낮게 관측됐다.
- 검증된 Structured Output 및 단순한 production option 계약을 유지한다.

## 단점과 위험

- 실제 관측 snapshot은 오래된 `gpt-4o-mini-2024-07-18`이다.
- 최신 모델의 향후 품질 개선을 자동으로 얻지 못한다.
- 가격·모델 behavior·rate limit은 변할 수 있고, 단일 40-call run으로 일반 성능을 보장하지 않는다.

## 검증 방법

- Prompt/version regression, Dataset 40/10/10/10 invariant, Application Validation, 전체 build/test
- opt-in 실제 Provider RAW→DTO, 대표 6건, 동일 40건 품질·token·latency 평가
- 비용은 확인일 공개 가격 × 실측 token으로만 추정

## 재검토 조건

- 품질 KPI 하락 또는 신규 held-out Dataset에서의 유의한 차이
- OpenAI 가격·Rate Limit·장애 조건 변화
- #59 Kafka Consumer throughput/E2E 병목
- 멀티모달 Moderation 요구
