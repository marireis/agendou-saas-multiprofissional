CREATE TABLE services (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 100),
 description TEXT NOT NULL DEFAULT '' CHECK(length(description)<=2000),
 duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 5 AND 480),
 price_cents BIGINT NOT NULL CHECK(price_cents BETWEEN 1 AND 100000000),
 buffer_before_minutes INTEGER NOT NULL DEFAULT 0 CHECK(buffer_before_minutes BETWEEN 0 AND 240),
 buffer_after_minutes INTEGER NOT NULL DEFAULT 0 CHECK(buffer_after_minutes BETWEEN 0 AND 240),
 deposit_percent INTEGER NOT NULL CHECK(deposit_percent BETWEEN 50 AND 100),
 active BOOLEAN NOT NULL DEFAULT true,
 version INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,id)
);
CREATE INDEX services_tenant_created ON services(tenant_id,created_at,id);
ALTER TABLE services ENABLE ROW LEVEL SECURITY;
ALTER TABLE services FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_services ON services USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT,UPDATE ON services TO agendou_runtime;
