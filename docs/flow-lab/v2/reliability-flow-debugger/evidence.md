# Flow Debugger Evidence

| Scenario | Evidence type / SHA | 실제 Class · Method | 관련 Test | 확인 결과 · 한계 |
|---|---|---|---|---|
| Ch1 Refresh Token | develop merged `a0d5195` | `AuthService.reissue`, `RefreshTokenStore.rotate` | `AuthServiceTest`, `RefreshTokenStoreIntegrationTest` | Redis TTL·회전 구현. 실제 Redis 통합 테스트는 환경 변수 선택 실행 |
| Ch2 좌석 경쟁 | develop merged `a0d5195` | `ReservationPreparationService.prepare`, `findReservationWithLockOrThrow` | `ReservationPreparationConcurrencyIntegrationTest` | Reservation → TimeSlot 고정 락 순서·가용 인원 재확인 |
| Ch3 완료 확정 | develop merged `a0d5195` | `PaymentCompletionTransactionService.complete`, `ReservationConfirmationService.confirm` | `PaymentCompletionIdempotencyIntegrationTest`, `PaymentReservationConfirmationTransactionIntegrationTest` | Payment·Reservation 확정 원자성 |
| Ch4 취소·환불 | develop merged `a0d5195` | `ReservationCancellationService.cancel`, `RefundCompletionService` | 취소/환불 Service·Integration Test | 외부 I/O는 커밋 뒤, 부분 성공 보존. 운영 PortOne 환불 E2E는 미확인 |
| Ch5 ChatRoom/STOMP | develop merged `a0d5195` | `ChatRoomCreationEventListener.handle`, `ChatMessageCommandService.send` | Chat listener/interceptor/command tests | AFTER_COMMIT 생성·종료 경계 차단. 실제 STOMP E2E는 제한 |
| Ch6 마감·CLOSED·노쇼·로그 | develop merged `a0d5195` | `RecruitmentDeadlineCancellationService`, `ReservationClosingProcessor`, `NoShowService` | scheduler/closing/no-show integration tests | PR #179 구조화 로그도 merged. Grafana 등은 미구현 |
| Ch6 이메일 | open PR basis #177 `4d4d0e3` | `ReservationNotificationService`, `SmtpReservationNotificationAdapter` | PR #177의 관련 테스트 11건 | 아직 develop 미병합, SMTP 최신 Head 실연동은 제한 |
