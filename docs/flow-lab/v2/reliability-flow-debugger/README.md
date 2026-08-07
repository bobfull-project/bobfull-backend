# Reliability Flow Debugger

6개 Chapter는 전체 사용자 여정을 넓게 보여 주고, 동시성·환불·채팅·시간 경계만 실제 메서드와 상태·락·트랜잭션까지 확장한다. Ch3는 V1 결제 완료 Debugger의 세부 단계를 재복제하지 않고 V2 연결점만 다룬다.

기준: `develop merged` `a0d519561fb7ebe20eea8a08649f4f08f2583987`. Ch3·Ch6 이메일 알림은 현재 실제 `open PR basis` PR #177 Head `0ae7796471d236b6e62102eeb6bd662a56d324d8`이다. CREATE 접수·JOIN 참여, 모집 마감 확정·취소 이메일은 Event → AFTER_COMMIT → 전용 `@Async` Executor → SMTP로 이어진다. `AFTER_COMMIT`은 커밋 경계이고 `@Async`가 호출 스레드와 SMTP를 분리한다.

로컬에서 `index.html`을 열어 실행·이전·다음·자동 실행·일시 정지·초기화를 사용할 수 있다.
