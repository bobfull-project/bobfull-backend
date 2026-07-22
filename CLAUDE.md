# BobFull Claude Code 진입점

@AGENTS.md

Claude Code는 공통 실행 규칙을 `AGENTS.md`에서 가져온다.

- 작업 전 대상 GitHub Issue를 먼저 읽는다.
- 필요한 문서와 관련 코드·테스트만 추가로 읽는다.
- `AGENTS.md`와 대상 Issue가 충돌하면 임의로 구현하지 않고 Human 판단을 요청한다.
- Claude 전용 규칙은 공통 규칙과 중복해서 작성하지 않는다.
