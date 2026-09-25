CREATE TABLE calendar_allocations (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenants(id),
 resource_id uuid NOT NULL CHECK(resource_id=tenant_id),
 kind text NOT NULL CHECK(kind IN ('BLOCK','HOLD')),
 service_id uuid,
 starts_at timestamptz NOT NULL,
 ends_at timestamptz NOT NULL CHECK(ends_at>starts_at),
 buffer_before integer NOT NULL CHECK(buffer_before BETWEEN 0 AND 240),
 buffer_after integer NOT NULL CHECK(buffer_after BETWEEN 0 AND 240),
 protected_start timestamptz NOT NULL,
 protected_end timestamptz NOT NULL,
 active boolean NOT NULL DEFAULT true,
 expires_at timestamptz,
 reason text NOT NULL DEFAULT '' CHECK(length(reason)<=200),
 created_at timestamptz NOT NULL DEFAULT now(),
 released_at timestamptz,
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id),
 CHECK(protected_start=starts_at-make_interval(mins=>buffer_before)),
 CHECK(protected_end=ends_at+make_interval(mins=>buffer_after)),
 CHECK((kind='BLOCK' AND service_id IS NULL AND expires_at IS NULL AND buffer_before=0 AND buffer_after=0)
    OR (kind='HOLD' AND service_id IS NOT NULL AND expires_at IS NOT NULL)),
 CONSTRAINT calendar_no_overlap EXCLUDE USING gist
 (tenant_id WITH =,resource_id WITH =,tstzrange(protected_start,protected_end,'[)') WITH &&) WHERE(active)
);
ALTER TABLE calendar_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE calendar_allocations FORCE ROW LEVEL SECURITY;
CREATE POLICY calendar_tenant ON calendar_allocations USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT,UPDATE ON calendar_allocations TO agendou_runtime;
CREATE INDEX calendar_tenant_start ON calendar_allocations(tenant_id,starts_at);
