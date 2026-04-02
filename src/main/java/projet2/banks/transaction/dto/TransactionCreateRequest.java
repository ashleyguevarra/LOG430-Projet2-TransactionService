package projet2.banks.transaction.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransactionCreateRequest(
    @NotBlank String senderKey,
    @NotBlank String receiverKey,
    @NotNull @DecimalMin(value = "0.01", message = "Amount must be positive") BigDecimal amount,
    @NotBlank String participantSenderId,
    @NotBlank String participantReceiverId
) {}
