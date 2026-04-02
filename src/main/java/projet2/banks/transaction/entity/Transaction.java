package projet2.banks.transaction.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "sender_key", nullable = false)
    private String senderKey;

    @Column(name = "receiver_key", nullable = false)
    private String receiverKey;

    @Column(name = "participant_sender_id", nullable = false)
    private String participantSenderId;

    @Column(name = "participant_receiver_id", nullable = false)
    private String participantReceiverId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransactionSagaState status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderKey() { return senderKey; }
    public void setSenderKey(String senderKey) { this.senderKey = senderKey; }

    public String getReceiverKey() { return receiverKey; }
    public void setReceiverKey(String receiverKey) { this.receiverKey = receiverKey; }

    public String getParticipantSenderId() { return participantSenderId; }
    public void setParticipantSenderId(String participantSenderId) { this.participantSenderId = participantSenderId; }

    public String getParticipantReceiverId() { return participantReceiverId; }
    public void setParticipantReceiverId(String participantReceiverId) { this.participantReceiverId = participantReceiverId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public TransactionSagaState getStatus() { return status; }
    public void setStatus(TransactionSagaState status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
