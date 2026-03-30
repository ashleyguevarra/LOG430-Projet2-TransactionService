package projet2.banks.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import projet2.banks.transaction.dto.OrchestratorNotifyRequest;

@Component
public class OrchestratorClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);

    private final RestTemplate restTemplate;
    private final String orchestratorUrl;

    public OrchestratorClient(
            RestTemplate restTemplate,
            @Value("${app.services.orchestrator-url}") String orchestratorUrl) {
        this.restTemplate = restTemplate;
        this.orchestratorUrl = orchestratorUrl;
    }

    public void notifyTransactionCreated(OrchestratorNotifyRequest request) {
        String url = orchestratorUrl + "/saga/transaction/created";
        try {
            restTemplate.postForObject(url, request, String.class);
        } catch (Exception e) {
            log.warn("OrchestratorClient: échec notification créée [{}]: {}", request.id(), e.getMessage());
        }
    }
}
