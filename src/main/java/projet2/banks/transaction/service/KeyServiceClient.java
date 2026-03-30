package projet2.banks.transaction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import projet2.banks.transaction.dto.KeyResolvedResponse;

@Component
public class KeyServiceClient {

    private final RestTemplate restTemplate;
    private final String keyServiceUrl;

    public KeyServiceClient(
            RestTemplate restTemplate,
            @Value("${app.services.key-service-url}") String keyServiceUrl) {
        this.restTemplate = restTemplate;
        this.keyServiceUrl = keyServiceUrl;
    }

    public KeyResolvedResponse resolveKey(String key) {
        String url = keyServiceUrl + "/api/v2/keys/resolve/" + key;
        try {
            return restTemplate.getForObject(url, KeyResolvedResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Key not found: " + key);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "KeyService unavailable: " + e.getMessage());
        }
    }
}
