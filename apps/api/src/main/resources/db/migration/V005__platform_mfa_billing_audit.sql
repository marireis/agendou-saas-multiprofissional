CREATE TABLE platform_roles (
 user_id UUID PRIMARY KEY REFERENCES app_users(id),
 role TEXT NOT NULL CHECK (role='SUPER_ADMIN'),
 granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 granted_by TEXT NOT NULL,
 reason TEXT NOT NULL CHECK (length(trim(reason)) >= 10)
);
-- Runtime cannot grant itself a platform role.
GRANT SELECT ON platform_roles TO agendou_runtime;

CREATE TABLE platform_mfa (
 user_id UUID PRIMARY KEY REFERENCES platform_roles(user_id) ON DELETE CASCADE,
 encrypted_secret TEXT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT false,
 enrollment_expires_at TIMESTAMPTZ NOT NULL,
 last_step BIGINT NOT NULL DEFAULT -1,
 version UUID NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON platform_mfa TO agendou_runtime;

CREATE TABLE billing_decisions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id),
 actor_id UUID NOT NULL REFERENCES app_users(id),
 action TEXT NOT NULL CHECK(action IN ('CONFIRM_PAYMENT','SUSPEND','REACTIVATE')),
 reason TEXT NOT NULL CHECK(length(trim(reason)) >= 10),
 reference TEXT UNIQUE, amount_cents BIGINT,
 before_state JSONB NOT NULL, after_state JSONB NOT NULL,
 correlation_id TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK ((action='CONFIRM_PAYMENT' AND reference IS NOT NULL AND amount_cents>0)
     OR (action<>'CONFIRM_PAYMENT' AND reference IS NULL AND amount_cents IS NULL))
);
ALTER TABLE billing_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing_decisions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_billing_decisions ON billing_decisions
 USING (tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT, INSERT ON billing_decisions TO agendou_runtime;
CREATE INDEX billing_decisions_tenant ON billing_decisions(tenant_id,created_at DESC);

CREATE TABLE platform_audit (
 id UUID PRIMARY KEY, actor_id UUID NOT NULL REFERENCES app_users(id),
 action TEXT NOT NULL, tenant_id UUID REFERENCES tenants(id),
 correlation_id TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT ON platform_audit TO agendou_runtime;
CREATE INDEX platform_audit_created ON platform_audit(created_at DESC);

-- Narrow projection for platform listing. No business-table RLS bypass granted to runtime.
CREATE FUNCTION platform_tenants(actor UUID, page_offset INTEGER)
RETURNS TABLE(id UUID, slug TEXT, display_name TEXT, subscription_status TEXT,
 plan_code TEXT, trial_ends_at TIMESTAMPTZ, paid_until TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public AS $$
 SELECT t.id,t.slug,t.display_name,s.status,s.plan_code,s.trial_ends_at,s.paid_until
 FROM public.tenants t JOIN public.subscriptions s ON s.tenant_id=t.id
 WHERE EXISTS(SELECT 1 FROM public.platform_roles r WHERE r.user_id=actor AND r.role='SUPER_ADMIN')
 ORDER BY t.created_at,t.id LIMIT 50 OFFSET greatest(0,least(page_offset,100000))
$$;
REVOKE ALL ON FUNCTION platform_tenants(UUID,INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform_tenants(UUID,INTEGER) TO agendou_runtime;
