package projet2.banks.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransactionExpirationScheduler.class);

    private final TransactionService transactionService;

    public TransactionExpirationScheduler(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Scheduled(fixedDelayString = "${app.transaction-expiration.poll-ms:60000}")
    public void expireStaleTransactions() {
        int expiredCount = transactionService.expireStaleTransactions(6);
        if (expiredCount > 0) {
            log.info("Expired {} stale transaction(s)", expiredCount);
        }
    }
}
