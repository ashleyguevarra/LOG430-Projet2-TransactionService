package projet2.banks.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import projet2.banks.transaction.entity.TransactionSagaState;

public record OrchestratorNotifyRequest(
    @JsonProperty("id") String id,
    @JsonProperty("receiverKey") String receiverKey,
    @JsonProperty("senderKey") String senderKey,
    @JsonProperty("amount") double amount,
    @JsonProperty("currentState") TransactionSagaState currentState
) {}
