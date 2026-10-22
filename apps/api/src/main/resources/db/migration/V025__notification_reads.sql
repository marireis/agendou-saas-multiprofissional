ALTER TABLE booking_events ADD CONSTRAINT booking_event_tenant_id UNIQUE(tenant_id,id);
CREATE INDEX booking_event_feed ON booking_events(tenant_id,created_at DESC,id DESC);
CREATE TABLE notification_reads (
 tenant_id uuid NOT NULL, event_id uuid NOT NULL, user_id uuid NOT NULL,
 read_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,event_id,user_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES booking_events(tenant_id,id),
 FOREIGN KEY(tenant_id,user_id) REFERENCES memberships(tenant_id,user_id)
);
ALTER TABLE notification_reads ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_reads FORCE ROW LEVEL SECURITY;
CREATE POLICY notification_read_tenant ON notification_reads USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON notification_reads TO agendou_runtime;
