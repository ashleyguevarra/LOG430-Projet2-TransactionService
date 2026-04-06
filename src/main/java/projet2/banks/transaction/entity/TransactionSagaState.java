package projet2.banks.transaction.entity;

public enum TransactionSagaState {
    CREATED,
    CREATED_PENDING,
    ACCEPTED,
    ACCEPTED_PENDING,
    ACCEPTED_IN_TREATMENT,
    REFUSED,
    SETTLED,
    REJECTED,
    EXPIRED,
    COMPLETED,
    CREATED_DLT
}
