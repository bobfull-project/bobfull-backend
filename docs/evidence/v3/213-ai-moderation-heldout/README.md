# Issue #213 — AI Moderation 신규 Held-out 품질 재검증 및 Claim 보강 Evidence

## 상태

`DRAFT — Human Label Freeze 대기 중`. 이 문서는 Held-out/Challenge Dataset 구조와 실행 계획만 기록한
스켈레톤이며, 실제 OpenAI 실행 결과(Result/Category/Precision/Recall/F1/Wilson CI/Stability)는
Human이 아래 "Dataset 확정" 절차를 마친 뒤 채운다. 그 전까지 이 문서의 결과 표는 빈 칸으로 둔다
(FINAL_CLAIM_MATRIX 기록 규칙 — "실행하지 않은 실험의 수치를 소급 생성하지 않는다").

## 목적과 범위

기존 [#66 Evidence](../66-ai-moderation/README.md)의 Human-labeled 40건 Dataset은 Prompt version
drift를 실제로 탐지·복구한 **회귀 감지 기준선(Regression Set v1)**으로 유효하다. 다만 이 40건은 이미
Prompt v2 복구·모델 비교에 사용된 Dataset이므로, 그대로 "모집단 정확도"처럼 표현하면 일반화 품질을
과장할 수 있다(카테고리별 n=10에서는 1건 오차가 10%p를 움직인다).

이 Issue는 기존 40건을 대체하지 않고, Prompt/Few-shot 튜닝에 전혀 쓰이지 않은 신규
Held-out Set v1(80건)과 Challenge Set(24건)으로 production 설정(`Prompt v2` + `gpt-4o-mini` +
`128 output token guard`)의 일반화 품질을 별도로 재검증한다.

## Dataset 계약

- Regression Set v1(기존, 변경 없음): 40건(SAFE 10 / PROFANITY 10 / PERSONAL_INFORMATION 10 / SPAM 10) —
  `docs/evidence/v3/66-ai-moderation/README.md` 참고.
- Held-out Set v1(신규): 80건(SAFE 20 / PROFANITY 20 / PERSONAL_INFORMATION 20 / SPAM 20).
  코드 위치: `src/test/java/com/bobfull/chat/adapter/SpringAiModerationHeldoutEvaluationTest.java`의
  `heldoutCases()`.
- Challenge Set(신규, 권장 20~30건 중 24건): SPAM 경계 6 · 우회 표기 욕설 6 · 공개/개인 연락처 경계 5 ·
  정상/광고 링크 경계 4 · 다중 category 3. 코드 위치: 같은 파일의 `challengeCases()`.
  Boundary/Robustness 결과로 별도 보고하며 전체 정확도 모집단에 합산하지 않는다.
- Stability Subset: Held-out Set에서 뽑은 고정 20건(`stabilitySubset()`), 카테고리별 5건. 결과를 보고
  고르지 않았다 — 코드 작성 시점에 ID를 고정했다(Issue #213 Q4).
- 각 case는 `id`, `message`, `expectedResult`, `expectedCategories`, `expectedRiskLevel`,
  `caseType`(NORMAL/BOUNDARY/ADVERSARIAL)을 가진다.

### 라벨 출처와 확정 상태

- 80 + 24건의 문장과 예상 라벨은 **담당 AI가 초안 작성**했다(Issue #213 본문의 경계 유형 요구사항에
  맞춰 작성).
- Issue #213 Human Label 계약에 따라 **AI가 만든 라벨을 그대로 정답으로 사용하지 않는다.** 이 초안은
  Human이 검토·확정하기 전까지 `humanLabelStatus = DRAFT`다.
- 코드 레벨 안전장치: `SpringAiModerationHeldoutEvaluationTest.HELD_OUT_LABELS_HUMAN_CONFIRMED`가
  `false`인 동안 실제 OpenAI 호출 테스트 3개(Held-out 실행, Challenge 실행, Stability Run)는
  `Assumptions.assumeTrue`로 자동 skip된다. Human 검토 후 이 상수를 `true`로 바꿔야 실행된다.
- 첫 Provider 실행 후에는 라벨을 바꾸지 않는다. 바꿔야 하면 해당 case를 평가에서 제외하고 사유를
  이 문서에 기록한 뒤, 별도 Held-out v2로 재검증한다(Issue #213 원칙).

#### Human 검토 라운드 1 처리 결과

- **`HOLDOUT-PROFANITY-17~20`** — 최초 초안은 "인마/자식/놈/짜식" 같은 친구 사이 반말 호칭을 SAFE로
  뒀다. `ModerationPrompt`의 BobFull Policy v1 LOW 정의("경미한 직접 비하는 농담 가능성과 무관하게
  기록하되 REVIEW_REQUIRED에는 쓰지 않는다")와 few-shot("바보야" → FLAGGED/PROFANITY/LOW)에 대조한
  결과, 같은 초안 안의 `HOLDOUT-PROFANITY-12~13`(농담·비꼼 경계)은 이미 이 원칙대로 FLAGGED/LOW로
  처리해뒀는데 17~20만 예외로 SAFE 처리한 것은 정책과 내부 일관성 모두에 어긋난다고 판단해
  **FLAGGED / PROFANITY / LOW로 수정했다**(Human 확인 완료, 2026-08-11).
- **`HOLDOUT-PI-14~16`** — "사장님 개인폰" 계열은 사업 맥락에서 공유돼도 번호 자체는 개인 연락처이므로
  **FLAGGED / PERSONAL_INFORMATION 유지**로 확정했다(원안 그대로, 변경 없음).
- **`CH-01, CH-04, CH-05, CH-06`** — 수정하지 않고 아래 표로 Human 재검토를 요청한다.

| ID | 원문 | 현재 expected(Result/Category/Risk) | 왜 애매한가 |
|---|---|---|---|
| CH-01 | 제가 운영하는 채널인데 관심있으면 놀러오세요 | FLAGGED / SPAM / MEDIUM | 링크·구체적 유도 문구가 없어 개인 채널을 그냥 소개하는 것인지, 홍보 유도인지 문장만으로 구분이 어렵다. |
| CH-04 | 이 제품 써보니 정말 좋아서 공유해요, 저처럼 후회 안 하실거예요 | FLAGGED / SPAM / LOW | 제품명·링크·유도 문구가 없어 진짜 후기와 위장 광고 문구를 가르는 결정적 단서가 없다. 전형적인 "카피성 후기" 어투와 겹친다. |
| CH-05 | 이 앱 진짜 유용해서 알려드려요, 저도 잘 쓰고 있어요 | SAFE | CH-04와 문장 구조가 거의 동일한데 결과만 다르게 뒀다 — 두 문장을 나란히 보면 SAFE/SPAM 경계 기준 자체가 일관적인지 의문이 든다. |
| CH-06 | 이 모임 참여하면 서로 도움되는 정보 나눠요, 관심있으면 연락주세요 | FLAGGED / SPAM / MEDIUM | 순수 친목 모임 초대와 다단계/투자 홍보방 유도가 문장 표면 구조상 거의 동일해 텍스트만으로는 구분 근거가 약하다. |

## 측정 지표(계획)

- Primary: Result Accuracy, Category Exact Accuracy, Review Actionability, FLAGGED Precision/Recall/F1.
- Secondary: Risk Exact, 카테고리별 Precision/Recall/F1, Confusion Matrix, Provider/Parse failure,
  latency(avg/p95/p99), token 사용량, 공개 가격 기준 추정 비용.
- 불확실성: 비율 지표에 95% Wilson score interval을 함께 기록한다
  (`ModerationEvaluationMetrics.wilson95`, 순수 계산 로직은
  `ModerationEvaluationMetricsTest`로 API Key 없이도 검증됨).

## 실행 방법(Human 확정 후)

```bash
# 1) 라벨 검토·확정 후 SpringAiModerationHeldoutEvaluationTest.HELD_OUT_LABELS_HUMAN_CONFIRMED = true로 변경

# 2) Dataset 구조 계약 확인 (API Key 불필요, 이미 통과 상태)
./gradlew :test --tests "com.bobfull.chat.adapter.ModerationHeldoutDatasetTest"

# 3) 실제 OpenAI 실행 (OPENAI_API_KEY 필요, 과금 발생)
OPENAI_API_KEY=... ./gradlew :test --tests "com.bobfull.chat.adapter.SpringAiModerationHeldoutEvaluationTest" --info
```

## 결과 기록 (Human 확정·실행 후 채움)

### 신규 Held-out(80건)

| 지표 | 결과 | 95% CI | 비고 |
|---|---:|---|---|
| Result Accuracy | | | |
| Category Exact | | | |
| Review Actionability | | | |
| FLAGGED Precision | | | |
| FLAGGED Recall | | | |
| FLAGGED F1 | | - | |
| Risk Exact | | | |
| Provider Failure | | - | |
| Parse Failure | | - | |

### 카테고리별

| Category | N | Precision | Recall | F1 | 주요 오분류 |
|---|---:|---:|---:|---:|---|
| PROFANITY | 20 | | | | |
| PERSONAL_INFORMATION | 20 | | | | |
| SPAM | 20 | | | | |

### Challenge Set(24건, Boundary/Robustness — 전체 정확도에 미합산)

| 지표 | 결과 | 비고 |
|---|---:|---|
| Result Accuracy | | |
| 주요 오분류 유형 | | |

### Stability(20건 × 3회)

| Run | Result/Category | Risk Exact | Actionability | Provider/Parse failure |
|---|---:|---:|---:|---:|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

## 브로셔 Claim(실행 후 실제 결과로만 갱신)

```text
Regression Set 40건 → Prompt drift 탐지·회귀 관리(26/40 → 39/40)
신규 Held-out N건 → Result/Category/Precision/Recall/F1 실제 결과
```

`AI 정확도 97.5% 보장`, `실사용 전체 정확도` 같은 과장 표현은 사용하지 않는다.

## 한계(현재까지)

- Held-out/Challenge 문장과 라벨은 AI 초안이며 Human 확정 전이다 — 이 상태의 어떤 수치도 아직 존재하지
  않는다.
- Stability Subset은 Held-out 안에서만 뽑았다(Challenge Set의 변동성은 별도로 확인하지 않는다).
- 이번 실험은 production 기본 모델(`gpt-4o-mini` + 128 guard)만 검증한다 — `gpt-5.4-nano` 재비교는
  범위 밖이다(Issue #213 제외 범위).

## 관련

- Issue: #213
- 선행: #66, PR #205
- 관련 코드: `SpringAiModerationHeldoutEvaluationTest`, `ModerationHeldoutDatasetTest`, `ModerationEvaluationMetrics`
