# 밥풀 API Specification — 확정 계약 요약본

> 상세 요청·응답 필드는 DTO와 ERD 구현 Issue에서 조정할 수 있다.  
> 이 문서는 승인된 공통 계약과 전체 HTTP API 목록을 관리한다.

## 1. 공통 계약

- Base URL: `/api`
- 기능 단계는 `[V1]·[V2]·[V3]`로 구분하지만 URL에는 버전을 넣지 않는다.
- 일반 사용자 역할: `MEMBER`
- 사장님 역할: `OWNER`
- 관리자 역할: `ADMIN`

### 성공 응답

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

### 실패 응답

```json
{
  "success": false,
  "message": "에러 메시지",
  "code": "ERROR_CODE"
}
```

- 성공 응답에는 `code`를 넣지 않는다.
- 실패 응답에는 `data`를 넣지 않는다.
- 역할 부족은 `ACCESS_DENIED`를 사용한다.
- 타인 리소스 접근은 구현 도메인별 접근 거부 코드를 사용한다.
- Actuator의 `/actuator/prometheus`, `/actuator/health`는 공통 응답으로 감싸지 않는다.

## 2. 주요 상태

```text
ReservationStatus: RECRUITING, CONFIRMED, CANCELLED, CLOSED
RecruitmentStatus: OPEN, CLOSED
ParticipationStatus: RESERVED, NO_SHOW, CANCELLED
PaymentStatus: READY, PAID, FAILED, CANCELLED
RefundStatus: REQUESTED, PROCESSING, COMPLETED, FAILED
```

## 3. 테이블·예약 계약

- 합석 테이블 정원: `2·4·6·8명`
- 확정 기준: `2→2명`, `4→3명`, `6→5명`, `8→7명`
- 결제 금액: `partySize × 1인당 예약금`
- 한 사용자의 `partySize`를 하나의 참여 단위로 관리한다.
- 정상 방문자는 `RESERVED`를 유지하며 `VISITED`를 사용하지 않는다.
- 예약 상태와 모집 상태를 분리한다.

## 4. PortOne 결제 계약

```text
예약 또는 추가 참여 결제 준비
→ 좌석 10분 임시 선점
→ Payment READY와 paymentId 생성
→ 프론트 PortOne 결제
→ 서버가 PortOne 결제 상태·금액·통화 검증
→ 성공 시 Payment PAID와 예약·참여 반영
→ 실패·만료 시 Payment FAILED와 좌석 해제
```

- `paymentId`를 결제 식별자로 통일한다.
- `paymentOrderId`, `paymentKey`를 서비스 API 계약으로 사용하지 않는다.
- 완료 검증 API와 PortOne 웹훅을 함께 사용하며 중복 실행은 멱등하게 처리한다.
- 환불 완료 시 `RefundStatus.COMPLETED`, `PaymentStatus.CANCELLED`를 반영한다.
- 사용자가 결제 취소 API를 직접 호출하지 않는다.

## 5. V2 채팅·관리자 범위

- 예약별 채팅방 1개
- 최초 예약자와 결제 완료 참여자만 이용
- 사장님·관리자는 예약 채팅에서 제외
- 관리자 V2는 현황과 통계 조회 중심
- 회원 상태 변경, 식당 상태 변경, 비정상 예약 강제 취소는 제외

## 6. API 목록 요약

- 실제 HTTP API 수: **87개**
- API가 아닌 기능은 V2·V3 내부 구현 정책으로 별도 정리한다.

| 기능 분류 | 버전 | 기능명 | Method | Path | 담당자 |
|---|---|---|---|---|---|
| 공통 | V3 | API 모니터링 | `GET` | `/actuator/prometheus` | 김홍기 |
| 공통 | V3 | 애플리케이션 상태 확인 | `GET` | `/actuator/health` | 김홍기 |
| 회원·인증 | V1 | 일반 사용자 회원가입 | `POST` | `/api/auth/signup/users` | 정용태 |
| 회원·인증 | V1 | 사장님 회원가입 | `POST` | `/api/auth/signup/owners` | 정용태 |
| 회원·인증 | V1 | 로그인 | `POST` | `/api/auth/login` | 정용태 |
| 회원·인증 | V1 | 내 정보 조회 | `GET` | `/api/members/me` | 정용태 |
| 회원·인증 | V2 | 내 정보 수정 | `PATCH` | `/api/members/me` | 정용태 |
| 회원·인증 | V2 | 로그아웃 | `POST` | `/api/auth/logout` | 정용태 |
| 회원·인증 | V2 | 토큰 재발급 | `POST` | `/api/auth/reissue` | 정용태 |
| 회원·인증 | V2 | 회원 탈퇴 | `DELETE` | `/api/members/me` | 정용태 |
| 식당 관리 | V1 | 식당 등록 | `POST` | `/api/owner/restaurants` | 정용태 |
| 식당 관리 | V1 | 내 식당 목록 조회 | `GET` | `/api/owner/restaurants` | 정용태 |
| 식당 관리 | V1 | 내 식당 상세 조회 | `GET` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V1 | 식당 정보 수정 | `PATCH` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V1 | 사용자용 식당 목록 조회 | `GET` | `/api/restaurants` | 정용태 |
| 식당 관리 | V1 | 사용자용 식당 상세 조회 | `GET` | `/api/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V2 | 식당 운영 상태 변경 | `PATCH` | `/api/owner/restaurants/{restaurantId}/status` | 정용태 |
| 식당 관리 | V2 | 식당 삭제 | `DELETE` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 검색 | V1 | 식당 동적 검색 | `GET` | `/api/restaurants/search` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 등록 | `POST` | `/api/owner/restaurants/{restaurantId}/tables` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/tables` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 상세 조회 | `GET` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 수정 | `PATCH` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 테이블 | V2 | 합석 테이블 상태 변경 | `PATCH` | `/api/owner/tables/{tableId}/status` | 김홍기 |
| 합석 테이블 | V2 | 합석 테이블 삭제 | `DELETE` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 회차 | V1 | 합석 회차 등록 | `POST` | `/api/owner/tables/{tableId}/dining-sessions` | 김홍기 |
| 합석 회차 | V1 | 사장님용 회차 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/dining-sessions` | 김홍기 |
| 합석 회차 | V1 | 사장님용 회차 상세 조회 | `GET` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 합석 회차 | V1 | 사용자용 예약 가능 회차 조회 | `GET` | `/api/restaurants/{restaurantId}/dining-sessions` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 수정 | `PATCH` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 삭제 | `DELETE` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 일괄 등록 | `POST` | `/api/owner/tables/{tableId}/dining-sessions/batch` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 상태 변경 | `PATCH` | `/api/owner/dining-sessions/{sessionId}/status` | 김홍기 |
| 예약 생성 | V1 | 최초 예약 가능 여부 확인 | `GET` | `/api/dining-sessions/{sessionId}/reservation-availability` | 배지현 |
| 예약 생성 | V1 | 최초 예약 결제 준비 | `POST` | `/api/dining-sessions/{sessionId}/reservations/prepare` | 배지현 |
| 예약 참여 | V1 | 추가 참여 가능 여부 확인 | `GET` | `/api/reservations/{reservationId}/join-availability` | 배지현 |
| 예약 참여 | V1 | 예약 추가 참여 결제 준비 | `POST` | `/api/reservations/{reservationId}/participations/prepare` | 배지현 |
| 모집 예약 검색 | V1 | 참여 가능한 예약 검색 | `GET` | `/api/reservations/search` | 김홍기 |
| 예약 조회 | V1 | 예약 상세 조회 | `GET` | `/api/reservations/{reservationId}` | 배지현 |
| 예약 조회 | V1 | 내 예약 목록 조회 | `GET` | `/api/members/me/reservations` | 배지현 |
| 예약 조회 | V1 | 내 예약 상세 조회 | `GET` | `/api/members/me/reservations/{reservationId}` | 배지현 |
| 예약 조회 | V1 | 내 참여 정보 조회 | `GET` | `/api/reservations/{reservationId}/participations/me` | 배지현 |
| 예약 조회 | V1 | 예약 참여자 목록 조회 | `GET` | `/api/reservations/{reservationId}/participations` | 배지현 |
| 예약 조회 | V2 | 예약 상태 변경 이력 조회 | `GET` | `/api/reservations/{reservationId}/histories` | 배지현 |
| 모집 관리 | V1 | 추가 모집 수동 마감 | `POST` | `/api/reservations/{reservationId}/recruitment/close` | 배지현 |
| 모집 관리 | V1 | 모집 상태 조회 | `GET` | `/api/reservations/{reservationId}/recruitment` | 배지현 |
| 예약 취소 | V2 | 취소 예상 결과 조회 | `GET` | `/api/reservations/{reservationId}/participations/me/cancellation-preview` | 배지현 |
| 예약 취소 | V2 | 내 예약 참여 취소 | `POST` | `/api/reservations/{reservationId}/participations/me/cancel` | 배지현 |
| 사장님 예약 관리 | V1 | 식당별 예약 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/reservations` | 배지현 |
| 사장님 예약 관리 | V1 | 사장님용 예약 상세 조회 | `GET` | `/api/owner/reservations/{reservationId}` | 배지현 |
| 사장님 예약 관리 | V1 | 예약 참여자 목록 조회 | `GET` | `/api/owner/reservations/{reservationId}/participations` | 배지현 |
| 사장님 예약 관리 | V2 | 식당 귀책 예약 취소 | `POST` | `/api/owner/reservations/{reservationId}/cancel` | 배지현 |
| 사장님 예약 관리 | V2 | 시간대별 예약 현황 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/reservation-calendar` | 배지현 |
| 결제 | V1 | 결제 완료 검증 | `POST` | `/api/payments/{paymentId}/complete` | 김현승 |
| 결제 | V1 | PortOne 결제 웹훅 | `POST` | `/api/webhooks/portone` | 김현승 |
| 결제 | V1 | 내 결제 목록 조회 | `GET` | `/api/members/me/payments` | 김현승 |
| 결제 | V1 | 결제 상세 조회 | `GET` | `/api/payments/{paymentId}` | 김현승 |
| 결제 | V2 | 결제 상태 조회 | `GET` | `/api/payments/{paymentId}/status` | 김현승 |
| 결제 | V3 | 결제 실패 재검증 | `POST` | `/api/admin/payments/{paymentId}/retry` | 김현승 |
| 환불 | V1 | 내 환불 목록 조회 | `GET` | `/api/members/me/refunds` | 김현승 |
| 환불 | V1 | 환불 상세 조회 | `GET` | `/api/refunds/{refundId}` | 김현승 |
| 환불 | V2 | 환불 상태 조회 | `GET` | `/api/refunds/{refundId}/status` | 김현승 |
| 환불 | V3 | 실패 환불 목록 조회 | `GET` | `/api/admin/refunds/failed` | 김현승 |
| 환불 | V3 | 실패 환불 재처리 | `POST` | `/api/admin/refunds/{refundId}/retry` | 김현승 |
| 노쇼 | V2 | 노쇼 처리 대상 참여자 조회 | `GET` | `/api/owner/reservations/{reservationId}/participations/no-show-candidates` | 정용태 |
| 노쇼 | V2 | 참여자 노쇼 처리 | `POST` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | 정용태 |
| 노쇼 | V2 | 노쇼 처리 해제 | `DELETE` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | 정용태 |
| 노쇼 | V2 | 예약별 노쇼 이력 조회 | `GET` | `/api/owner/reservations/{reservationId}/no-show-histories` | 정용태 |
| 노쇼 | V2 | 식당 노쇼 현황 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/no-shows` | 정용태 |
| 정산 | V1 | 지급 예정 금액 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/expected` | 김현승 |
| 정산 | V1 | 예약별 지급 예정 내역 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/reservations` | 김현승 |
| 정산 | V1 | 예약별 지급 예정 상세 조회 | `GET` | `/api/owner/settlements/reservations/{reservationId}` | 김현승 |
| 정산 | V2 | 기간별 결제·환불 요약 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/summary` | 김현승 |
| 정산 | V3 | 정산 데이터 재집계 | `POST` | `/api/admin/settlements/recalculate` | 김현승 |
| 관리자 | V2 | 회원 목록 조회 | `GET` | `/api/admin/members` | 정용태 |
| 관리자 | V2 | 회원 상세 조회 | `GET` | `/api/admin/members/{memberId}` | 정용태 |
| 관리자 | V2 | 식당 목록 조회 | `GET` | `/api/admin/restaurants` | 정용태 |
| 관리자 | V2 | 식당 상세 조회 | `GET` | `/api/admin/restaurants/{restaurantId}` | 정용태 |
| 관리자 | V2 | 전체 예약 현황 조회 | `GET` | `/api/admin/reservations` | 정용태 |
| 관리자 | V2 | 전체 결제 현황 조회 | `GET` | `/api/admin/payments` | 정용태 |
| 관리자 | V2 | 전체 환불 현황 조회 | `GET` | `/api/admin/refunds` | 정용태 |
| 관리자 | V2 | 전체 노쇼 현황 조회 | `GET` | `/api/admin/no-shows` | 정용태 |
| 관리자 | V2 | 전체 운영 지표 조회 | `GET` | `/api/admin/statistics/overview` | 정용태 |
| 관리자 | V2 | 식당별 예약 성사율 조회 | `GET` | `/api/admin/statistics/restaurants` | 정용태 |
| 관리자 | V2 | 사용자별 노쇼율 조회 | `GET` | `/api/admin/statistics/members/no-show-rates` | 정용태 |
| 예약 참여자 채팅 | V2 | 예약 채팅방 조회 | `GET` | `/api/reservations/{reservationId}/chat-room` | 김현승 |
| 예약 참여자 채팅 | V2 | 채팅 메시지 목록 조회 | `GET` | `/api/chat-rooms/{chatRoomId}/messages` | 김현승 |

## 7. 구현 Issue에서 결정할 사항

아래 항목은 현재 확정 정책과 충돌하지 않으며, 실제 구현 시 세부 계약을 정한다.

- PortOne 공식 SDK·API 버전에 따른 웹훅 Payload와 서명 검증 코드
- 좌석 임시 선점 저장소와 만료 처리 방식
- 결제 완료 검증과 웹훅 동시 실행에 대한 락·트랜잭션·멱등성 구현
- 도메인별 타인 리소스 접근 거부 에러 코드 이름
- 채팅 STOMP 경로, 메시지 보관 기간과 종료 후 조회 정책
- 상세 DTO 필드와 ERD 컬럼
