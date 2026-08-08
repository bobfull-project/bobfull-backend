---
name: bobfull-pr-explain
description: BobFull Draft PR 생성·본문 갱신 때 실제 Issue, Diff, 검증 근거를 바탕으로 이해 중심 PR 요약과 상세 검증 근거를 작성·검증한다.
---

# BobFull PR Explain Diff

## 역할

이 Skill은 **PR 본문 설명 작성**만 담당한다. 코드 결함 리뷰는 구현 담당 AI가 수행하지 않고, PR 생성/업데이트 시 GitHub가 자동 요청하는 독립 Copilot reviewer가 `.github/skills/bobfull-pr-review/SKILL.md` 기준으로 수행한다.

## 사용 시점

- 구현 완료 후 Draft PR 생성 전
- 기존 PR 본문 복구 또는 최신 템플릿 반영
- 새 Commit으로 실제 흐름·테스트·검증 상태가 바뀐 뒤 PR 본문 갱신
- Ready 전환 또는 Human 리뷰 요청 전 PR 설명 최종 확인

## 필수 입력

1. 연결 Issue의 본문·최종 계약·현재 `status:*` Label
2. 최신 `.github/pull_request_template.md`
3. base와 Head 사이 실제 Diff와 변경 파일
4. 추가·수정 테스트 및 실제 테스트·build·직접 검증·CI 결과
5. 변경과 직접 관련된 확정 문서

근거를 확인하지 못한 영역은 추측하지 않는다. 실행하지 않은 검증은 `NOT_RUN | 미실행`과 이유·한계를 기록한다.

## 작성 절차

1. 최종 계약과 최신 Diff를 대조해 변경 목적·범위·제외 범위를 정리한다.
2. 최신 템플릿의 `한 줄 요약/관련 Issue → PR 이해 요약 → 상세 변경 및 검증 → Human 검토` 순서를 유지한다.
3. `PR 이해 요약`을 실제 근거로 작성한다.
   - 쉬운 설명: 3~5문장
   - 주요 실행 흐름: 핵심 순서와 분기
   - Mermaid: 의미 있는 기능 흐름이 있을 때만
   - 주요 개념: 실제 이해에 필요한 2~5개
   - 핵심 트러블슈팅: 실제 문제 해결이 있을 때만 3~5문장
   - 코드 읽는 순서: 실제 변경 파일·호출 흐름을 따라 3~6단계
4. 단순 문서·설정·DTO·정적 상수·단순 CRUD처럼 해당 항목이 필요 없으면 `해당 없음`과 이유를 기록한다.
5. 상세 변경 및 검증에는 주요 변경, 예외·실패·중복·경계, 트레이드오프, 제한사항, 제외 범위, 테스트와 실제 검증 증거를 유지한다.
6. 긴 정보만 `<details>`로 접고 `BLOCKER`, `FAIL`, `NOT_RUN`, 미검증 위험은 접힌 영역에만 두지 않는다.
7. PR Human 이해도는 검토 수준에 따라 고정한다.
   - 기본: 질문 0개
   - 강화: 정확히 3개 — 핵심 흐름 / 중요 개념과 적용 이유 / 설계 선택·실패 처리·남은 한계
8. Human Review Checklist는 템플릿의 공통 4개 이해 체크를 유지한다.

## Mermaid 기준

의미 있는 실행 흐름이 있는 기능 PR만 Mermaid를 포함한다.

- 처리 순서·조건 분기: `flowchart`
- 호출 순서: `sequenceDiagram`
- 상태 전이: `stateDiagram-v2`
- 데이터 관계: `erDiagram`

최신 Head의 실제 클래스·상태·분기와 일치해야 하며 구현되지 않은 후속 계획을 현재 동작처럼 그리지 않는다.

## 자동 AI Review와의 경계

PR 본문을 작성한 뒤 구현 담당 AI가 자기 PR을 다시 리뷰하는 절차를 실행하지 않는다.

다음은 GitHub 자동화가 담당한다.

```text
PR opened / synchronize / reopened / ready_for_review
→ .github/workflows/copilot-auto-review.yml
→ GitHub Copilot reviewer 자동 요청
→ .github/skills/bobfull-pr-review/SKILL.md 적용
→ 독립 AI Review 댓글
```

새 Push가 올라오면 자동 reviewer가 최신 Head를 다시 검토한다. 최초 리뷰를 위해 사람이 `PR #번호 검토하라`를 입력할 필요가 없다.

## 작성 범위

PR Explain의 목적은 다른 사람이 변경을 빠르게 이해하고 실제 Diff와 검증 근거를 올바르게 리뷰하게 하는 것이다. 별도 HTML·이미지·Flow Lab·포트폴리오 문서를 이 Skill에서 만들지 않는다.
