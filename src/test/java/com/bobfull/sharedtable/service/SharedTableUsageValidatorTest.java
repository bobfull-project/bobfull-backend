package com.bobfull.sharedtable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.SharedTableErrorCode;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SharedTableUsageValidatorTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Test
    void 활성_회차가_연결된_합석_테이블은_삭제할_수_없다() {
        // given
        given(timeSlotRepository.existsBySharedTableIdAndDeletedAtIsNull(100L)).willReturn(true);
        SharedTableUsageValidator validator = new SharedTableUsageValidator(timeSlotRepository);

        // when
        Throwable result = catchThrowable(() -> validator.validateDeletionAllowed(100L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(SharedTableErrorCode.TABLE_HAS_DINING_SESSION);
    }
}
