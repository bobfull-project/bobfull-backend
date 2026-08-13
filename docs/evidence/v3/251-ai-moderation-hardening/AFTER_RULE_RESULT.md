# Issue #251 Frozen Dataset v1 — AFTER Production CLEAR_FLAGGED Rule

## 실행 계약

- 실행: 2026-08-13 20:38~20:39 KST, 실제 OpenAI Provider 1회 순차 Run
- baseline commit SHA: `32059bcae3642717e952e424ca4d4bea3f1a9953`
- After Implementation SHA: `131d3cf4c8c38bcc5eda259e401a43fb0189387e`
- PR #255 MAJOR boundary 수정 후 위 SHA 기준으로 2026-08-13 21:07~21:08 KST에 최종 1회 재측정했다.
- Dataset: `issue-251-hardening-v1` / SHA-256 `9caf442202e82faaedd333ee5eaf57b422b06b48669bc7c5c1418e7487afbaba`
- requested model: `gpt-4o-mini`; actual returned model: `gpt-4o-mini-2024-07-18`
- promptVersion / policyVersion: `moderation-prompt-v3-scope` / `moderation-policy-v2`
- production 경로: `ChatModerationService → ModerationRuleFilter → Validator → ChatModeration`
- Context 없음, CLEAR_SAFE 없음, Frozen Dataset/expected 변경 없음.

## Routing / Token

| Metric | BEFORE | AFTER | 변화 |
|---|---:|---:|---:|
| Frozen Case | 66 | 66 | - |
| message traversal | 88 | 88 | - |
| CLEAR_FLAGGED | 0 | 17 | - |
| LLM_REQUIRED | 88 traversals | 71 traversals | -17 |
| 실제 OpenAI calls | 88 | 71 | -19.3% |
| Prompt Tokens | 65,395 | 52,742 | -19.3% |
| Completion Tokens | 1,371 | 1,059 | -22.8% |
| Total Tokens | 66,766 | 53,801 | -19.4% |

단건 workload 기준 routing 후보는 `52 → 35`(32.7%)이고, 위 표는 split fragment를 포함한 실제 Frozen traversal
측정값이다. 비용은 측정 시점의 공식 단가 Source를 이 Evidence에 고정하지 않았으므로 `COST = NOT_CALCULATED`다.

## Quality

| Metric | BEFORE | AFTER |
|---|---:|---:|
| Result Accuracy | 62/66 | 62/66 |
| Category Exact | 61/66 | 62/66 |
| Risk Exact | 61/66 | 61/66 |
| Precision / Recall / F1 | .923 / .973 / .947 | .923 / .973 / .947 |
| FP / FN | 3 / 1 | 3 / 1 |
| Injection Security | 9/10 | 10/10 |
| Obfuscation Detection | 12/12 | 12/12 |
| Split Detection | 5/6 | 5/6 |
| Split FP / FN | 2 / 1 | 2 / 1 |

## Rule Fast Path Evidence

- Fast Path: 17
- Fast Path Precision: 17/17 = 1.000
- Fast Path False Positive: 0
- Human SAFE Fast Path: 0
- OpenAI calls on Fast Path: 0
- Rule result category/risk mismatch: 0

각 17건은 `BOBFULL_RULE / rule-filter-v1 / NO_LLM / moderation-policy-v2 / token=null` metadata로 저장됐고,
기존 `ModerationResultValidator`를 거쳤다.

## INJ-06

| 항목 | AFTER actual |
|---|---|
| route | LLM_REQUIRED |
| Provider raw output | `FLAGGED / [PROFANITY] / MEDIUM` |
| Application Validation | PASS |
| 저장 | `FLAGGED / [PROFANITY] / MEDIUM` |

Provider failure 0, Structured Output failure 0, Application Validation failure 0이다. BEFORE의 빈 category 응답은
과거 관측으로 유지하며, AFTER raw output의 변화는 LLM_REQUIRED Provider variation 후보로 분리한다.

## Latency

- 전체 traversal avg / p50 / p95: 731.3ms / 809ms / 1,362ms
- Rule Fast Path avg: 0.0ms (`ChatModeration.latencyMillis` 밀리초 단위 반올림; 외부 Provider 호출 없음)
- LLM path avg: 906.4ms

외부 Provider latency 변동을 성능 개선으로 과장하지 않는다. Rule Fast Path의 구조적 이점은 외부 호출 0회이며,
전체 workload latency는 해당 단일 측정값이다.

## Attribution / Gate 후보

- Rule attributable regression: 없음. Fast Path 17/17 정확, Fast Path FP 0, 신규 category/result 오류 0.
- LLM_REQUIRED Provider variation: Injection Security 9/10 → 10/10, Category Exact 61/66 → 62/66. Prompt/model/provider/validator는
  LLM_REQUIRED에서 변경하지 않았으므로 Rule 효과로 단정하지 않는다.
- `RULE_QUALITY_GATE = PASS`
- `ROUTING_BENEFIT = MEASURED` (actual calls 및 total tokens 감소)
- `OVERALL_QUALITY = NO_RULE_ATTRIBUTABLE_REGRESSION_OBSERVED`

## 검증 명령

```bash
ISSUE251_AFTER=true ./gradlew :test \
  --tests 'com.bobfull.chat.adapter.Issue251ProductionRuleProviderAfterTest' \
  --rerun-tasks -PshowTestOutput
```

결과: `BUILD SUCCESSFUL`

최종 SHA 기준 일반 build는 Provider opt-in 환경 변수와 `OPENAI_API_KEY`를 제거한 다음 명령으로도 성공했다.

```bash
env -u OPENAI_API_KEY -u ISSUE251_PROVIDER -u ISSUE251_CONTEXT_V2_SPIKE -u ISSUE251_AFTER \
  ./gradlew clean build --console=plain
```

결과: `BUILD SUCCESSFUL` (Provider 평가 미실행)
