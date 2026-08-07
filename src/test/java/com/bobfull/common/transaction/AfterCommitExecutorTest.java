package com.bobfull.common.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitExecutorTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션_동기화가_없으면_즉시_실행한다() {
        AtomicInteger executions = new AtomicInteger();

        AfterCommitExecutor.run(executions::incrementAndGet);

        assertThat(executions).hasValue(1);
    }

    @Test
    void 트랜잭션_동기화가_있으면_afterCommit에서만_실행한다() {
        AtomicInteger executions = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommitExecutor.run(executions::incrementAndGet);

        assertThat(executions).hasValue(0);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertThat(executions).hasValue(1);
    }
}
