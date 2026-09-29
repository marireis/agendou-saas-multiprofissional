CREATE TABLE customer_portal_tokens (
 token_hash text PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenants(id),
 email text NOT NULL,
 expires_at timestamptz NOT NULL,
 UNIQUE(tenant_id,email)
);
ALTER TABLE customer_portal_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_portal_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY portal_token_tenant ON customer_portal_tokens USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT,DELETE ON customer_portal_tokens TO agendou_runtime;
CREATE INDEX portal_token_expiry ON customer_portal_tokens(expires_at);
CREATE INDEX customer_booking_list ON bookings(tenant_id,customer_id,starts_at DESC,id DESC);
ALTER TABLE mail_outbox DROP CONSTRAINT mail_outbox_purpose_check;
ALTER TABLE mail_outbox ADD CONSTRAINT mail_outbox_purpose_check CHECK(purpose IN ('VERIFY','RESET','CUSTOMER_ACCESS','BOOKING_REQUEST','CUSTOMER_PORTAL'));
CREATE OR REPLACE FUNCTION public.customer_access_live(requested_hash text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT EXISTS(SELECT 1 FROM public.customer_access_tokens WHERE token_hash=requested_hash AND expires_at>now())
 OR EXISTS(SELECT 1 FROM public.customer_portal_tokens WHERE token_hash=requested_hash AND expires_at>now())
$$;
CREATE OR REPLACE FUNCTION public.purge_customer_access() RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 DELETE FROM public.customer_access_tokens WHERE expires_at<=now();
 DELETE FROM public.customer_portal_tokens WHERE expires_at<=now();
$$;
