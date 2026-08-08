# BobFull PR 자동 AI Review·Human Review 가이드

## 1. 역할 분리

BobFull의 PR 리뷰는 다음 역할을 분리한다.

```text
구현 담당자 AI
= 구현·테스트·PR 설명·리뷰 반영

GitHub Copilot Reviewer
= PR event로 자동 실행되는 독립 AI 코드 리뷰어

담당자 Human
= 정책 판단 + 강화 PR 이해도 3문항

Human Reviewer
= 최종 코드 리뷰 + Approve
```

**구현 담당자 AI가 자기 PR을 다시 읽고 AI Review 댓글을 남기는 것을 공식 자동 리뷰로 인정하지 않는다.**

## 2. 자동 리뷰 시작 조건

최초 코드 리뷰를 위해 Human이 `PR #번호 검토하라`를 입력할 필요가 없다.

`.github/workflows/copilot-auto-review.yml`이 다음 event를 감지해 `copilot-pull-request-reviewer[bot]`을 reviewer로 자동 요청한다.

- `opened`
- `synchronize`
- `reopened`
- `ready_for_review`

따라서 새 Commit Push도 자동 재리뷰 대상이다.

자동 리뷰는 GitHub Copilot Code Review 기능이 저장소에서 사용 가능한 상태여야 한다. reviewer 요청이 실패하면 리뷰 미실행으로 처리하고 PASS로 간주하지 않는다.

## 3. 자동 리뷰 컨텍스트

Copilot reviewer는 다음 저장소 지침을 사용한다.

- `.github/copilot-instructions.md`
- `.github/skills/bobfull-pr-review/SKILL.md`
- PR 본문과 최신 Diff
- 저장소 코드·테스트·문서 컨텍스트

자동 리뷰 에이전트는 구현 담당자의 이전 대화나 숨은 reasoning을 전제로 하지 않는다.

## 4. 코드 리뷰 기준

### 모든 PR

- PR 설명의 목적·범위와 실제 Diff 일치
- 핵심 정상 흐름과 주요 실패·중복·경계 흐름
- 입력 검증과 예외 처리
- 상태·데이터 정합성
- 테스트와 변경 동작의 연결
- `PASS`, `NOT_RUN`, CI 상태의 사실성
- 범위 밖 변경과 불필요한 복잡성
- 비밀정보·개인정보 포함 여부

단순 문서·설정·CRUD도 리뷰 대상이지만 해당하지 않는 고급 아키텍처 문제를 억지로 지적하지 않는다.

### 강화 PR

인증·결제·환불·예약·좌석·상태 변경·정산·외부 연동·동시성·이벤트·캐시·인프라 핵심 변경 등 실패 비용이 큰 변경은 추가로 확인한다.

- 인증·인가·소유권
- Transaction·Rollback·부분 성공
- 상태·금액·수량 정합성
- 멱등성·중복 요청
- 동시성·Lock·Retry
- Event·AFTER_COMMIT·Outbox 등 후속 처리 경계
- 외부 I/O 실패와 보상
- 구현 전 설계 확인과 실제 Diff의 차이

## 5. 중요도 기준

발견 사항은 높은 중요도부터 판단한다.

### BLOCKER

Merge하면 안 되는 문제.

예:

- 보안·권한 우회
- 데이터 손실
- 중복 결제
- 핵심 정합성 훼손
- Issue 핵심 계약과 구현 정면 충돌

### MAJOR

요구사항 실패, 주요 런타임 오류, 필수 실패 처리 누락, 핵심 설계 문제.

### MINOR

Merge를 직접 막지는 않지만 명확성·유지보수성·테스트 정확도를 위해 수정 가치가 있는 문제.

### SUGGESTION

현재 구현도 허용 가능하며 선택적으로 개선할 수 있는 제안.

### PASS

실제 근거를 검토했지만 보고할 BLOCKER·MAJOR·MINOR가 없음.

리뷰 흔적을 남기기 위해 의미 없는 지적을 생성하지 않는다.

## 6. 리뷰 출력

실제 코드 위치의 문제는 inline review comment를 우선한다.

전체 결과는 가능하면 다음 순서로 정리한다.

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
판정: PASS | 수정 후 재검토 필요
```

BLOCKER 또는 MAJOR가 있으면 수정 후 재검토가 필요하다.

새 Push가 올라오면 이전 Head의 PASS를 최신 Head에 재사용하지 않는다.

## 7. 자동 리뷰 실패와 재실행

자동 reviewer 요청 workflow 또는 Copilot review가 실패한 경우:

- 실패 원인을 확인한다.
- 리뷰가 실제 PR에 생성됐는지 확인한다.
- 생성되지 않았으면 `자동 AI Review 미실행`으로 기록한다.
- Human Approve만으로 자동 리뷰가 실행된 것처럼 기록하지 않는다.

수정 Commit Push 후 `synchronize` event가 발생하면 Copilot reviewer를 다시 요청한다.

## 8. 리뷰 반영

자동 AI Review와 Human Review 의견은 최신 Issue 계약·Diff·테스트 근거로 판단한다.

### 범위 안 수정 가능

- 명확한 기능 오류
- 예외 처리·검증·테스트 누락
- PR 설명과 Diff 불일치
- 정책 재결정이 필요 없는 BLOCKER·MAJOR·MINOR

### Human 결정 필요

- Issue 범위 확장
- 정책·API·DB·상태·권한·트랜잭션 재결정
- 새로운 라이브러리·인프라 도입
- 다른 담당자 계약 변경

실제 수정 시작 시 연결 Issue를 `status:in-progress`로 되돌린다.

수정·검증·Push 후 자동 재리뷰 결과를 확인한다.

## 9. Human 이해도 정책

Issue 단계와 PR 단계를 구분한다.

### Issue 단계

기존 구현 착수 질문 정책을 유지한다.

- 기본: 필요한 질문 1~2개
- 강화: 필요한 질문 2~3개
- 강화: 담당자가 직접 작성한 이해 근거 한 줄 이상 필요

### PR 단계 — 기본

```text
Human 이해도 질문: 0개
```

문서·설정·단순 CRUD·기존 패턴 반복에는 질문을 만들지 않는다.

### PR 단계 — 강화

```text
Human 이해도 질문: 정확히 3개
```

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 실제 적용 위치·이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

질문은 현재 PR에 맞게 구체화하되 세 축을 늘리거나 줄이지 않는다.

특정 클래스·메서드 암기나 프레임워크 내부 구현 퀴즈를 만들지 않는다.

## 10. Human Review Checklist

모든 PR에서 공통 이해 체크를 사용한다.

- [ ] 이 PR이 무엇을 왜 변경하는지 이해했다.
- [ ] 변경 후 기본 실행 흐름과 중요한 분기를 이해했다.
- [ ] 중요한 기술 개념이 있다면 어디에 왜 적용됐는지 이해했다.
- [ ] 테스트·검증 결과와 남아 있는 미검증 위험을 확인했다.

PR마다 임의의 세부 구현 질문을 동적으로 만들지 않는다.

## 11. Merge 전 경계

다음을 확인한다.

- 최신 Head 필수 테스트·build·직접 검증 결과
- 최신 Head 자동 Copilot Review 실제 생성 여부
- 해결되지 않은 BLOCKER 없음
- 강화 PR이면 Human 이해도 3문항 답변과 AI 대조 완료
- Human Review 의견 반영 또는 판단 완료
- 남은 Human 결정 필요 사항 명시

`status:final-human-review`는 Human 최종 검토를 기다리는 상태다.

Approve와 Merge는 Human이 수행한다.

## 12. 금지 사항

- 구현 담당자 AI의 자기리뷰를 독립 자동 리뷰로 표시
- 자동 리뷰가 실행되지 않았는데 PASS로 기록
- Human 답변 대리 작성
- Human Checklist 선체크
- 정책·API·DB·권한·트랜잭션 임의 결정
- 이전 Head PASS를 최신 Head에 재사용
- AI Approve 또는 Merge
