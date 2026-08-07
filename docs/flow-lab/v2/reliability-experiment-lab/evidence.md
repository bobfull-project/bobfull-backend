# Experiment Lab Evidence

| Experiment | 실제 채택 근거 | Test | 비교용 가상 대안의 의미 | 한계 |
|---|---|---|---|---|
| 마지막 좌석 | `ReservationPreparationService.prepare`, Reservation → TimeSlot 잠금, develop `a0d5195` | `ReservationPreparationConcurrencyIntegrationTest` | 락 없음·단순 잠금은 결과 비교 모델 | 락 wait·K6 수치 없음 |
| 취소·환불 | `ReservationCancellationService`, `RefundCompletionService`, develop `a0d5195` | `RefundTransactionIntegrationTest`, refund service tests | 긴 Transaction·전체 일괄 처리는 비교 모델 | 운영 PortOne E2E·자동 재환불 없음 |
| ChatRoom | `ChatRoomCreationEventListener.handle`, AFTER_COMMIT, develop `a0d5195` | `ChatRoomCreationEventListenerTest` | 핵심 트랜잭션 내부 저장은 비교 모델 | 조회 시 복구를 제외한 운영 재처리 없음 |
| 시간 경계 | `ReservationClosingProcessor`, `ChatMessageCommandService`, develop `a0d5195` | closing/chat command tests | Scheduler 완료 뒤 차단은 비교 모델 | 스케줄러 대량 지연 실측 없음 |
