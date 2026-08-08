# BobFull AI 협업 워크플로우

## 1. 현재 운영 모드 — V3 Sprint Mode

V3 마무리 기간에는 **속도를 우선하되 치명적인 결함은 담당 구현 AI의 독립 리뷰 패스와 필수 검증으로 차단**한다.

이 모드의 원칙은 `검토를 없앤다`가 아니라 **Merge를 막는 기준을 핵심 위험으로 좁힌다**는 것이다.

### Merge를 막는 항목

- 전체 build 실패
- 변경한 핵심 기능의 직접 검증 실패
- 최신 담당 구현 AI Review의 미해결 `BLOCKER` 또는 `MAJOR`
- 정책·API·DB·상태·권한·트랜잭션처럼 Human 결정이 필요한 미확정 사항
- 강화 PR의 Human 이해도 3문항 미완료

### Merge를 막지 않는 항목

- `MINOR`
- `SUGGESTION`
- 범위 밖 리팩터링
- 추가하면 좋은 테스트
- 현재 기능에 직접 영향이 없는 코드 스타일 개선
- 후속 개선으로 남길 수 있는 기술부채

이 항목들은 PR 또는 후속 Issue·트러블슈팅으로 기록하고 진행을 막지 않는다.

### 승인 정책

V3 Sprint Mode에서는 **필수 Human Approve 인원은 0명**으로 운영한다.

- 별도 리뷰어 승인을 기다리지 않는다.
- Human Review는 가능하면 수행하지만 Merge 필수 Gate로 두지 않는다.
- Merge는 담당 Human이 필수 검증과 담당 구현 AI Review 결과를 확인한 뒤 수행한다.
- 스퍼트 종료 후 승인 정책은 다시 평가한다.

## 2. 역할

- 구현 담당 AI: Issue 계약에 따라 구현·테스트·PR 설명 작성·독립 리뷰 패스·리뷰 반영
- 담당자 Human: 실제 정책 판단, 강화 PR 이해도 답변, 최종 Merge 판단
- Human 리뷰어: 선택적 추가 리뷰. V3 Sprint Mode에서는 승인 대기 Gate가 아님

GitHub Copilot Code Review나 별도 외부 리뷰 서비스는 필수 구성요소가 아니다.

## 3. 전체 흐름

```text
Issue 분석
→ 미결정 정책이 없으면 바로 구현
→ status:in-progress
→ 구현·관련 테스트·직접 검증
→ 전체 build
→ Diff 자체 검토
→ bobfull-pr-explain 적용
→ Draft PR 생성
→ 같은 담당 구현 AI가 즉시 독립 리뷰 패스로 전환
→ 최신 Issue/Head/Diff/검증 근거 재수집
→ 중요도 순 PR Review 댓글 작성
→ BLOCKER/MAJOR면 수정·재검증·Push
→ 같은 담당 구현 AI가 최신 Head 즉시 재리뷰
→ 기본 PR: Human 이해도 0개
→ 강화 PR: Human 이해도 정확히 3개
→ Sprint Merge Gate 확인
→ 담당 Human Merge
```

**최초 AI Review를 위해 `PR #번호 검토하라` 같은 추가 Human 명령을 요구하지 않는다.**
Draft PR 생성이 담당 구현 AI의 리뷰 단계 시작점이다.

## 4. Issue 단계 — 질문 병목 최소화

V3 Sprint Mode에서는 Issue 단계의 **학습용 Human 질문을 구현 착수 Gate로 사용하지 않는다.**

AI가 Issue·코드·확정 문서를 읽고 구현 가능한 수준이면 바로 계약을 정리하고 진행한다.

Human 질문은 다음처럼 실제 결정이 필요한 경우에만 한다.

- 정책이 둘 이상 가능하고 코드만으로 결정할 수 없음
- API·DB·상태 계약 변경
- 권한·금액·트랜잭션·보상 정책 재결정
- 다른 담당자 범위와 충돌

실제 실행 상태는 GitHub `status:*` Label 하나로 관리한다.

```text
status:human-answer-required
status:in-progress
status:final-human-review
```

## 5. 구현과 Draft PR

`status:in-progress` 이후 구현 AI는 다음을 수행한다.

1. 대상 Issue 전용 브랜치 확인 또는 최신 `develop` 기준 생성
2. Issue 최종 계약 범위의 최소 변경 계획 수립
3. 코드·테스트·필요 문서 구현
4. 변경 기능 관련 테스트 실행
5. HTTP/API 기능이면 Postman 또는 동등한 직접 호출로 핵심 성공 흐름 검증
6. 전체 build 실행
7. 실제 Diff 자체 검토
8. `skills/bobfull-pr-explain/SKILL.md` 적용
9. Issue 관련 변경만 Commit·Push
10. develop 대상 Draft PR 생성
11. **즉시 `skills/bobfull-pr-review/SKILL.md`를 적용해 독립 리뷰 패스 실행 및 PR 댓글 작성**

### 필수 검증 기준

기능 PR의 최소 필수 검증은 다음이다.

```text
관련 테스트 PASS
+ 전체 build PASS
+ 변경한 핵심 기능 직접 검증 PASS
```

직접 검증 예:

- HTTP/API: Postman, curl 또는 동등한 실제 요청
- Scheduler/Consumer/Event: 해당 동작을 실제 실행 가능한 테스트·로그·직접 트리거
- 문서/설정 전용: 적용 결과 또는 정적 검증

기능과 관계없는 추가 검증을 무조건 늘리지 않는다.

## 6. 담당 구현 AI의 독립 리뷰 패스

### 6.1 실행 원칙

리뷰는 별도 AI 제품이 아니라 **해당 PR을 구현한 담당 AI가 역할을 구현자에서 리뷰어로 전환해 수행**한다.

리뷰할 때 구현 중 기억을 정답으로 가정하지 않고 다음을 최신 GitHub 상태에서 다시 읽는다.

- 연결 Issue 최신 계약
- 최신 Head SHA
- base 대비 실제 Diff
- 관련 테스트·전체 build·직접 검증 결과
- 기존 리뷰 댓글과 미해결 지적

리뷰 기준은 `skills/bobfull-pr-review/SKILL.md`를 따른다.

### 6.2 자동 실행 시점

- Draft PR 생성 직후
- 담당 구현 AI가 기존 PR에 새 Commit을 Push한 직후
- BLOCKER/MAJOR 수정 Push 직후
- Merge 전 Head가 마지막 리뷰 Head와 달라진 경우

별도 Human 명령을 기다리지 않는다.

### 6.3 V3 Sprint Review 기준

```text
BLOCKER    → Merge 금지
MAJOR      → Merge 금지
MINOR      → 기록 후 Merge 가능
SUGGESTION → 기록 후 Merge 가능
PASS       → 현재 Merge를 막을 치명적 문제 없음
```

리뷰 흔적을 만들기 위해 중요하지 않은 문제를 억지로 찾지 않는다.

## 7. PR Human 이해도

### 기본

```text
Human 이해도 질문: 0개
```

문서·설정·단순 CRUD·기존 패턴 반복에는 질문을 만들지 않는다.

### 강화

```text
Human 이해도 질문: 정확히 3개
```

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 실제 적용 이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

질문은 코드 암기가 아니라 실제 기능을 이해하는 수준으로 작성한다.

## 8. 리뷰 반영

### Merge 전 반드시 수정

- BLOCKER
- MAJOR
- 필수 검증 실패
- 범위 안의 명확한 핵심 기능 오류

### Merge를 막지 않고 기록

- MINOR
- SUGGESTION
- 범위 밖 개선
- 추가 최적화·리팩터링
- 현재 요구 기능을 깨뜨리지 않는 테스트 보강 제안

수정 Push 뒤에는 같은 담당 구현 AI가 최신 Head를 다시 리뷰하고 새 댓글을 남긴다.

## 9. V3 Sprint Merge Gate

다음만 모두 만족하면 Merge 가능하다.

```text
[필수] 전체 build PASS 또는 해당 없음 근거 명확
[필수] 변경 핵심 기능 직접 검증 PASS 또는 해당 없음 근거 명확
[필수] 최신 Head 담당 구현 AI Review 완료
[필수] 미해결 BLOCKER 없음
[필수] 미해결 MAJOR 없음
[필수] Human 결정 필요 사항 없음
[강화 PR만] Human 이해도 3문항 완료
```

필수 Human Approve 수는 `0`이다.
MINOR/SUGGESTION은 Merge를 막지 않는다.
Merge는 담당 Human이 수행한다.
