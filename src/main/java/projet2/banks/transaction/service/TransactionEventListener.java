package projet2.banks.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            "transaction.created",
            "transaction.created.pending",
            "transaction.accepted",
            "transaction.accepted.pending",
            "transaction.accepted.intreatement",
            "transaction.accepted.InTreatment",
            "transaction.settled",
            "transaction.refused",
            "transaction.rejected",
            "transaction.failed",
            "transaction.expired",
            "transaction.routed"
        },
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onTransactionEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.debug("Received event on topic [{}]: {}", topic, payload);
        try {
            JsonNode node = objectMapper.readTree(payload);
            transactionService.handleInboundKafkaEvent(topic, node);
            log.info("Processed inbound event on topic [{}]", topic);

        } catch (Exception e) {
            log.error("Error processing event on topic [{}]: {}", topic, e.getMessage(), e);
        }
    }
}
