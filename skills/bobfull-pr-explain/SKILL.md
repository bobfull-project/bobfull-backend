---
name: bobfull-pr-explain
description: BobFull Draft PR 생성·본문 갱신·최신 Head 재검토 때 실제 Issue, Diff, 검증 근거를 바탕으로 Explain Diff와 Mermaid 실행 흐름 시각화를 작성·검증한다.
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

1. 최종 계약과 최신 Diff를 대조해 변경 전 동작, 해결할 문제, 변경 범위와 제외 범위를 정리한다. 신규 기능에는 존재하지 않았던 이전 동작을 만들지 않는다.
2. 최신 템플릿의 섹션 이름과 순서를 유지한다. `한 줄 요약`의 `무엇을`, `왜`, `기술부채(있다면)`, `의도부채(있다면, Issue 논의와 달라진 부분)` 하위 필드는 삭제하거나 한 문장으로 대체하지 않는다. 앞의 두 필드는 실제 Issue·Diff를 근거로 작성하고, 기술부채와 의도부채가 없으면 각각 `없음`으로 기록한다. 기술부채는 현재 제한사항과 혼동하지 않는다.
3. 요청·검증·상태 변경·저장·응답의 실제 실행 흐름을 작성한다. 실행 흐름과 실제 클래스·메서드를 연결한 코드 확인 순서를 제공한다.
4. 아래 기준으로 Mermaid 필요 여부와 형식을 결정하고, 필요한 경우 GitHub PR 본문의 `mermaid` 코드블록에 작성한다.
5. 실제 근거가 있는 예외·실패·중복·경계 상황, 트레이드오프, 현재 제한사항과 확정된 후속 개선만 기록한다. 단순 문서·설정 PR에서는 해당하지 않는 Explain Diff 영역을 `해당 없음`과 이유로 짧게 작성할 수 있다.
6. `추가·수정 테스트`에는 실제 추가·수정한 테스트 클래스·파일, 검증 시나리오와 테스트가 보장하는 것을 기록한다. 테스트 코드를 변경하지 않았다면 `해당 없음`과 이유를 기록한다. 이 영역은 테스트·build·직접 검증의 실제 실행 결과와 구분한다.
7. Issue 완료 조건을 구현 위치와 실제 검증 증거에 연결한다. Human 이해도 질문과 PR 전용 Human Review Checklist는 최신 Diff·테스트를 근거로 미체크(`- [ ]`) 항목만 작성한다. 의미 있는 실행 흐름과 Mermaid가 있는 기능 PR의 `구현 확인`에는 Mermaid가 최신 Head의 실제 호출·분기·상태 흐름과 일치하는지 확인하는 PR 전용 항목을 포함한다. 단순 문서 PR에는 이 항목을 만들지 않는다.
8. PR 본문 마지막의 `Human Review Checklist`와 `담당자 AI 검토·수정 기록`을 유지한다. AI가 Human 답변·리뷰·Approve를 대신 작성하거나 Checklist를 선체크하지 않는다.

강화 검토의 구현 전 설계 확인 기록은 PR Conversation 댓글에 유지한다. 책임 클래스, 상태 변경 위치, 트랜잭션 범위, 실패 처리 방식을 PR 본문에 그대로 복제하지 않고, Explain Diff에서는 리뷰에 필요한 실행 흐름·선택 이유·예외만 한 번 요약한다.

## Mermaid 실행 흐름 시각화

Controller·Service·Port/Adapter·Repository 호출, 상태 전이, 분기, 트랜잭션, 락, 이벤트처럼 의미 있는 실행 흐름이 있는 기능 PR에는 Mermaid 다이어그램을 최소 1개 포함한다.

- 처리 순서·조건 분기: `flowchart`
- 사용자·클래스·외부 시스템의 호출 순서: `sequenceDiagram`
- 도메인 상태 전이: `stateDiagram-v2`
- 데이터 관계가 변경 이해의 중심: `erDiagram` 또는 더 적합한 Mermaid 표현

서로 다른 관점이 실제 이해에 필요할 때만 다이어그램을 추가한다. 단순 문서·설정·DTO·정적 상수 변경처럼 의미 있는 실행 흐름이 없으면 `해당 없음`과 이유를 명시하고 생략할 수 있다.

작성 후 다음을 대조한다.

- 노드·참여자·상태·분기가 최신 Head의 실제 클래스·메서드·호출 관계와 일치하는가
- 구현되지 않은 분기나 후속 계획을 현재 동작처럼 표현하지 않았는가
- 다이어그램이 설명을 장식하거나 본문과 같은 내용을 반복하지 않는가
- GitHub에서 Mermaid가 정상 렌더링되는가

## 최신 Head 갱신과 최종 확인

새 Commit으로 호출 관계·분기·상태 전이·테스트가 바뀌면 이 Skill을 다시 적용한다. 이전 Head를 기준으로 한 설명, 코드 확인 순서, 다이어그램, 검증 결과, Human 이해도 질문, Checklist를 제거하거나 최신 Diff에 맞춰 갱신한다.

`PR #번호 검토하라`와 Ready 전 최종 확인에서는 연결 Issue 계약, 최신 Head, PR 본문, Mermaid, 검증 증거, Checklist를 다시 대조한다. 설명·다이어그램·Checklist가 실제 Diff와 불일치하면 PR 이해 문서화는 완료되지 않은 것으로 기록하고 수정 또는 Human 판단을 진행한다.

## 작성 범위

정책과 출력 형식의 원본은 `.github/pull_request_template.md`, `docs/AI_WORKFLOW.md`, `docs/AI_IMPLEMENTATION_GUIDE.md`, `docs/AI_REVIEW_GUIDE.md`에 둔다. 이 Skill에는 실행 절차만 유지한다.

PR Explain Diff는 변경 하나를 이해하기 위한 GitHub Markdown이다. 별도 HTML, PNG·SVG·AI 생성 이미지, GitHub Actions Artifact·Pages, Flow Lab, 새로운 퀴즈·승인·Merge Gate를 만들지 않는다.
