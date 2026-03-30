package projet2.banks.transaction.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import projet2.banks.transaction.entity.TransactionSagaState;

@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    public TransactionEventListener(TransactionService transactionService,
                                    ObjectMapper objectMapper) {
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = {
            "transaction.routed",
            "transaction.accepted",
            "transaction.settled",
            "transaction.rejected",
            "transaction.failed",
            "transaction.expired"
        },
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onTransactionEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.debug("Received event on topic [{}]: {}", topic, payload);
        try {
            JsonNode node = objectMapper.readTree(payload);
            String id = node.path("id").asText(null);
            if (id == null || id.isBlank()) {
                log.warn("Event on topic [{}] has no id field, skipping", topic);
                return;
            }

            TransactionSagaState newStatus = mapTopicToState(topic);
            if (newStatus == null) {
                log.warn("No status mapping for topic [{}], skipping", topic);
                return;
            }

            transactionService.updateStatus(id, newStatus);
            log.info("Transaction [{}] status updated to [{}] via topic [{}]", id, newStatus, topic);

        } catch (Exception e) {
            log.error("Error processing event on topic [{}]: {}", topic, e.getMessage(), e);
        }
    }

    private TransactionSagaState mapTopicToState(String topic) {
        return switch (topic) {
            case "transaction.routed"   -> TransactionSagaState.CREATED_PENDING;
            case "transaction.accepted" -> TransactionSagaState.ACCEPTED;
            case "transaction.settled"  -> TransactionSagaState.SETTLED;
            case "transaction.rejected" -> TransactionSagaState.REJECTED;
            case "transaction.failed"   -> TransactionSagaState.REFUSED;
            case "transaction.expired"  -> TransactionSagaState.EXPIRED;
            default                     -> null;
        };
    }
}
