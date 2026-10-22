ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK(status IN ('AWAITING_PAYMENT','IN_REVIEW','CONFIRMED','EXPIRED','CANCELED','COMPLETED','NO_SHOW'));
CREATE TABLE booking_outcomes (
 tenant_id uuid NOT NULL,booking_id uuid NOT NULL,status text NOT NULL CHECK(status IN ('COMPLETED','NO_SHOW')),
 expected_version integer NOT NULL,reason text NOT NULL CHECK(length(trim(reason)) BETWEEN 5 AND 1000),
 recorded_by uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),correlation_id text NOT NULL,
 PRIMARY KEY(tenant_id,booking_id),FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id),
 FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id)
);
ALTER TABLE booking_outcomes ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_outcomes FORCE ROW LEVEL SECURITY;
CREATE POLICY outcome_tenant ON booking_outcomes USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON booking_outcomes TO agendou_runtime;
