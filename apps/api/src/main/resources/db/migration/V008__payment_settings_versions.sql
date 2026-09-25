CREATE TABLE payment_settings_versions (
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 version INTEGER NOT NULL CHECK(version>0),
 key_type TEXT NOT NULL CHECK(key_type IN ('CPF','CNPJ','EMAIL','PHONE','RANDOM')),
 pix_key TEXT NOT NULL CHECK(length(pix_key) BETWEEN 1 AND 77),
 recipient_name TEXT NOT NULL CHECK(length(trim(recipient_name)) BETWEEN 2 AND 100),
 payment_instructions TEXT NOT NULL CHECK(length(trim(payment_instructions)) BETWEEN 10 AND 2000),
 cancellation_policy TEXT NOT NULL CHECK(length(trim(cancellation_policy)) BETWEEN 10 AND 4000),
 enabled BOOLEAN NOT NULL,
 actor_id UUID NOT NULL REFERENCES app_users(id),
 change_reason TEXT NOT NULL CHECK(length(trim(change_reason)) BETWEEN 10 AND 500),
 changed_fields TEXT[] NOT NULL,
 correlation_id TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,version),
 FOREIGN KEY(actor_id,tenant_id) REFERENCES memberships(user_id,tenant_id)
);
ALTER TABLE payment_settings_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_settings_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_payment_settings ON payment_settings_versions
 USING(tenant_id::text=current_setting('app.tenant_id',true));
-- Immutable versions form the audit trail and can be referenced by future booking snapshots.
GRANT SELECT,INSERT ON payment_settings_versions TO agendou_runtime;
