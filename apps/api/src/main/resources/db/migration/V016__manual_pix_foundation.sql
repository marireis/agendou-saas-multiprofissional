CREATE TABLE payment_intents (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, booking_id uuid NOT NULL,
 provider text NOT NULL CHECK(provider='MANUAL_PIX'),
 status text NOT NULL CHECK(status IN ('AWAITING_PAYMENT','EXPIRED')),
 amount_due_cents bigint NOT NULL CHECK(amount_due_cents>0),
 deadline_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,booking_id),
 FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id)
);
INSERT INTO payment_intents(id,tenant_id,booking_id,provider,status,amount_due_cents,deadline_at)
 SELECT gen_random_uuid(),tenant_id,id,'MANUAL_PIX',CASE WHEN status='EXPIRED' OR expires_at<=now() THEN 'EXPIRED' ELSE 'AWAITING_PAYMENT' END,deposit_cents,expires_at FROM bookings;
CREATE TABLE payment_transactions (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,intent_id uuid NOT NULL,
 amount_cents bigint NOT NULL CHECK(amount_cents>0),bank_reference text NOT NULL CHECK(length(trim(bank_reference)) BETWEEN 1 AND 200),
 received_at timestamptz NOT NULL,recorded_by uuid NOT NULL REFERENCES app_users(id),created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,id),UNIQUE(tenant_id,bank_reference),FOREIGN KEY(tenant_id,intent_id) REFERENCES payment_intents(tenant_id,id)
);
CREATE TABLE payment_evidence (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,intent_id uuid NOT NULL,
 object_key text NOT NULL UNIQUE,sha256 text NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 media_type text NOT NULL CHECK(media_type IN ('image/jpeg','image/png','application/pdf')),
 size_bytes bigint NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880),created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,intent_id) REFERENCES payment_intents(tenant_id,id)
);
CREATE TABLE payment_refunds (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,transaction_id uuid NOT NULL,
 amount_cents bigint NOT NULL CHECK(amount_cents>0),reason text NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 1000),
 bank_reference text NOT NULL CHECK(length(trim(bank_reference)) BETWEEN 1 AND 200),
 refunded_at timestamptz NOT NULL,recorded_by uuid NOT NULL REFERENCES app_users(id),created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,bank_reference),FOREIGN KEY(tenant_id,transaction_id) REFERENCES payment_transactions(tenant_id,id)
);
ALTER TABLE payment_intents ENABLE ROW LEVEL SECURITY; ALTER TABLE payment_intents FORCE ROW LEVEL SECURITY;
ALTER TABLE payment_transactions ENABLE ROW LEVEL SECURITY; ALTER TABLE payment_transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE payment_evidence ENABLE ROW LEVEL SECURITY; ALTER TABLE payment_evidence FORCE ROW LEVEL SECURITY;
ALTER TABLE payment_refunds ENABLE ROW LEVEL SECURITY; ALTER TABLE payment_refunds FORCE ROW LEVEL SECURITY;
CREATE POLICY payment_intent_tenant ON payment_intents USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY payment_transaction_tenant ON payment_transactions USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY payment_evidence_tenant ON payment_evidence USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY payment_refund_tenant ON payment_refunds USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON payment_intents TO agendou_runtime;
GRANT UPDATE(status) ON payment_intents TO agendou_runtime;
-- Records are prepared for the next operations; no financial writes exposed yet.
GRANT SELECT ON payment_transactions,payment_evidence,payment_refunds TO agendou_runtime;
