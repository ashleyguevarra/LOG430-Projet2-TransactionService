-- Schéma Transaction Service (v2)
DROP TABLE IF EXISTS outbox_events;
DROP TABLE IF EXISTS transactions;

CREATE TABLE IF NOT EXISTS transactions (
    id             VARCHAR(36)    NOT NULL PRIMARY KEY,
    sender_key     VARCHAR(255)   NOT NULL,
    receiver_key   VARCHAR(255)   NOT NULL,
    participant_sender_id VARCHAR(255)   NOT NULL,
    participant_receiver_id VARCHAR(255) NOT NULL,
    amount         DECIMAL(19,4)  NOT NULL,
    status         VARCHAR(32)    NOT NULL DEFAULT 'CREATED',
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    topic       VARCHAR(128) NOT NULL,
    payload     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published   BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Seed data
INSERT INTO transactions (id, sender_key, receiver_key, participant_sender_id, participant_receiver_id, amount, status)
VALUES ('seed-txn-001', 'key-alice', 'key-charlie', '1', '2', 150.0000, 'CREATED');
