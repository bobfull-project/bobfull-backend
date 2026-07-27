package com.bobfull.reservation.port;

/**
 * 예약 결제 준비가 구분해야 하는 목적이다. 실제 Payment 도메인(#91)의 PaymentPurpose와
 * 값 이름을 맞추되, 이 enum은 예약 도메인이 결제 준비 계약을 표현하기 위해 소유한다.
 */
public enum PaymentPurpose {
    CREATE,
    JOIN
}
