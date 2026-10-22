ALTER TABLE customers ADD COLUMN phone text NOT NULL DEFAULT '' CHECK(length(phone)<=25);
ALTER TABLE customers ADD COLUMN version integer NOT NULL DEFAULT 0;
GRANT UPDATE(name,email,phone,version) ON customers TO agendou_runtime;
CREATE TABLE customer_changes (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,customer_id uuid NOT NULL,recorded_by uuid NOT NULL,version integer NOT NULL,
 event_type text NOT NULL CHECK(event_type IN ('CREATED','UPDATED')),created_at timestamptz NOT NULL DEFAULT now(),correlation_id text NOT NULL,
 FOREIGN KEY(tenant_id,customer_id) REFERENCES customers(tenant_id,id),FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id)
);
CREATE TABLE assisted_bookings (
 tenant_id uuid NOT NULL,booking_id uuid NOT NULL,recorded_by uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,booking_id),FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id),FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id)
);
ALTER TABLE customer_changes ENABLE ROW LEVEL SECURITY;ALTER TABLE customer_changes FORCE ROW LEVEL SECURITY;
ALTER TABLE assisted_bookings ENABLE ROW LEVEL SECURITY;ALTER TABLE assisted_bookings FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_change_tenant ON customer_changes USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY assisted_booking_tenant ON assisted_bookings USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON customer_changes,assisted_bookings TO agendou_runtime;
