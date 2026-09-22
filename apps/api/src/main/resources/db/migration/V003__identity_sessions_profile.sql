CREATE TABLE app_users (
 id UUID PRIMARY KEY, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL,
 verified BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE memberships (
 user_id UUID NOT NULL REFERENCES app_users(id), tenant_id UUID NOT NULL REFERENCES tenants(id),
 role TEXT NOT NULL CHECK (role = 'ADMIN'), PRIMARY KEY(user_id, tenant_id), UNIQUE(user_id)
);
CREATE TABLE auth_tokens (
 token_hash TEXT PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id),
 purpose TEXT NOT NULL CHECK (purpose IN ('VERIFY', 'RESET')), expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE mail_outbox (
 id UUID PRIMARY KEY, recipient TEXT NOT NULL, subject TEXT NOT NULL, body TEXT NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 sent_at TIMESTAMPTZ
);
CREATE TABLE public_profiles (
 tenant_id UUID PRIMARY KEY REFERENCES tenants(id), description TEXT NOT NULL DEFAULT '',
 timezone TEXT NOT NULL DEFAULT 'America/Sao_Paulo', updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public_profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_profiles ON public_profiles
 USING (tenant_id::text = current_setting('app.tenant_id', true));
CREATE TABLE spring_session (
 primary_id CHAR(36) PRIMARY KEY, session_id CHAR(36) NOT NULL UNIQUE,
 creation_time BIGINT NOT NULL, last_access_time BIGINT NOT NULL,
 max_inactive_interval INT NOT NULL, expiry_time BIGINT NOT NULL, principal_name VARCHAR(100)
);
CREATE INDEX spring_session_expiry ON spring_session(expiry_time);
CREATE INDEX spring_session_principal ON spring_session(principal_name);
CREATE TABLE spring_session_attributes (
 session_primary_id CHAR(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
 attribute_name VARCHAR(200) NOT NULL, attribute_bytes BYTEA NOT NULL,
 PRIMARY KEY(session_primary_id, attribute_name)
);
GRANT SELECT, INSERT, UPDATE ON app_users, memberships, public_profiles, mail_outbox TO agendou_runtime;
GRANT SELECT, INSERT, DELETE ON auth_tokens TO agendou_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session, spring_session_attributes TO agendou_runtime;
ALTER TABLE subscriptions ADD CONSTRAINT valid_trial CHECK (
 status NOT IN ('TRIAL_ACTIVE', 'TRIAL_EXPIRING', 'TRIAL_EXPIRED_BLOCKED') OR
 (plan_code = 'PREMIUM_TOP' AND trial_started_at IS NOT NULL AND trial_ends_at = trial_started_at + interval '7 days')
);
ALTER TABLE subscriptions ADD CONSTRAINT subscription_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE subscription_events ADD CONSTRAINT event_subscription_tenant
 FOREIGN KEY (tenant_id, subscription_id) REFERENCES subscriptions(tenant_id, id);
