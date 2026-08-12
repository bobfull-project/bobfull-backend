package com.bobfull.outbox.service;

import com.bobfull.outbox.entity.EmailDeliveryStatus;
import com.bobfull.outbox.repository.EmailOutboxDeliveryRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxDeliveryTransactionService {
    private final EmailOutboxDeliveryRepository repository;
    public EmailOutboxDeliveryTransactionService(EmailOutboxDeliveryRepository repository) { this.repository = repository; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(Long id, Instant now) {
        return repository.markSent(id, EmailDeliveryStatus.PENDING, EmailDeliveryStatus.SENT, now) == 1;
    }
}
