-- Seller onboarding and verification.
-- A farmer registers as a normal user (BUYER). They submit a seller_application; an admin reviews it.
-- Approval creates/activates the seller_profile and grants the SELLER role (in one transaction, app layer).
-- Fields mirror the existing frontend application form (features/portals/types.ts SellerApplicationInput).

CREATE TABLE seller_applications (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    application_number VARCHAR(30)   NOT NULL,
    user_id            BIGINT        NOT NULL,
    -- DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, RESUBMISSION_REQUIRED
    status             VARCHAR(30)   NOT NULL,
    full_name          VARCHAR(120)  NOT NULL,
    email              VARCHAR(254)  NOT NULL,
    phone              VARCHAR(20)   NOT NULL,
    farm_name          VARCHAR(150)  NOT NULL,
    farm_description   VARCHAR(2000) NULL,
    farming_type       VARCHAR(40)   NULL,
    crops_grown        VARCHAR(500)  NULL,
    experience         VARCHAR(100)  NULL,
    address_line       VARCHAR(255)  NULL,
    village            VARCHAR(100)  NULL,
    district           VARCHAR(100)  NULL,
    state              VARCHAR(100)  NULL,
    postal_code        VARCHAR(12)   NULL,
    submitted_at       DATETIME(6)   NULL,
    reviewed_at        DATETIME(6)   NULL,
    reviewed_by        BIGINT        NULL,
    -- Reason shown to the applicant on rejection, or the missing information on resubmission requests.
    decision_reason    VARCHAR(1000) NULL,
    -- Internal admin notes; never shown to the applicant.
    admin_notes        VARCHAR(2000) NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_seller_applications PRIMARY KEY (id),
    CONSTRAINT uk_seller_applications_number UNIQUE (application_number),
    CONSTRAINT fk_seller_applications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_seller_applications_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
);

CREATE INDEX idx_seller_applications_user_status ON seller_applications (user_id, status);
CREATE INDEX idx_seller_applications_status_submitted ON seller_applications (status, submitted_at);

-- One seller profile per user, created when the first application is approved.
CREATE TABLE seller_profiles (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    application_id    BIGINT        NULL,
    farm_name         VARCHAR(150)  NOT NULL,
    farm_description  VARCHAR(2000) NULL,
    farming_type      VARCHAR(40)   NULL,
    crops_grown       VARCHAR(500)  NULL,
    experience        VARCHAR(100)  NULL,
    contact_phone     VARCHAR(20)   NULL,
    contact_email     VARCHAR(254)  NULL,
    address_line      VARCHAR(255)  NULL,
    village           VARCHAR(100)  NULL,
    district          VARCHAR(100)  NULL,
    state             VARCHAR(100)  NULL,
    postal_code       VARCHAR(12)   NULL,
    -- ACTIVE or SUSPENDED. Suspension blocks new listings and new purchases; history is preserved.
    seller_status     VARCHAR(20)   NOT NULL,
    status_reason     VARCHAR(1000) NULL,
    approved_at       DATETIME(6)   NOT NULL,
    suspended_at      DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_seller_profiles PRIMARY KEY (id),
    CONSTRAINT uk_seller_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_seller_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_seller_profiles_application FOREIGN KEY (application_id) REFERENCES seller_applications (id)
);

CREATE INDEX idx_seller_profiles_status ON seller_profiles (seller_status);
CREATE INDEX idx_seller_profiles_location ON seller_profiles (state, district);

-- Verification documents. Binary content lives in private storage; only an opaque storage key and
-- metadata are stored here. Resubmitted documents supersede older ones, which are kept for history.
CREATE TABLE seller_documents (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    application_id      BIGINT        NOT NULL,
    -- IDENTITY_PROOF, ADDRESS_PROOF, FARM_AUTHORIZATION
    document_type       VARCHAR(40)   NOT NULL,
    storage_key         VARCHAR(255)  NOT NULL,
    -- Sanitized original name for display only; never used as a filesystem path.
    original_filename   VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    size_bytes          BIGINT        NOT NULL,
    sha256              VARCHAR(64)   NOT NULL,
    -- PENDING, APPROVED, REJECTED
    verification_status VARCHAR(20)   NOT NULL,
    review_note         VARCHAR(1000) NULL,
    reviewed_by         BIGINT        NULL,
    reviewed_at         DATETIME(6)   NULL,
    superseded_at       DATETIME(6)   NULL,
    uploaded_at         DATETIME(6)   NOT NULL,
    CONSTRAINT pk_seller_documents PRIMARY KEY (id),
    CONSTRAINT uk_seller_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_seller_documents_application FOREIGN KEY (application_id) REFERENCES seller_applications (id),
    CONSTRAINT fk_seller_documents_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_seller_documents_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_seller_documents_application_type ON seller_documents (application_id, document_type);

-- Append-only timeline of every application state change and review action.
CREATE TABLE seller_application_history (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    application_id BIGINT        NOT NULL,
    -- SUBMITTED, RESUBMITTED, REVIEW_STARTED, APPROVED, REJECTED, RESUBMISSION_REQUESTED, DOCUMENT_REVIEWED, ...
    action         VARCHAR(40)   NOT NULL,
    from_status    VARCHAR(30)   NULL,
    to_status      VARCHAR(30)   NULL,
    actor_user_id  BIGINT        NULL,
    note           VARCHAR(1000) NULL,
    created_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_seller_application_history PRIMARY KEY (id),
    CONSTRAINT fk_seller_application_history_application FOREIGN KEY (application_id) REFERENCES seller_applications (id),
    CONSTRAINT fk_seller_application_history_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE INDEX idx_seller_application_history_app_time ON seller_application_history (application_id, created_at);
