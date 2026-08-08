# BobFull V3 Sprint AI Review·Human 이해 가이드

## 1. 현재 리뷰 목적

V3 Sprint Mode의 리뷰 목적은 **완벽한 코드 만들기**가 아니라 **치명적인 문제를 Merge 전에 잡는 것**이다.

따라서 리뷰는 다음을 우선한다.

```text
기능이 실제로 깨지는가
데이터·권한·결제·환불·예약 정합성이 깨지는가
런타임에서 주요 장애가 나는가
핵심 실패 처리가 빠졌는가
전체 build·직접 검증 결과가 사실인가
```

스타일·취향·범위 밖 개선으로 Merge를 지연시키지 않는다.

## 2. 역할 분리

```text
구현 담당자 AI
= 구현·테스트·PR 설명·리뷰 반영

GitHub Copilot Reviewer
= 독립 자동 코드 리뷰

담당자 Human
= 실제 정책 결정 + 강화 PR 이해도 + Merge 판단

Human Reviewer
= 선택적 추가 리뷰, V3 Sprint Mode 필수 승인 Gate 아님
```

## 3. 자동 리뷰

Repository Ruleset:

```text
Automatically request Copilot code review = Enabled
Review draft pull requests = Enabled
Review new pushes = Enabled
```

최초 코드 리뷰를 위해 Human 명령을 요구하지 않는다.

자동 리뷰 컨텍스트:

- `.github/copilot-instructions.md`
- `.github/skills/bobfull-pr-review/SKILL.md`
- 최신 PR Head와 실제 Diff
- 저장소 코드·테스트·관련 문서

## 4. V3 Sprint 리뷰 기준

### 최우선

- 요구한 핵심 기능이 실제로 동작하지 않음
- 데이터 손실·중복 처리·잘못된 상태 전이
- 결제·환불·예약·좌석·권한의 치명적 정합성 문제
- 주요 런타임 예외·NPE·컴파일/빌드 문제
- Transaction·Rollback·Lock·멱등성·외부 I/O 경계의 치명적 오류
- 인증·인가·소유권 우회
- 필수 실패 처리 누락
- 테스트·build·직접 검증 결과를 사실과 다르게 기록

### 필요할 때만

- 유지보수성
- 코드 명확성
- 추가 테스트
- 추가 리팩터링
- 구조 개선
- 성능 최적화

현재 요구 기능을 깨지 않는다면 아래 항목은 Merge 차단보다 기록을 우선한다.

## 5. 중요도와 Merge 영향

### BLOCKER — Merge 금지

- 보안·권한 우회
- 데이터 손실·중복 결제 등 치명적 정합성 오류
- 핵심 계약 정면 위반
- build 불가 또는 핵심 기능 완전 실패

### MAJOR — Merge 금지

- 주요 요구사항 실패
- 주요 런타임 오류
- 필수 실패 처리 누락
- 실제 운영 흐름에서 높은 확률로 잘못된 결과 발생

### MINOR — Merge 가능

- 핵심 기능은 정상이나 품질 개선 가치가 있음
- 추가 테스트·명확성·작은 유지보수성 문제

### SUGGESTION — Merge 가능

- 선택적 개선
- 향후 리팩터링·최적화 아이디어

### PASS

보고할 BLOCKER·MAJOR가 없고 필수 검증이 사실과 일치함.

> V3 Sprint Mode에서 `PASS`는 “완벽함”이 아니라 “현재 Merge를 막을 치명적 문제가 보이지 않음”을 뜻한다.

## 6. 리뷰 출력

실제 발견 사항은 높은 중요도부터 기록한다.

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
판정: BLOCK | MERGEABLE
```

- BLOCKER/MAJOR 하나라도 있으면 `BLOCK`
- MINOR/SUGGESTION만 있으면 `MERGEABLE`
- 지적을 만들기 위해 억지로 문제를 생성하지 않는다.

코드 위치가 명확하면 inline review comment를 우선한다.

## 7. 필수 검증

### 기능 PR

```text
관련 테스트 PASS
전체 build PASS
변경 핵심 기능 직접 검증 PASS
```

HTTP/API 변경은 Postman, curl 또는 동등한 실제 요청을 우선한다.

### 문서·설정 PR

- 전체 build가 의미 없으면 `NOT_RUN` 이유 명시 가능
- 정적 검사·렌더링·설정 적용 등 변경과 직접 관련된 검증 수행

실행하지 않은 것을 PASS로 기록하지 않는다.

## 8. 리뷰 반영

### Merge 전에 수정

- BLOCKER
- MAJOR
- build 실패
- 직접 검증 실패
- 핵심 기능 오류

### Merge 후 처리 가능

- MINOR
- SUGGESTION
- 범위 밖 리팩터링
- 추가 최적화
- 현재 기능을 깨지 않는 추가 테스트

후속 가치가 있으면 Issue 또는 `docs/troubleshooting`에 기록한다.

## 9. Human 이해도

### Issue 단계

V3 Sprint Mode에서는 학습용 질문을 구현 착수 Gate로 사용하지 않는다.

Human 질문은 실제 결정이 필요할 때만 한다.

### PR 기본

```text
Human 이해도 질문: 0개
```

### PR 강화

```text
Human 이해도 질문: 정확히 3개
```

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 실제 적용 위치·이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

목표는 클래스·메서드 암기가 아니라 **담당자가 자신이 만든 기능을 설명할 수 있는 상태**다.

## 10. Human Review Checklist

이 체크리스트는 이해 기준이며 V3 Sprint Mode의 별도 Approve Gate가 아니다.

- [ ] 무엇을 왜 바꿨는지 이해했다.
- [ ] 기본 흐름과 중요한 분기를 이해했다.
- [ ] 중요한 기술 개념과 적용 이유를 이해했다.
- [ ] 전체 build·직접 검증·자동 AI Review 결과와 남은 위험을 확인했다.

## 11. V3 Sprint Merge Gate

Merge 차단 조건은 다음으로 제한한다.

```text
전체 build FAIL
핵심 기능 직접 검증 FAIL
Automatic Copilot Review 미실행
미해결 BLOCKER
미해결 MAJOR
Human 결정 필요 사항 미해결
강화 PR Human 3문항 미완료
```

그 외 MINOR·SUGGESTION은 기록 후 Merge 가능하다.

필수 Human Approve 수는 `0`이다.
Merge는 담당 Human이 수행한다.

## 12. 금지 사항

- 중요하지 않은 문제로 Merge 지연
- MINOR/SUGGESTION을 BLOCKER처럼 취급
- 실행하지 않은 검증을 PASS 처리
- Automatic Copilot Review 미실행인데 완료 처리
- Human 답변 대리 작성
- 정책·API·DB·권한·트랜잭션 임의 결정
- AI가 Merge 수행
