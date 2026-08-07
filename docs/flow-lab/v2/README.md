# BobFull V2 Flow Lab

기준 `develop` SHA는 `a0d519561fb7ebe20eea8a08649f4f08f2583987`이다. V1을 변경하지 않고 V2 전용 Landing과 두 Lab을 제공한다.

- `reliability-flow-debugger`: 실제 채택된 흐름을 6개 Chapter로 재생한다.
- `reliability-experiment-lab`: 실제 채택안과 비교용 가상 대안을 4개 실험으로 비교한다.

PR #179 구조화 로그는 기준 `develop`에 병합되어 `develop merged`로 표시한다. PR #177 이메일 알림은 아직 병합되지 않아 `open PR basis`와 Head `4d4d0e310126982716373eda11d8694928653dac`로 표시한다.

각 Lab은 외부 CDN·font·API·fetch 없이 로컬 파일로 열 수 있다. HTML은 실제 Spring Boot·MySQL·Redis·PortOne·SMTP를 실행하지 않는 학습용 정적 시뮬레이터다.
