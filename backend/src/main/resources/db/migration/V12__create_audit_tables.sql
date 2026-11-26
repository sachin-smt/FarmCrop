-- Append-only audit trail for administrative and sensitive marketplace actions
-- (seller approval/rejection, suspensions, product moderation, inventory corrections, refunds...).
-- Never contains passwords, tokens or document contents. The application exposes no update/delete API.

CREATE TABLE audit_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT        NULL,
    -- ADMIN, SELLER, BUYER, SYSTEM
    actor_type    VARCHAR(20)   NOT NULL,
    action        VARCHAR(60)   NOT NULL,
    entity_type   VARCHAR(40)   NOT NULL,
    entity_id     BIGINT        NULL,
    reason        VARCHAR(1000) NULL,
    -- Small JSON document with non-sensitive context (e.g. {"from":"SUBMITTED","to":"APPROVED"}).
    metadata      VARCHAR(4000) NULL,
    ip_address    VARCHAR(45)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at);
CREATE INDEX idx_audit_logs_actor_time ON audit_logs (actor_user_id, created_at);
CREATE INDEX idx_audit_logs_action_time ON audit_logs (action, created_at);
