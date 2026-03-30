package projet2.banks.transaction.dto;

import projet2.banks.transaction.entity.Transaction;
import projet2.banks.transaction.entity.TransactionSagaState;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
    String id,
    String senderKey,
    String receiverKey,
    BigDecimal amount,
    String participantId,
    TransactionSagaState status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static TransactionResponse fromEntity(Transaction t) {
        return new TransactionResponse(
            t.getId(),
            t.getSenderKey(),
            t.getReceiverKey(),
            t.getAmount(),
            t.getParticipantId(),
            t.getStatus(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }
}
