---
name: bobfull-pr-explain
description: BobFull Draft PR 생성·본문 갱신·최신 Head 재검토 때 실제 Issue, Diff, 검증 근거를 바탕으로 이해 중심 PR 요약과 상세 검증 근거를 작성·검증한다.
---

# BobFull PR Explain Diff

## 사용 시점

다음 작업을 수행하기 전 이 Skill을 직접 읽고 적용한다.

- 구현 완료 후 Draft PR 생성
- 기존 PR 본문 복구 또는 최신 템플릿 반영
- 새 Commit 반영 후 PR 본문·다이어그램·Checklist 갱신
- `PR #번호 검토하라`에서 최신 Head 기준 PR 설명 재검토
- Ready 전환 또는 Human 리뷰 요청 전 최종 PR 본문 확인

이 Skill은 PR 본문을 자동으로 수정·Commit·Push하는 도구가 아니다. 실제 근거를 읽어 설명을 작성·검증하는 절차다.

## 필수 입력과 중단 기준

다음 자료를 최신 상태로 확보한다.

1. 연결 Issue의 본문, 댓글, 현재 `status:*` Label과 최종 계약
2. 최신 `.github/pull_request_template.md`
3. base와 Head 사이의 실제 Diff, 변경 파일과 실제 호출·상태 흐름
4. 추가·수정 테스트와 실제로 실행한 테스트·build·직접 검증·CI 결과
5. 변경과 직접 관련된 확정 문서

Issue·Diff·검증 근거 중 PR 설명에 필요한 자료를 읽지 못했거나 실제 상태를 확인할 수 없으면 내용을 추측해 작성하지 않는다. 부족한 자료, 영향을 받는 본문 영역과 중단 이유를 보고한다. 실행하지 않은 검증은 `NOT_RUN | 미실행`과 이유·한계로 기록한다.

## 작성 절차

1. 최종 계약과 최신 Diff를 대조해 변경 전 동작, 해결할 문제, 변경 범위와 제외 범위를 정리한다.
2. 최신 템플릿의 섹션 이름과 순서를 유지한다. 한 줄 요약의 네 하위 필드는 삭제하거나 한 문장으로 대체하지 않는다.
3. `PR 이해 요약`을 실제 근거로 작성한다.
   - `쉬운 설명`: 전문용어를 최소화해 3~5문장으로 목적과 결과를 설명한다.
   - `주요 실행 흐름`: 요청 → 검증 → Transaction/Lock → 상태 변경 → 이벤트/외부 I/O → 응답 중 실제 핵심 순서와 필요한 분기를 짧게 설명한다.
   - `Mermaid 시각화`: 기능 PR이면 최신 Head의 주요 흐름과 필요한 Transaction·Lock·Event 경계를 설명한다.
   - `주요 개념`: 실제 이해에 필요한 개념만 2~5개를 `개념 | 쉽게 말하면 | 이 PR에서 왜 필요한가` 표로 작성한다.
   - `핵심 트러블슈팅`: 실제 의미 있는 문제만 3~5문장으로 요약하고 상세 원본 경로를 연결한다.
   - `코드 읽는 순서`: 실제 변경 파일·호출 흐름을 따라 3~6단계의 책임을 안내한다.
4. 단순 문서·설정·DTO·정적 상수 변경처럼 해당 항목이 필요 없으면 가짜 설명을 만들지 않고 `해당 없음`과 이유를 기록한다.
5. `상세 변경 및 검증`에는 주요 변경, 예외·실패·중복·경계, 트레이드오프, 제한사항, 제외 범위, 추가·수정 테스트, 완료 조건과 실제 검증 증거를 유지한다. 긴 정보만 `<details>`로 접으며 `BLOCKER`, `FAIL`, `NOT_RUN`, 미검증 위험은 접힌 영역에만 두지 않는다.
6. `추가·수정 테스트`은 검증 코드를 설명하고, `테스트·build·직접 검증`은 실제 실행 결과를 기록한다.
7. Human 이해도 질문과 PR 전용 Human Review Checklist는 최신 Diff·테스트를 근거로 작성한다. AI가 Human 답변·리뷰·Approve를 대신 작성하거나 Checklist를 선체크하지 않는다.

## Mermaid 실행 흐름 시각화

의미 있는 실행 흐름이 있는 기능 PR에는 Mermaid 다이어그램을 최소 1개 포함한다.

- 처리 순서·조건 분기: `flowchart`
- 사용자·클래스·외부 시스템의 호출 순서: `sequenceDiagram`
- 도메인 상태 전이: `stateDiagram-v2`
- 데이터 관계가 변경 이해의 중심: `erDiagram`

작성 후 다음을 대조한다.

- 노드·참여자·상태·분기가 최신 Head의 실제 클래스·메서드·호출 관계와 일치하는가
- Transaction, Lock, AFTER_COMMIT, Async, 외부 I/O 중 이번 변경을 이해하는 데 필요한 경계만 표현했는가
- 구현되지 않은 분기나 후속 계획을 현재 동작처럼 표현하지 않았는가
- 다이어그램이 장식용이거나 본문을 단순 반복하지 않는가
- GitHub에서 Mermaid가 정상 렌더링되는가

## 최신 Head 갱신과 최종 확인

새 Commit으로 호출 관계·분기·상태 전이·테스트가 바뀌면 이 Skill을 다시 적용한다. 이전 Head를 기준으로 한 이해 요약, 상세 검증, 다이어그램, 코드 읽는 순서, Human 이해도 질문, Checklist와 검증 결과를 최신 Diff에 맞춰 갱신한다.

`PR #번호 검토하라`와 Ready 전 최종 확인에서는 연결 Issue 계약, 최신 Head, PR 이해 요약, 상세 검증, Mermaid, 검증 증거, Checklist를 다시 대조한다. 설명·다이어그램·Checklist가 실제 Diff와 불일치하면 PR 이해 문서화는 완료되지 않은 것으로 기록하고 수정 또는 Human 판단을 진행한다.

## 작성 범위

정책과 출력 형식의 원본은 `.github/pull_request_template.md`, `docs/AI_WORKFLOW.md`, `docs/AI_IMPLEMENTATION_GUIDE.md`, `docs/AI_REVIEW_GUIDE.md`에 둔다. 이 Skill에는 실행 절차만 유지한다.

PR Explain Diff의 공식 목적은 다른 사람이 변경을 빠르게 이해하고 실제 Diff와 검증 근거를 올바르게 리뷰하게 하는 것이다. PR 자체를 포트폴리오 문서로 확장하지 않는다. 별도 HTML, PNG·SVG·AI 생성 이미지, GitHub Actions Artifact·Pages, Flow Lab, 새로운 퀴즈·승인·Merge Gate를 만들지 않는다.
