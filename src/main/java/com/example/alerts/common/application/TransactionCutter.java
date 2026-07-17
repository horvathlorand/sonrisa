package com.example.alerts.common.application;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class TransactionCutter {

    private final PlatformTransactionManager transactionManager;

    public <T> T inNewTransaction(Supplier<T> operation) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> operation.get());
    }

    public void inNewTransaction(Runnable operation) {
        inNewTransaction(() -> {
            operation.run();
            return null;
        });
    }
}
