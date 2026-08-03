package com.bobfull.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminPaymentQueryServiceTest {

    @Mock private PaymentRepository paymentRepository;

    @InjectMocks private AdminPaymentQueryService service;

    @Test
    void EXPIRED_상태필터는_허용하지_않는다() {
        Pageable pageable = PageRequest.of(0, 20);

        Throwable result = catchThrowable(() -> service.getPayments("EXPIRED", pageable));

        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 필터가_없으면_전체_결제를_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<com.bobfull.payment.entity.Payment> emptyPage = new PageImpl<>(java.util.List.of(), pageable, 0);
        given(paymentRepository.findAll(any(Pageable.class))).willReturn(emptyPage);

        service.getPayments(null, pageable);

        verify(paymentRepository).findAll(any(Pageable.class));
    }

    @Test
    void PAID_필터가_있으면_상태별로_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<com.bobfull.payment.entity.Payment> emptyPage = new PageImpl<>(java.util.List.of(), pageable, 0);
        given(paymentRepository.findAllByStatus(eq(PaymentStatus.PAID), any(Pageable.class))).willReturn(emptyPage);

        service.getPayments("PAID", pageable);

        verify(paymentRepository).findAllByStatus(eq(PaymentStatus.PAID), any(Pageable.class));
    }
}
