# BobFull V3 Sprint Mode

## 목적

V3 마무리 기간에는 일정 병목을 최소화하면서 치명적 결함만 Merge 전에 확실히 차단한다.

이 모드는 품질 기준을 없애는 것이 아니라 **Merge Gate를 핵심 위험에 집중하는 운영 모드**다.

## 필수 Merge Gate

다음은 모두 충족해야 한다.

```text
전체 build PASS
변경 핵심 기능 직접 검증 PASS
Automatic Copilot Code Review 실행 확인
미해결 BLOCKER 없음
미해결 MAJOR 없음
Human 결정 필요 사항 없음
강화 PR이면 Human 이해도 3문항 완료
```

## 비차단 항목

다음은 기록 후 Merge 가능하다.

- MINOR
- SUGGESTION
- 범위 밖 리팩터링
- 추가 최적화
- 현재 기능을 깨지 않는 추가 테스트
- 후속 기술부채

## Human 승인

V3 Sprint Mode의 필수 Human Approve 수는 `0`이다.

별도 리뷰어 승인 대기를 Merge Gate로 두지 않는다.
Merge는 담당 Human이 필수 검증과 자동 AI Review 결과를 확인한 뒤 수행한다.

## Human 이해

- 기본 PR: Human 이해도 질문 0개
- 강화 PR: 정확히 3개
  1. 핵심 실행 흐름과 주요 분기
  2. 중요한 기술 개념과 실제 적용 이유
  3. 설계 선택 이유, 주요 실패 처리와 남은 한계

PR Explain은 팀원이 빠르게 흐름·개념·검증 상태를 이해할 수 있게 유지한다.

## Issue 단계

학습용 질문을 구현 착수 Gate로 사용하지 않는다.
Human 질문은 정책·API·DB·상태·권한·트랜잭션 등 실제 결정이 필요할 때만 한다.

## 직접 검증

- HTTP/API: Postman, curl 또는 동등한 실제 요청
- 결제·예약·환불: 핵심 상태 변화와 결과 확인
- Scheduler/Event/Consumer: 직접 트리거·테스트·로그
- 문서/설정: 정적 검사 또는 실제 적용 결과

범위 밖 시나리오를 무제한 확장하지 않는다.

## Sprint 종료 후

V3 Sprint 종료 후 다음을 다시 평가한다.

- 필수 Human Approve 수
- Review 깊이
- 추가 테스트 Gate
- Sprint 중 누적된 MINOR/SUGGESTION/기술부채 처리 우선순위
