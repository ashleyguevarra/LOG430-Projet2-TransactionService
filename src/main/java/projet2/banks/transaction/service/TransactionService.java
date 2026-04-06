package projet2.banks.transaction.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import projet2.banks.transaction.dto.OrchestratorNotifyRequest;
import projet2.banks.transaction.dto.TransactionCreateRequest;
import projet2.banks.transaction.dto.TransactionResponse;
import projet2.banks.transaction.entity.OutboxEvent;
import projet2.banks.transaction.entity.Transaction;
import projet2.banks.transaction.entity.TransactionSagaState;
import projet2.banks.transaction.repository.OutboxEventRepository;
import projet2.banks.transaction.repository.TransactionRepository;

@Service
public class TransactionService {

    private static final List<TransactionSagaState> EXPIRABLE_STATUSES = List.copyOf(
        EnumSet.of(
            TransactionSagaState.CREATED,
            TransactionSagaState.CREATED_PENDING,
            TransactionSagaState.ACCEPTED,
            TransactionSagaState.ACCEPTED_PENDING,
            TransactionSagaState.ACCEPTED_IN_TREATMENT
        )
    );

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
        tx.setParticipantSenderId(request.participantSenderId());
        tx.setParticipantReceiverId(request.participantReceiverId());
        tx.setAmount(request.amount());
        tx.setStatus(TransactionSagaState.CREATED);
        tx = repository.saveAndFlush(tx);

        OrchestratorNotifyRequest notify = new OrchestratorNotifyRequest(
            tx.getId(),
            tx.getReceiverKey(),
            tx.getSenderKey(),
            tx.getAmount().doubleValue(),
            tx.getParticipantSenderId(),
            tx.getParticipantReceiverId(),
            TransactionSagaState.CREATED,
            tx.getCreatedAt() != null ? tx.getCreatedAt() : LocalDateTime.now()
        );
        enqueueOutboxEvent("transaction.created", notify, tx.getId());

        return notify;
    }

    @Transactional
    public void handleInboundKafkaEvent(String topic, JsonNode node) {
        String normalizedTopic = normalizeTopic(topic);
        switch (normalizedTopic) {
            case "transaction.created" -> handleCreatedEvent(node);
            case "transaction.accepted" -> handleAcceptedEvent(node);
            case "transaction.created.pending" -> updateStatus(extractId(node, topic), TransactionSagaState.CREATED_PENDING);
            case "transaction.accepted.pending" -> updateStatus(extractId(node, topic), TransactionSagaState.ACCEPTED_PENDING);
            case "transaction.accepted.intreatment" -> handleInTreatmentEvent(node);
            case "transaction.settled" -> updateStatus(extractId(node, topic), TransactionSagaState.SETTLED);
            case "transaction.refused", "transaction.failed" -> handleRefusedEvent(node);
            case "transaction.rejected" -> updateStatus(extractId(node, topic), TransactionSagaState.REJECTED);
            case "transaction.expired" -> updateStatus(extractId(node, topic), TransactionSagaState.EXPIRED);
            case "transaction.routed" -> updateStatus(extractId(node, topic), TransactionSagaState.CREATED_PENDING);
            default -> throw new IllegalArgumentException("Unsupported topic: " + topic);
        }
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
    public TransactionResponse transitionStatusAndPublish(String id,
                                                          TransactionSagaState newStatus,
                                                          String topicToPublish) {
        Transaction tx = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Transaction not found: " + id));
        tx.setStatus(newStatus);
        tx = repository.saveAndFlush(tx);

        OrchestratorNotifyRequest notify = toNotifyRequest(tx, newStatus);
        enqueueOutboxEvent(topicToPublish, notify, tx.getId());
        return TransactionResponse.fromEntity(tx);
    }

    @Transactional
    public int expireStaleTransactions(int timeoutHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(timeoutHours);
        List<Transaction> staleTransactions = repository.findByStatusInAndCreatedAtBefore(
            EXPIRABLE_STATUSES,
            cutoff
        );

        for (Transaction tx : staleTransactions) {
            transitionStatusAndPublish(tx.getId(), TransactionSagaState.EXPIRED, "transaction.expired");
        }
        return staleTransactions.size();
    }

    @Transactional
    public Transaction createTransactionForTest(TransactionSagaState initialStatus) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setSenderKey("test-sender-key");
        tx.setReceiverKey("test-receiver-key");
        tx.setParticipantSenderId("test-participant-sender-id");
        tx.setParticipantReceiverId("test-participant-receiver-id");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus(initialStatus);
        return repository.saveAndFlush(tx);
    }

    private void handleCreatedEvent(JsonNode node) {
        String id = extractId(node, "transaction.created");
        if (repository.existsById(id)) {
            publishDltEvent(id, "transaction.created.dlt");
        } else {
            Transaction tx = saveCreatedTransaction(node, id);
            transitionStatusAndPublish(tx.getId(), TransactionSagaState.CREATED_PENDING, "transaction.created.pending");
        }
    }

    private void handleAcceptedEvent(JsonNode node) {
        String id = extractId(node, "transaction.accepted");
        Transaction tx = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));

        if (tx.getStatus() != TransactionSagaState.CREATED_PENDING) {
            publishDltEvent(id, "transaction.accepted.dlt");
            return;
        }

        updateStatus(id, TransactionSagaState.ACCEPTED);
        transitionStatusAndPublish(id, TransactionSagaState.ACCEPTED_PENDING, "transaction.accepted.pending");
    }

    private void handleRefusedEvent(JsonNode node) {
        String id = extractId(node, "transaction.refused");
        Transaction tx = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));

        if (tx.getStatus() != TransactionSagaState.CREATED_PENDING) {
            publishDltEvent(id, "transaction.refused.dlt");
            return;
        }

        updateStatus(id, TransactionSagaState.REFUSED);
        transitionStatusAndPublish(id, TransactionSagaState.REJECTED, "transaction.rejected");
    }

    private void handleInTreatmentEvent(JsonNode node) {
        String id = extractId(node, "transaction.accepted.intreatement");
        Transaction tx = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));

        if (tx.getStatus() != TransactionSagaState.ACCEPTED_PENDING) {
            publishDltEvent(id, "transaction.accepted.intreatment.dlt");
            return;
        }

        updateStatus(id, TransactionSagaState.ACCEPTED_IN_TREATMENT);
        transitionStatusAndPublish(id, TransactionSagaState.SETTLED, "transaction.settled");
    }

    private Transaction saveCreatedTransaction(JsonNode node, String id) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setSenderKey(readRequiredString(node, "senderKey"));
        tx.setReceiverKey(readRequiredString(node, "receiverKey"));
        tx.setParticipantSenderId(readParticipantSenderId(node));
        tx.setParticipantReceiverId(readParticipantReceiverId(node));
        tx.setAmount(readAmount(node));
        tx.setStatus(TransactionSagaState.CREATED);
        return repository.saveAndFlush(tx);
    }

    private String readParticipantSenderId(JsonNode node) {
        String participantId = readOptionalString(node, "participantSenderKey");
        if (participantId == null) {
            participantId = readOptionalString(node, "participantSenderId");
        }
        if (participantId == null) {
            throw new IllegalArgumentException("Missing participantSenderKey/participantSenderId in created event");
        }
        return participantId;
    }

    private String readParticipantReceiverId(JsonNode node) {
        String participantId = readOptionalString(node, "participantReceiverKey");
        if (participantId == null) {
            participantId = readOptionalString(node, "participantReceiverId");
        }
        if (participantId == null) {
            throw new IllegalArgumentException("Missing participantReceiverKey/participantReceiverId in created event");
        }
        return participantId;
    }

    private BigDecimal readAmount(JsonNode node) {
        JsonNode amountNode = node.path("amount");
        if (!amountNode.isNumber()) {
            throw new IllegalArgumentException("Missing or invalid amount in created event");
        }
        return amountNode.decimalValue();
    }

    private String readRequiredString(JsonNode node, String fieldName) {
        String value = readOptionalString(node, fieldName);
        if (value == null) {
            throw new IllegalArgumentException("Missing field '" + fieldName + "'");
        }
        return value;
    }

    private String readOptionalString(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    private String extractId(JsonNode node, String topic) {
        String id = readOptionalString(node, "id");
        if (id == null) {
            throw new IllegalArgumentException("Event on topic '" + topic + "' has no id");
        }
        return id;
    }

    private String normalizeTopic(String topic) {
        return topic == null ? "" : topic.toLowerCase(Locale.ROOT).replace("intreatement", "intreatment");
    }

    private OrchestratorNotifyRequest toNotifyRequest(Transaction tx, TransactionSagaState state) {
        return new OrchestratorNotifyRequest(
            tx.getId(),
            tx.getReceiverKey(),
            tx.getSenderKey(),
            tx.getAmount().doubleValue(),
            tx.getParticipantSenderId(),
            tx.getParticipantReceiverId(),
            state,
            tx.getCreatedAt() != null ? tx.getCreatedAt() : LocalDateTime.now()
        );
    }

    private void publishDltEvent(String id, String topic) {
        Transaction tx = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
        OrchestratorNotifyRequest notify = toNotifyRequest(tx, tx.getStatus());
        enqueueOutboxEvent(topic, notify, id);
    }

    private void enqueueOutboxEvent(String topic, OrchestratorNotifyRequest notify, String transactionId) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTopic(topic);
            event.setPayload(objectMapper.writeValueAsString(notify));
            event.setPublished(false);
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event for transaction " + transactionId, e);
        }
    }
}
