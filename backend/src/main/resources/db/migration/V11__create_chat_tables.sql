-- AI assistant conversations for buyers and sellers. Provider API keys are never stored here;
-- provider/model columns only record which (configured) backend produced a reply.

CREATE TABLE chat_conversations (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    -- BUYER or SELLER: selects the assistant's context and permitted data.
    context_role    VARCHAR(20)  NOT NULL,
    title           VARCHAR(150) NULL,
    -- ACTIVE, ARCHIVED
    status          VARCHAR(20)  NOT NULL,
    language        VARCHAR(10)  NOT NULL DEFAULT 'en',
    last_message_at DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_chat_conversations PRIMARY KEY (id),
    CONSTRAINT fk_chat_conversations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_conversations_role CHECK (context_role IN ('BUYER', 'SELLER'))
);

CREATE INDEX idx_chat_conversations_user_status ON chat_conversations (user_id, status, updated_at);

CREATE TABLE chat_messages (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT        NOT NULL,
    -- USER, ASSISTANT, SYSTEM
    sender_type     VARCHAR(20)   NOT NULL,
    content         VARCHAR(8000) NOT NULL,
    provider        VARCHAR(40)   NULL,
    model           VARCHAR(80)   NULL,
    token_count     INT           NULL,
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_messages_sender CHECK (sender_type IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_chat_messages_conversation_time ON chat_messages (conversation_id, created_at);
