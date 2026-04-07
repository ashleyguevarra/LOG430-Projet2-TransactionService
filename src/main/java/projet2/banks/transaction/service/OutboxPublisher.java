package projet2.banks.transaction.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import projet2.banks.transaction.entity.OutboxEvent;
import projet2.banks.transaction.repository.OutboxEventRepository;

@Component
public class OutboxPublisher {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    // Timeout par envoi Kafka (ms) — au-dela, l'event est considere en echec
    private static final long SEND_TIMEOUT_MS = 5000;

    // Limite du batch par cycle
    private static final int BATCH_LIMIT = 200;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            Optional<Tracer> tracerOpt) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:5000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByPublishedFalse();
        if (pending.isEmpty()) return;

        // Traite par batch pour ne pas saturer le producteur Kafka
        List<OutboxEvent> batch = pending.size() > BATCH_LIMIT
                ? pending.subList(0, BATCH_LIMIT)
                : pending;

        log.debug("Outbox: {} event(s) pending ({} in batch)", pending.size(), batch.size());

        List<String> succeededIds = sendBatchParallel(batch);

        if (!succeededIds.isEmpty()) {
            markPublished(succeededIds);
            io.micrometer.core.instrument.Counter.builder("saga.outbox.published.total")
                    .description("Nombre d'evenements outbox publies sur Kafka")
                    .register(meterRegistry)
                    .increment(succeededIds.size());
        }

        int failed = batch.size() - succeededIds.size();
        if (failed > 0) {
            log.warn("Outbox: {}/{} events failed to publish — will retry", failed, batch.size());
        } else {
            log.debug("Outbox: {}/{} events published", succeededIds.size(), batch.size());
        }
    }

    /**
     * Envoie tous les events du batch en parallele.
     * Chaque send est non-bloquant — on attend la completion de tous via allOf().
     * Retourne la liste des IDs publies avec succes.
     */
    private List<String> sendBatchParallel(List<OutboxEvent> events) {
        // Capture les resultats : index -> id si succes, null si echec
        @SuppressWarnings("unchecked")
        CompletableFuture<String>[] futures = new CompletableFuture[events.size()];

        for (int i = 0; i < events.size(); i++) {
            final OutboxEvent event = events.get(i);
            final int idx = i;
            futures[idx] = new CompletableFuture<>();

            try {
                kafkaTemplate.send(event.getTopic(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                futures[idx].complete(event.getId());
                            } else {
                                log.warn("Outbox send failed (async) for event {} on topic {}: {}",
                                        event.getId(), event.getTopic(), ex.getMessage());
                                futures[idx].complete(null);
                            }
                        });
            } catch (Exception ex) {
                log.warn("Outbox send failed (sync) for event {} on topic {}: {}",
                        event.getId(), event.getTopic(), ex.getMessage());
                futures[idx].complete(null);
            }
        }

        // Attend tous les futures avec timeout global
        try {
            CompletableFuture.allOf(futures).get(SEND_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Outbox: batch send timed out after {}ms — collecting partial results", SEND_TIMEOUT_MS * 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.warn("Outbox: unexpected error waiting for Kafka ACKs: {}", e.getMessage());
        }

        // Collecte les succes
        List<String> succeeded = new ArrayList<>(events.size());
        for (CompletableFuture<String> f : futures) {
            if (f.isDone() && !f.isCancelled() && !f.isCompletedExceptionally()) {
                try {
                    String id = f.getNow(null);
                    if (id != null) succeeded.add(id);
                } catch (Exception ignored) {}
            }
        }
        return succeeded;
    }

    // Une seule requete SQL UPDATE ... WHERE id IN (...) au lieu de N SELECT + N UPDATE.
    // La transaction est geree par @Transactional sur markPublishedByIds dans le repository.
    private void markPublished(List<String> ids) {
        outboxEventRepository.markPublishedByIds(ids);
    }
}
