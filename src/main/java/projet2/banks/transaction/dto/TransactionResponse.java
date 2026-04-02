package projet2.banks.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import projet2.banks.transaction.entity.Transaction;
import projet2.banks.transaction.entity.TransactionSagaState;

public record TransactionResponse(
    String id,
    String senderKey,
    String receiverKey,
    BigDecimal amount,
    String participantSenderId,
    String participantReceiverId,
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
            t.getParticipantSenderId(),
            t.getParticipantReceiverId(),
            t.getStatus(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }
}
