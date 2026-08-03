package com.bobfull.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import com.bobfull.payment.repository.RefundRepository;
import java.util.List;
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
class AdminRefundQueryServiceTest {

    @Mock private RefundRepository refundRepository;

    @InjectMocks private AdminRefundQueryService service;

    @Test
    void 유효하지_않은_상태필터는_400_예외가_발생한다() {
        Pageable pageable = PageRequest.of(0, 20);

        Throwable result = catchThrowable(() -> service.getRefunds("INVALID", pageable));

        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void COMPLETED_필터가_있으면_상태별로_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Refund> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        given(refundRepository.findAllByStatus(eq(RefundStatus.COMPLETED), any(Pageable.class))).willReturn(emptyPage);

        service.getRefunds("COMPLETED", pageable);

        verify(refundRepository).findAllByStatus(eq(RefundStatus.COMPLETED), any(Pageable.class));
    }
}
