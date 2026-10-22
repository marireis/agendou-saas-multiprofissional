ALTER TABLE bookings ADD COLUMN schedule_version integer NOT NULL DEFAULT 0 CHECK(schedule_version>=0);
GRANT UPDATE(starts_at,ends_at,schedule_version) ON bookings TO agendou_runtime;
GRANT UPDATE(month) ON booking_usage TO agendou_runtime;
CREATE TABLE booking_reschedules (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, booking_id uuid NOT NULL,request_key uuid NOT NULL,
 expected_version integer NOT NULL,old_start timestamptz NOT NULL,old_end timestamptz NOT NULL,new_start timestamptz NOT NULL,new_end timestamptz NOT NULL,
 reason text NOT NULL CHECK(length(trim(reason)) BETWEEN 5 AND 1000),recorded_by uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),correlation_id text NOT NULL,
 UNIQUE(tenant_id,booking_id,request_key),FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id),FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id)
);
ALTER TABLE booking_reschedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_reschedules FORCE ROW LEVEL SECURITY;
CREATE POLICY reschedule_tenant ON booking_reschedules USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON booking_reschedules TO agendou_runtime;
