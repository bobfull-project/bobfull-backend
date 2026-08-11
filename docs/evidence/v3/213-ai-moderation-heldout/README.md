# Issue #213 — AI Moderation 신규 Held-out 품질 재검증 및 Claim 보강 Evidence

## 상태

`FROZEN — Human Label Freeze 완료, Provider 실행 대기`. Held-out 80건 + Challenge 24건 라벨은
Human 최종 승인으로 동결됐다(아래 "Dataset Freeze" 절). 실제 OpenAI 실행 결과
(Result/Category/Precision/Recall/F1/Wilson CI/Stability/latency/token/cost)는 이 문서의
"결과 기록" 절에 실행 후 채운다. 그 전까지 결과 표는 빈 칸으로 둔다(FINAL_CLAIM_MATRIX 기록 규칙 —
"실행하지 않은 실험의 수치를 소급 생성하지 않는다").

## Dataset Freeze

- Held-out/Challenge 104건(80+24) 라벨은 Human 검토 3라운드(초안 → CH-01/04/05/06 확정 →
  104건 Consistency Sweep 5쌍 확정)를 거쳐 **2026-08-11 Human 최종 승인으로 동결**했다.
- `SpringAiModerationHeldoutEvaluationTest.HELD_OUT_LABELS_HUMAN_CONFIRMED = true`
- Dataset Content SHA-256(id/message/expectedResult/expectedCategories/expectedRiskLevel/caseType
  canonical 직렬화 기준, `ModerationHeldoutDatasetTest.datasetContentSha256()`로 재현 가능):
  `78a072fae2d208da79defeb9f7c260594f77c9e964f94d56e1615a55c840527e`
- Held-out Dataset 기준 Commit SHA(Label Freeze를 반영한 커밋): `PENDING_COMMIT_SHA`(직후 커밋에서 확정)
- 이 SHA 이후 Provider 실행 전까지 `SpringAiModerationHeldoutEvaluationTest`의 expected 라벨은
  변경하지 않는다. 뒤늦게 라벨 오류가 확인되면 코드를 고치지 않고 해당 case를 결과 집계에서 제외한
  뒤 사유를 이 문서에 기록한다(Issue #213 원칙).

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
- **(라운드 2 추가, 2026-08-11)** Human Label 확정 시 메시지에 드러나지 않은 숨은 의도를 추정하지
  않고, 문장에 명시적으로 드러난 근거를 우선한다(예: "제가 운영하는"처럼 자기 운영을 밝힌 표현,
  "사장님 개인폰"처럼 개인 소유를 명시한 표현). 이 기준으로도 본질적으로 정답이 불명확한 Challenge
  case는 평가셋에서 제외하거나 문장을 수정한다.

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

#### Human 검토 라운드 2 처리 결과 (최종 확정, 2026-08-11)

| ID | 원문 | 변경 전 | 변경 후(최종) |
|---|---|---|---|
| CH-01 | 제가 운영하는 채널인데 관심있으면 놀러오세요 | FLAGGED / SPAM / MEDIUM | **FLAGGED / SPAM / LOW** |
| CH-04 | 이 제품 써보니 정말 좋아서 공유해요, 저처럼 후회 안 하실거예요 | FLAGGED / SPAM / LOW | **SAFE** |
| CH-05 | 이 앱 진짜 유용해서 알려드려요, 저도 잘 쓰고 있어요 | SAFE | SAFE(변경 없음) |
| CH-06 | 이 모임 참여하면 서로 도움되는 정보 나눠요, 관심있으면 연락주세요 | FLAGGED / SPAM / MEDIUM | **SAFE** |

이 4건은 Human 최종 판단으로 확정했으며 추가 재검토 대상이 아니다.

### 104건 전체 라벨 Consistency Sweep (Human 검토 대기, 미반영)

라운드 2 규칙(명시적 근거 우선, 숨은 의도 추정 금지) 기준으로 80 + 24건 전체를 다시 훑어, 표면상
유사한 문장인데 Result/Category/Risk가 다르게 매겨진 쌍을 정리했다. **아직 어느 것도 코드에
반영하지 않았다** — Human 최종 확인 후에만 수정한다.

| 그룹 | 문장 A | A 라벨 | 문장 B | B 라벨 | 왜 나란히 봐야 하는가 |
|---|---|---|---|---|---|
| 자기 운영 홍보 | `HOLDOUT-SPAM-04` 제 쇼핑몰 오픈했어요 놀러오세요 | SPAM/MEDIUM | `CH-01` 제가 운영하는 채널인데 관심있으면 놀러오세요 | SPAM/LOW(라운드2 확정) | 둘 다 "내가 운영 + 놀러오라" 구조. "쇼핑몰"(명확한 거래 목적)과 "채널"(목적 불명)의 차이로 risk를 가른 것인지 확인 필요 |
| 친근한 비속어성 표현 | `HOLDOUT-SAFE-17` 저 사람 실력 진짜 또라이같이 잘한다 | SAFE | `HOLDOUT-PROFANITY-19` 너 진짜 웃긴 놈이다 ㅋㅋㅋ | PROFANITY/LOW | "또라이"가 "놈"보다 사전적 어감이 더 강한데, 사람을 직접 지칭하는 쪽(놈)만 FLAGGED로 처리됨 — "또라이같이"는 능력을 묘사하는 비유이고 "놈이다"는 사람을 직접 지칭한다는 차이로 설명 가능한지 확인 필요 |
| 비속어 강조 부사 | `CH-10` 존 나 짜증나게 하네 | PROFANITY/MEDIUM | `CH-12` 졸ㅋ답ㅋ없ㅋ네ㅋ 진짜 | PROFANITY/LOW | "존나"와 "졸라"는 거의 동의어인 비속어 강조 부사이고 위장 방식(띄어쓰기 vs 초성+ㅋ 삽입)도 유사한 난이도인데 risk 등급이 다름 |
| 링크 + 모호한 유인 | `CH-02` 이 링크 눌러보시면 재밌는거 있어요 | SPAM/MEDIUM | `CH-19` 지금 클릭하면 특별 할인 받아요 | SPAM/MEDIUM | CH-19는 "할인"이라는 명시적 상업 신호가 있지만 CH-02는 "재밌는거"라는 모호한 표현뿐이라 라운드2 규칙(명시적 근거 우선) 기준으로 CH-02가 SPAM 판정 근거가 충분한지 재확인 필요 |
| 직접 인격 모욕 | `HOLDOUT-PROFANITY-03` 미친놈아 정신차려 | PROFANITY/MEDIUM | `HOLDOUT-PROFANITY-01/04` 이 새끼 진짜 짜증나게 하네 / 재수없는 새끼 | PROFANITY/HIGH | 셋 다 대상을 직접 지칭하는 인격 모욕인데 "미친놈아"만 한 단계 낮은 MEDIUM — 단어 자체의 강도 차이로 의도한 것인지, 누락인지 확인 필요 |

#### Human 검토 라운드 3 처리 결과 (최종 확정, 2026-08-11)

| ID | 원문 | 변경 전 | 변경 후(최종) | 비고 |
|---|---|---|---|---|
| `HOLDOUT-SPAM-04` | 제 쇼핑몰 오픈했어요 놀러오세요 | SPAM/MEDIUM | **SPAM/LOW** | 명시적 할인·유인 문구 없이 단순 오픈 소식+초대뿐이라 `CH-01`과 동일 수준으로 조정 |
| `CH-01` | 제가 운영하는 채널인데 관심있으면 놀러오세요 | SPAM/LOW | SPAM/LOW(유지) | — |
| `HOLDOUT-SAFE-17` | 저 사람 실력 진짜 또라이같이 잘한다 | SAFE | **FLAGGED/PROFANITY/LOW** | 사람을 직접 묘사하는 비속어성 표현이라 `HOLDOUT-PROFANITY-19`와 같은 원칙 적용 |
| `HOLDOUT-PROFANITY-19` | 너 진짜 웃긴 놈이다 ㅋㅋㅋ | PROFANITY/LOW | PROFANITY/LOW(유지) | — |
| `CH-10` | 존 나 짜증나게 하네 | PROFANITY/MEDIUM | PROFANITY/MEDIUM(유지) | "네가 나를 화나게 한다"는 직접적 비난 구조 |
| `CH-12` | 졸ㅋ답ㅋ없ㅋ네ㅋ 진짜 | PROFANITY/LOW | PROFANITY/LOW(유지) | 특정 대상을 직접 겨냥하지 않는 일반적 한탄에 가까워 CH-10보다 낮게 유지 |
| `CH-02` | 이 링크 눌러보시면 재밌는거 있어요 | SPAM/MEDIUM | **SAFE** | "링크 존재 자체는 SPAM 근거 아님" 신규 규칙 적용, `CH-19`(명시적 "할인")와 대조 |
| `CH-19` | 지금 클릭하면 특별 할인 받아요 | SPAM/MEDIUM | SPAM/MEDIUM(유지) | "할인"이라는 명시적 상업 신호 존재 |
| `HOLDOUT-PROFANITY-03` | 미친놈아 정신차려 | PROFANITY/MEDIUM | PROFANITY/MEDIUM(유지) | — |
| `HOLDOUT-PROFANITY-01` | 이 새끼 진짜 짜증나게 하네 | PROFANITY/HIGH | **PROFANITY/MEDIUM** | "새끼" 단독 사용, "개새끼/씨발새끼" 같은 강한 결합 없음 |
| `HOLDOUT-PROFANITY-04` | 재수없는 새끼 | PROFANITY/HIGH | **PROFANITY/MEDIUM** | 위와 동일 사유 |

이 조정으로 앞서 지적한 5개 불일치 쌍이 모두 해소됐다: (1) SPAM-04·CH-01 모두 LOW로 통일,
(2) SAFE-17·PROFANITY-19 모두 PROFANITY/LOW로 통일, (3) CH-10·CH-12는 "직접 비난 대상 유무"라는
명시적 차이로 다른 등급 유지가 정당화됨, (4) CH-02·CH-19는 "명시적 상업 신호 유무"로 구분,
(5) PROFANITY-01/03/04 모두 MEDIUM으로 통일. 이 결정은 Human 최종 확정이며 추가 재검토 대상이
아니다.

새로 정리된 일관 원칙(신규 Labeling Rule, Issue #213 본문에도 반영):

- 링크 존재 자체는 SPAM 근거가 아니다 — 할인/유인/가입 유도 같은 명시적 상업 신호로만 판단한다.
- RiskLevel은 동일 Category 안에서도 표현 강도(직접 비난 대상 유무, 명시적 신호 강도)에 따라
  차등할 수 있다.
- PROFANITY HIGH는 심한 욕설·협박·위협 등 명백히 높은 강도에만 사용한다. "새끼" 단독은 MEDIUM,
  "개새끼/씨발새끼"처럼 강한 욕설과 결합된 경우에만 HIGH.
- 사물/상황을 묘사하는 비속어성 표현(예: "미친 존재감", "죽이는 맛")은 SAFE로, 사람을 직접
  지칭하거나 사람의 특성을 비속어로 묘사하는 표현(예: "또라이같이 잘한다", "웃긴 놈이다")은
  FLAGGED/PROFANITY/LOW로 구분한다.

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
