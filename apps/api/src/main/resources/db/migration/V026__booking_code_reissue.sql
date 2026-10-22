ALTER TABLE booking_access_codes ADD COLUMN version integer NOT NULL DEFAULT 0;
ALTER TABLE booking_access_codes ADD COLUMN rotated_at timestamptz;
GRANT UPDATE(code_hash,version,rotated_at) ON booking_access_codes TO agendou_runtime;
CREATE TABLE booking_code_reissues (
 tenant_id uuid NOT NULL, booking_id uuid NOT NULL, version integer NOT NULL,
 recorded_by uuid NOT NULL, reason text NOT NULL CHECK(length(trim(reason)) BETWEEN 5 AND 500),
 created_at timestamptz NOT NULL DEFAULT now(), correlation_id text NOT NULL,
 PRIMARY KEY(tenant_id,booking_id,version),
 FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id),
 FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id)
);
ALTER TABLE booking_code_reissues ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_code_reissues FORCE ROW LEVEL SECURITY;
CREATE POLICY booking_code_reissue_tenant ON booking_code_reissues USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON booking_code_reissues TO agendou_runtime;
