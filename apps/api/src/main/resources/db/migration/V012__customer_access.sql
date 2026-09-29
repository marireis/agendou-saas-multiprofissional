-- Public routing exposes only the tenant of an explicitly published page.
CREATE FUNCTION public.resolve_booking_tenant(requested_slug text) RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT t.id FROM public.tenants t JOIN public.public_profiles p ON p.tenant_id=t.id
 WHERE t.slug=requested_slug AND p.published
$$;
REVOKE ALL ON FUNCTION public.resolve_booking_tenant(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_booking_tenant(text) TO agendou_runtime;
CREATE TABLE customer_access_tokens (
 token_hash text PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenants(id),
 service_id uuid NOT NULL,
 starts_at timestamptz NOT NULL,
 customer_name text NOT NULL CHECK(length(customer_name) BETWEEN 1 AND 100),
 email text NOT NULL CHECK(length(email)<=254),
 expires_at timestamptz NOT NULL,
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id)
);
ALTER TABLE customer_access_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_access_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_access_tenant ON customer_access_tokens USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT,DELETE ON customer_access_tokens TO agendou_runtime;
CREATE INDEX customer_access_expiry ON customer_access_tokens(expires_at);
ALTER TABLE mail_outbox DROP CONSTRAINT mail_outbox_purpose_check;
ALTER TABLE mail_outbox ADD CONSTRAINT mail_outbox_purpose_check CHECK(purpose IN ('VERIFY','RESET','CUSTOMER_ACCESS'));
-- Worker can check liveness and purge expired secrets, but cannot read customer details.
CREATE FUNCTION public.customer_access_live(requested_hash text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT EXISTS(SELECT 1 FROM public.customer_access_tokens WHERE token_hash=requested_hash AND expires_at>now())
$$;
CREATE FUNCTION public.purge_customer_access() RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 DELETE FROM public.customer_access_tokens WHERE expires_at<=now()
$$;
REVOKE ALL ON FUNCTION public.customer_access_live(text),public.purge_customer_access() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.customer_access_live(text),public.purge_customer_access() TO agendou_runtime;
