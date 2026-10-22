ALTER TABLE customers ALTER COLUMN email DROP NOT NULL;
ALTER TABLE customers ADD COLUMN contact_name_key text NOT NULL DEFAULT '';
CREATE UNIQUE INDEX phone_customer_identity ON customers(tenant_id,phone,contact_name_key) WHERE email IS NULL;
GRANT UPDATE(contact_name_key) ON customers TO agendou_runtime;
CREATE TABLE booking_access_codes (
 code_hash text PRIMARY KEY CHECK(length(code_hash)=64),
 tenant_id uuid NOT NULL, booking_id uuid NOT NULL,
 name_key text NOT NULL, phone text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,booking_id),
 FOREIGN KEY(tenant_id,booking_id) REFERENCES bookings(tenant_id,id)
);
ALTER TABLE booking_access_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_access_codes FORCE ROW LEVEL SECURITY;
CREATE POLICY booking_code_tenant ON booking_access_codes USING(tenant_id::text=current_setting('app.tenant_id',true));
GRANT SELECT,INSERT ON booking_access_codes TO agendou_runtime;
CREATE FUNCTION public.resolve_booking_code_tenant(requested_slug text, requested_hash text) RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT t.id FROM public.tenants t JOIN public.booking_access_codes a ON a.tenant_id=t.id
 WHERE t.slug=requested_slug AND a.code_hash=requested_hash
$$;
REVOKE ALL ON FUNCTION public.resolve_booking_code_tenant(text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_booking_code_tenant(text,text) TO agendou_runtime;
