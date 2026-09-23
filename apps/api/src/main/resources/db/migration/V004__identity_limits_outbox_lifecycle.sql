-- Authentication infrastructure is global, like app_users and Spring Session.
CREATE TABLE auth_rate_limits (
    bucket_key TEXT NOT NULL,
    window_start BIGINT NOT NULL,
    hits INTEGER NOT NULL CHECK (hits > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bucket_key, window_start)
);
CREATE INDEX auth_rate_limits_expiry ON auth_rate_limits(expires_at);
GRANT SELECT, INSERT, UPDATE, DELETE ON auth_rate_limits TO agendou_runtime;

ALTER TABLE mail_outbox ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'EXPIRED', 'CANCELED'));
ALTER TABLE mail_outbox ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE mail_outbox ADD COLUMN expires_at TIMESTAMPTZ;
ALTER TABLE mail_outbox ADD COLUMN token_hash TEXT;
ALTER TABLE mail_outbox ADD COLUMN user_id UUID REFERENCES app_users(id);
ALTER TABLE mail_outbox ADD COLUMN purpose TEXT CHECK (purpose IN ('VERIFY', 'RESET'));
ALTER TABLE mail_outbox ADD COLUMN last_error_code TEXT;
ALTER TABLE mail_outbox ADD COLUMN last_attempt_at TIMESTAMPTZ;
UPDATE mail_outbox SET status = 'SENT' WHERE sent_at IS NOT NULL;
-- Legacy messages have no reliable token linkage. Do not deliver possibly expired links.
UPDATE mail_outbox SET status = 'EXPIRED', recipient = '', body = '', expires_at = now()
    WHERE sent_at IS NULL;
CREATE INDEX mail_outbox_dispatch ON mail_outbox(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX mail_outbox_token ON mail_outbox(token_hash);
CREATE INDEX auth_tokens_user_purpose ON auth_tokens(user_id, purpose);
CREATE INDEX auth_tokens_expiry ON auth_tokens(expires_at);
