ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK(status IN ('AWAITING_PAYMENT','IN_REVIEW','CONFIRMED','EXPIRED','CANCELED'));
ALTER TABLE payment_intents DROP CONSTRAINT payment_intents_status_check;
ALTER TABLE payment_intents ADD CONSTRAINT payment_intents_status_check CHECK(status IN ('AWAITING_PAYMENT','IN_REVIEW','CONFIRMED','EXPIRED','CANCELED'));
CREATE TABLE booking_cancellations (
 tenant_id uuid NOT NULL,booking_id uuid NOT NULL,recorded_by uuid NOT NULL,
 reason text NOT NULL CHECK(length(trim(reason)) BETWEEN 5 AND 1000),
 expected_net_cents bigint NOT NULL CHECK(expected_net_cents>=0),
 retained_cents bigint NOT NULL CHECK(retained_cents>=0 AND retained_cents<=expected_net_cents),
 created_at timestamptz NOT NULL DEFAULT now(),correlation_id text NOT NULL,
 PRIMARY KEY(tenant_id,booking_id),
 FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id),
 FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id)
);
ALTER TABLE booking_cancellations ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_cancellations FORCE ROW LEVEL SECURITY;
CREATE POLICY cancellation_tenant ON booking_cancellations USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON booking_cancellations TO agendou_runtime;
