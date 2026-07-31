package com.bobfull.payment.service;
import static org.mockito.Mockito.*;
import com.bobfull.payment.entity.*;
import com.bobfull.payment.repository.PaymentRepository;
import java.math.BigDecimal; import java.time.*; import java.util.Optional;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*; import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class) class PaymentExpirationProcessorTest {
 @Mock PaymentRepository repository;
 Payment payment(Instant expires){return Payment.createReady("p",1L,2L,null,PaymentPurpose.CREATE,1,BigDecimal.TEN,expires);}
 @Test void 내부PK_잠금으로_만료READY만_전이한다(){ Instant now=Instant.parse("2026-07-30T00:00:00Z"); Payment p=payment(now); when(repository.findWithLockById(7L)).thenReturn(Optional.of(p)); new PaymentExpirationProcessor(repository,Clock.fixed(now,ZoneOffset.UTC)).expire(7L); verify(repository).findWithLockById(7L); Assertions.assertEquals(PaymentStatus.EXPIRED,p.getStatus()); }
 @Test void 미래_READY는_유지되고_반복은_멱등이다(){ Instant now=Instant.parse("2026-07-30T00:00:00Z"); Payment p=payment(now.plusSeconds(1)); when(repository.findWithLockById(7L)).thenReturn(Optional.of(p)); PaymentExpirationProcessor x=new PaymentExpirationProcessor(repository,Clock.fixed(now,ZoneOffset.UTC)); x.expire(7L); x.expire(7L); Assertions.assertEquals(PaymentStatus.READY,p.getStatus()); verify(repository,times(2)).findWithLockById(7L); }
}
