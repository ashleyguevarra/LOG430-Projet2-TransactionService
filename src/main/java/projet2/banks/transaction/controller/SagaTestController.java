package projet2.banks.transaction.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projet2.banks.transaction.dto.TransactionResponse;
import projet2.banks.transaction.entity.Transaction;
import projet2.banks.transaction.entity.TransactionSagaState;
import projet2.banks.transaction.service.TransactionService;

/**
 * Synchronous saga simulation endpoints for k6 load testing.
 *
 * These endpoints do NOT call KeyService or the Orchestrator.
 * They create a transaction directly in the DB and advance it to
 * a terminal state in a single HTTP response.
 *
 * Flows simulated:
 *   /accept → CREATED → ACCEPTED
 *   /settle → CREATED → ACCEPTED → SETTLED
 *   /reject → CREATED → REFUSED  → REJECTED
 *   /expire → CREATED → EXPIRED
 */
@RestController
@RequestMapping("/api/v2/saga/transactions")
public class SagaTestController {

    private final TransactionService transactionService;

    public SagaTestController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/accept")
    public ResponseEntity<TransactionResponse> simulateAccept() {
        Transaction tx = transactionService.createTransactionForTest(TransactionSagaState.CREATED);
        TransactionResponse result = transactionService.updateStatus(tx.getId(), TransactionSagaState.ACCEPTED);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/settle")
    public ResponseEntity<TransactionResponse> simulateSettle() {
        return ResponseEntity.ok(transactionService.simulateSettleInOneTx());
    }

    @GetMapping("/reject")
    public ResponseEntity<TransactionResponse> simulateReject() {
        return ResponseEntity.ok(transactionService.simulateRejectInOneTx());
    }

    @GetMapping("/expire")
    public ResponseEntity<TransactionResponse> simulateExpire() {
        Transaction tx = transactionService.createTransactionForTest(TransactionSagaState.CREATED);
        TransactionResponse result = transactionService.updateStatus(tx.getId(), TransactionSagaState.EXPIRED);
        return ResponseEntity.ok(result);
    }
}
