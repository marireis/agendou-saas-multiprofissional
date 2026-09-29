ALTER TABLE plans ADD COLUMN monthly_booking_limit integer CHECK(monthly_booking_limit>=0);
-- NULL keeps plans uncapped until the commercial limits are configured.
ALTER TABLE calendar_allocations ADD CONSTRAINT calendar_tenant_id_unique UNIQUE(tenant_id,id);
CREATE TABLE customers (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), email text NOT NULL, name text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(tenant_id,email), UNIQUE(tenant_id,id)
);
CREATE TABLE bookings (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), customer_id uuid NOT NULL, service_id uuid NOT NULL,
 allocation_id uuid NOT NULL, status text NOT NULL CHECK(status IN ('AWAITING_PAYMENT','EXPIRED')),
 starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL, expires_at timestamptz NOT NULL,
 timezone text NOT NULL, service_snapshot jsonb NOT NULL, pix_snapshot jsonb NOT NULL,
 price_cents bigint NOT NULL CHECK(price_cents>0), deposit_cents bigint NOT NULL CHECK(deposit_cents>0 AND deposit_cents<=price_cents),
 payment_version integer NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,allocation_id),
 FOREIGN KEY(tenant_id,customer_id) REFERENCES customers(tenant_id,id),
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id),
 FOREIGN KEY(tenant_id,allocation_id) REFERENCES calendar_allocations(tenant_id,id),
 FOREIGN KEY(tenant_id,payment_version) REFERENCES payment_settings_versions(tenant_id,version)
);
CREATE TABLE booking_usage (
 tenant_id uuid NOT NULL, booking_id uuid NOT NULL, month date NOT NULL,
 state text NOT NULL CHECK(state IN ('HELD','COMMITTED','RELEASED')),
 PRIMARY KEY(tenant_id,booking_id), FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id)
);
CREATE INDEX booking_usage_month ON booking_usage(tenant_id,month,state);
CREATE TABLE booking_requests (
 tenant_id uuid NOT NULL, actor_email text NOT NULL, request_key uuid NOT NULL, request_hash text NOT NULL,
 booking_id uuid NOT NULL, response jsonb NOT NULL,
 PRIMARY KEY(tenant_id,actor_email,request_key), FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id)
);
CREATE TABLE booking_events (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, booking_id uuid NOT NULL, event_type text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id)
);
ALTER TABLE customers ENABLE ROW LEVEL SECURITY; ALTER TABLE customers FORCE ROW LEVEL SECURITY;
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY; ALTER TABLE bookings FORCE ROW LEVEL SECURITY;
ALTER TABLE booking_usage ENABLE ROW LEVEL SECURITY; ALTER TABLE booking_usage FORCE ROW LEVEL SECURITY;
ALTER TABLE booking_requests ENABLE ROW LEVEL SECURITY; ALTER TABLE booking_requests FORCE ROW LEVEL SECURITY;
ALTER TABLE booking_events ENABLE ROW LEVEL SECURITY; ALTER TABLE booking_events FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_tenant ON customers USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY booking_tenant ON bookings USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY usage_tenant ON booking_usage USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY request_tenant ON booking_requests USING(tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY event_tenant ON booking_events USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON customers,bookings,booking_usage,booking_requests,booking_events TO agendou_runtime;
GRANT UPDATE(status) ON bookings TO agendou_runtime;
GRANT UPDATE(state) ON booking_usage TO agendou_runtime;
CREATE INDEX booking_expiry ON bookings(tenant_id,expires_at) WHERE status='AWAITING_PAYMENT';
ALTER TABLE mail_outbox DROP CONSTRAINT mail_outbox_purpose_check;
ALTER TABLE mail_outbox ADD CONSTRAINT mail_outbox_purpose_check CHECK(purpose IN ('VERIFY','RESET','CUSTOMER_ACCESS','BOOKING_REQUEST'));
ALTER TABLE mail_outbox ADD COLUMN booking_id uuid REFERENCES bookings(id);
CREATE UNIQUE INDEX mail_booking_request_once ON mail_outbox(booking_id) WHERE purpose='BOOKING_REQUEST';
CREATE FUNCTION public.booking_notice_live(requested_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT EXISTS(SELECT 1 FROM public.bookings WHERE id=requested_id AND status='AWAITING_PAYMENT' AND expires_at>now())
$$;
REVOKE ALL ON FUNCTION public.booking_notice_live(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.booking_notice_live(uuid) TO agendou_runtime;
CREATE FUNCTION public.resolve_customer_tenant(requested_slug text, verified_email text) RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT t.id FROM public.tenants t WHERE t.slug=requested_slug AND (
 EXISTS(SELECT 1 FROM public.public_profiles p WHERE p.tenant_id=t.id AND p.published)
 OR EXISTS(SELECT 1 FROM public.customers c WHERE c.tenant_id=t.id AND c.email=verified_email))
$$;
REVOKE ALL ON FUNCTION public.resolve_customer_tenant(text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_customer_tenant(text,text) TO agendou_runtime;
