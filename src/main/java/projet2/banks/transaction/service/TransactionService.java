package projet2.banks.transaction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import projet2.banks.transaction.dto.OrchestratorNotifyRequest;
import projet2.banks.transaction.dto.TransactionCreateRequest;
import projet2.banks.transaction.dto.TransactionResponse;
import projet2.banks.transaction.entity.OutboxEvent;
import projet2.banks.transaction.entity.Transaction;
import projet2.banks.transaction.entity.TransactionSagaState;
import projet2.banks.transaction.repository.OutboxEventRepository;
import projet2.banks.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(
            TransactionRepository repository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public List<TransactionResponse> reconcileTransactions(String participantId, LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByParticipantIdAndCreatedAtBetween(participantId, startDate, endDate)
            .stream()
            .map(TransactionResponse::fromEntity)
            .toList();
    }

    public TransactionResponse createTransaction(TransactionCreateRequest request) {
        OrchestratorNotifyRequest notify = saveTransactionAndOutbox(request);
        return getTransaction(notify.id());
    }

    @Transactional
    protected OrchestratorNotifyRequest saveTransactionAndOutbox(TransactionCreateRequest request) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setSenderKey(request.senderKey());
        tx.setReceiverKey(request.receiverKey());
        tx.setParticipantId(request.participantId());
        tx.setAmount(request.amount());
        tx.setStatus(TransactionSagaState.CREATED);
        tx = repository.saveAndFlush(tx);

        OrchestratorNotifyRequest notify = new OrchestratorNotifyRequest(
            tx.getId(),
            tx.getReceiverKey(),
            tx.getSenderKey(),
            tx.getAmount().doubleValue(),
            TransactionSagaState.CREATED
        );
        try {
            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTopic("transaction.created");
            event.setPayload(objectMapper.writeValueAsString(notify));
            event.setPublished(false);
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event for transaction " + tx.getId(), e);
        }

        return notify;
    }

    public TransactionResponse getTransaction(String id) {
        return repository.findById(id)
            .map(TransactionResponse::fromEntity)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Transaction not found: " + id));
    }

    public List<TransactionResponse> getAllTransactions() {
        return repository.findAll().stream()
            .map(TransactionResponse::fromEntity)
            .toList();
    }

    @Transactional
    public TransactionResponse updateStatus(String id, TransactionSagaState newStatus) {
        Transaction tx = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Transaction not found: " + id));
        tx.setStatus(newStatus);
        return TransactionResponse.fromEntity(repository.save(tx));
    }

    @Transactional
    public Transaction createTransactionForTest(TransactionSagaState initialStatus) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setSenderKey("test-sender-key");
        tx.setReceiverKey("test-receiver-key");
        tx.setParticipantId("test-participant-id");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus(initialStatus);
        return repository.saveAndFlush(tx);
    }
}
