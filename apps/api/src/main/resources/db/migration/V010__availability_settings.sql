CREATE TABLE availability_settings (
 tenant_id uuid PRIMARY KEY REFERENCES tenants(id),
 version integer NOT NULL CHECK(version > 0),
 schedule jsonb NOT NULL CHECK(jsonb_typeof(schedule)='object'),
 updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE availability_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE availability_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY availability_tenant ON availability_settings
 USING (tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT,UPDATE ON availability_settings TO agendou_runtime;
