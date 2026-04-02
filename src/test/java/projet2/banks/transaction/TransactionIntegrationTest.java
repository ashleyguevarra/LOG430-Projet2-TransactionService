package projet2.banks.transaction;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import projet2.banks.transaction.dto.TransactionCreateRequest;
import projet2.banks.transaction.dto.TransactionResponse;
import projet2.banks.transaction.entity.OutboxEvent;
import projet2.banks.transaction.entity.Transaction;
import projet2.banks.transaction.entity.TransactionSagaState;
import projet2.banks.transaction.repository.OutboxEventRepository;
import projet2.banks.transaction.repository.TransactionRepository;
import projet2.banks.transaction.service.OutboxPublisher;
import projet2.banks.transaction.service.TransactionService;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    topics = {
        "transaction.created",
        "transaction.created.pending",
        "transaction.accepted",
        "transaction.accepted.pending",
        "transaction.accepted.intreatement",
        "transaction.settled",
        "transaction.refused",
        "transaction.rejected",
        "transaction.failed",
        "transaction.expired"
    }
)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:integrationdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.outbox.poll-ms=9999999"
})
@DirtiesContext
class TransactionIntegrationTest {

    @Autowired TransactionService transactionService;
    @Autowired TransactionRepository transactionRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired EmbeddedKafkaBroker embeddedKafka;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void transactionCreatedEvent_createsTransaction_andQueuesCreatedPendingOutboxEvent() {
        String txId = UUID.randomUUID().toString();
        String payload = String.format(
            """
            {
              "id": "%s",
              "senderKey": "KEY-SENDER-001",
              "receiverKey": "KEY-RECEIVER-001",
              "participantSenderKey": "PART-SENDER-001",
                            "participantReceiverKey": "PART-RECEIVER-001",
              "amount": 250.00,
              "currentState": "CREATED",
              "createdTime": "2026-01-01T12:00:00"
            }
            """, txId);

        kafkaTemplate.send("transaction.created", payload);

        awaitStatus(txId, TransactionSagaState.CREATED_PENDING);

        Transaction tx = transactionRepository.findById(txId).orElseThrow();
        assertThat(tx.getSenderKey()).isEqualTo("KEY-SENDER-001");
        assertThat(tx.getReceiverKey()).isEqualTo("KEY-RECEIVER-001");
        assertThat(tx.getParticipantSenderId()).isEqualTo("PART-SENDER-001");
        assertThat(tx.getParticipantReceiverId()).isEqualTo("PART-RECEIVER-001");

        List<OutboxEvent> events = outboxEventRepository.findByPublishedFalse();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("transaction.created.pending");
        assertThat(events.get(0).getPayload()).contains(txId);
    }

    // -----------------------------------------------------------------------
    // 2. OutboxPublisher : publie les événements pending vers Kafka
    // -----------------------------------------------------------------------

    @Test
    void outboxPublisher_publishesPendingEvents_marksAsPublishedAndSendsToKafka() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTopic("transaction.created.pending");
        event.setPayload("{\"id\":\"test-tx-id\",\"currentState\":\"CREATED_PENDING\"}");
        event.setPublished(false);
        outboxEventRepository.save(event);

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-outbox-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
            consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "transaction.created.pending");

        outboxPublisher.publishPendingEvents();

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        OutboxEvent saved = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.isPublished()).isTrue();

        consumer.close();
    }

    // -----------------------------------------------------------------------
    // 3. TransactionEventListener : un event Kafka met à jour le statut en DB
    // -----------------------------------------------------------------------

    @Test
    void transactionEventListener_onTransactionCreatedPending_updatesStatusToCreatedPending() {
        TransactionResponse tx = createTestTransaction("KEY-A", "KEY-B", "50.00");

        kafkaTemplate.send("transaction.created.pending",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.CREATED_PENDING);
    }

    @Test
    void transactionEventListener_onTransactionAccepted_updatesStatusToAcceptedPendingAndQueuesOutbox() {
        TransactionResponse tx = createTestTransaction("KEY-G", "KEY-H", "20.00");

        kafkaTemplate.send("transaction.accepted",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.ACCEPTED_PENDING);

        assertThat(outboxEventRepository.findByPublishedFalse())
            .anyMatch(e -> e.getTopic().equals("transaction.accepted.pending") && e.getPayload().contains(tx.id()));
    }

    @Test
    void transactionEventListener_onTransactionRefused_updatesStatusToRefused() {
        TransactionResponse tx = createTestTransaction("KEY-I", "KEY-J", "40.00");

        kafkaTemplate.send("transaction.refused",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.REFUSED);
    }

    @Test
    void transactionEventListener_onTransactionFailed_legacyTopicStillUpdatesStatusToRefused() {
        TransactionResponse tx = createTestTransaction("KEY-I", "KEY-J", "40.00");

        kafkaTemplate.send("transaction.failed",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.REFUSED);
    }

    @Test
    void transactionEventListener_onTransactionSettled_updatesStatusToSettled() {
        TransactionResponse tx = createTestTransaction("KEY-A", "KEY-B", "75.00");

        kafkaTemplate.send("transaction.settled",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.SETTLED);
    }

    @Test
    void transactionEventListener_onTransactionRejected_updatesStatusToRejected() {
        TransactionResponse tx = createTestTransaction("KEY-C", "KEY-D", "30.00");

        kafkaTemplate.send("transaction.rejected",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.REJECTED);
    }

    @Test
    void transactionEventListener_onTransactionExpired_updatesStatusToExpired() {
        TransactionResponse tx = createTestTransaction("KEY-E", "KEY-F", "10.00");

        kafkaTemplate.send("transaction.expired",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.EXPIRED);
    }

    @Test
    void transactionEventListener_onTransactionAcceptedInTreatmentTypoTopic_updatesStatus() {
        TransactionResponse tx = createTestTransaction("KEY-T1", "KEY-T2", "10.00");

        kafkaTemplate.send("transaction.accepted.intreatement",
            String.format("{\"id\":\"%s\"}", tx.id()));

        awaitStatus(tx.id(), TransactionSagaState.ACCEPTED_IN_TREATMENT);
    }

    // -----------------------------------------------------------------------
    // 4. Requêtes de service : getTransaction / getAllTransactions
    // -----------------------------------------------------------------------

    @Test
    void getTransaction_existingId_returnsTransaction() {
        TransactionResponse created = createTestTransaction("KEY-K", "KEY-L", "60.00");

        TransactionResponse found = transactionService.getTransaction(created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.status()).isEqualTo(TransactionSagaState.CREATED);
        assertThat(found.senderKey()).isEqualTo("KEY-K");
        assertThat(found.receiverKey()).isEqualTo("KEY-L");
    }

    @Test
    void getTransaction_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> transactionService.getTransaction("non-existent-id"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Transaction not found");
    }

    @Test
    void getAllTransactions_returnsAllPersistedTransactions() {
        createTestTransaction("KEY-M", "KEY-N", "10.00");
        createTestTransaction("KEY-O", "KEY-P", "20.00");

        List<TransactionResponse> all = transactionService.getAllTransactions();

        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // 5. Réconciliation (UC08) : filtre par participantId + plage de dates
    // -----------------------------------------------------------------------

    @Test
    void reconcileTransactions_filtersCorrectlyByParticipantAndDateRange() {
        String participantA = "PART-RECONCILE-A";
        String participantB = "PART-RECONCILE-B";

        transactionService.createTransaction(
            new TransactionCreateRequest("KEY-REC-S1", "KEY-REC-R1",
                new BigDecimal("100.00"), participantA, "PART-REC-X"));
        transactionService.createTransaction(
            new TransactionCreateRequest("KEY-REC-S2", "KEY-REC-R2",
                new BigDecimal("200.00"), participantB, "PART-REC-Y"));

        LocalDateTime start = LocalDateTime.now().minusMinutes(5);
        LocalDateTime end   = LocalDateTime.now().plusMinutes(5);

        List<TransactionResponse> results =
            transactionService.reconcileTransactions(participantA, start, end);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).participantSenderId()).isEqualTo(participantA);
    }

    // -----------------------------------------------------------------------
    // 6. Saga flows (logique des endpoints k6 : /settle, /reject, /expire)
    // -----------------------------------------------------------------------

    @Test
    void sagaFlow_settle_transitionsCREATED_ACCEPTED_SETTLED() {
        Transaction tx = transactionService.createTransactionForTest(TransactionSagaState.CREATED);
        transactionService.updateStatus(tx.getId(), TransactionSagaState.ACCEPTED);
        TransactionResponse result = transactionService.updateStatus(tx.getId(), TransactionSagaState.SETTLED);

        assertThat(result.status()).isEqualTo(TransactionSagaState.SETTLED);
    }

    @Test
    void sagaFlow_reject_transitionsCREATED_REFUSED_REJECTED() {
        Transaction tx = transactionService.createTransactionForTest(TransactionSagaState.CREATED);
        transactionService.updateStatus(tx.getId(), TransactionSagaState.REFUSED);
        TransactionResponse result = transactionService.updateStatus(tx.getId(), TransactionSagaState.REJECTED);

        assertThat(result.status()).isEqualTo(TransactionSagaState.REJECTED);
    }

    @Test
    void sagaFlow_expire_transitionsCREATED_EXPIRED() {
        Transaction tx = transactionService.createTransactionForTest(TransactionSagaState.CREATED);
        TransactionResponse result = transactionService.updateStatus(tx.getId(), TransactionSagaState.EXPIRED);

        assertThat(result.status()).isEqualTo(TransactionSagaState.EXPIRED);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TransactionResponse createTestTransaction(String sender, String receiver, String amount) {
        return transactionService.createTransaction(
            new TransactionCreateRequest(sender, receiver, new BigDecimal(amount),
                "id_participant_sender", "id_participant_receiver"));
    }

    private void awaitStatus(String txId, TransactionSagaState expected) {
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(250, TimeUnit.MILLISECONDS)
            .until(() -> transactionRepository.findById(txId)
                .map(t -> t.getStatus() == expected)
                .orElse(false));
    }
}
