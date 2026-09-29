CREATE TABLE booking_expiration_jobs (
 tenant_id uuid PRIMARY KEY REFERENCES tenants(id),
 lease_token uuid,
 leased_until timestamptz,
 next_attempt_at timestamptz NOT NULL DEFAULT now(),
 failures integer NOT NULL DEFAULT 0 CHECK(failures>=0)
);
ALTER TABLE booking_expiration_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_expiration_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY expiration_job_tenant ON booking_expiration_jobs USING(tenant_id::text=current_setting('app.tenant_id',true));
INSERT INTO booking_expiration_jobs(tenant_id) SELECT id FROM tenants;
CREATE FUNCTION public.initialize_booking_expiration_job() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN INSERT INTO public.booking_expiration_jobs(tenant_id) VALUES(NEW.id); RETURN NEW; END
$$;
CREATE TRIGGER tenant_expiration_job AFTER INSERT ON tenants FOR EACH ROW EXECUTE FUNCTION public.initialize_booking_expiration_job();
REVOKE ALL ON FUNCTION public.initialize_booking_expiration_job() FROM PUBLIC;

-- Only work coordinates leave this function; no customer or booking data is exposed.
CREATE FUNCTION public.claim_booking_expiration() RETURNS TABLE(tenant_id uuid,lease_token uuid)
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 WITH candidate AS (
  SELECT j.tenant_id FROM public.booking_expiration_jobs j
  WHERE j.next_attempt_at<=now() AND (j.leased_until IS NULL OR j.leased_until<=now())
   AND EXISTS(SELECT 1 FROM public.bookings b WHERE b.tenant_id=j.tenant_id AND b.status='AWAITING_PAYMENT' AND b.expires_at<=now())
  ORDER BY j.next_attempt_at,j.tenant_id LIMIT 1 FOR UPDATE OF j SKIP LOCKED
 ) UPDATE public.booking_expiration_jobs j SET lease_token=gen_random_uuid(),leased_until=now()+interval '2 minutes'
 FROM candidate c WHERE j.tenant_id=c.tenant_id RETURNING j.tenant_id,j.lease_token
$$;
CREATE FUNCTION public.lock_booking_expiration(requested_tenant uuid,requested_token uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
 PERFORM 1 FROM public.booking_expiration_jobs j WHERE j.tenant_id=requested_tenant AND j.lease_token=requested_token AND j.leased_until>clock_timestamp() FOR UPDATE;
 RETURN FOUND;
END
$$;
CREATE FUNCTION public.finish_booking_expiration(requested_tenant uuid,requested_token uuid,failed boolean) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 UPDATE public.booking_expiration_jobs SET lease_token=NULL,leased_until=NULL,
  failures=CASE WHEN failed THEN least(failures+1,10) ELSE 0 END,
  next_attempt_at=clock_timestamp()+CASE WHEN failed THEN make_interval(secs=>least(900,30*power(2,least(failures,5)))::int) ELSE interval '0 seconds' END
 WHERE tenant_id=requested_tenant AND lease_token=requested_token
$$;
REVOKE ALL ON FUNCTION public.claim_booking_expiration(),public.lock_booking_expiration(uuid,uuid),public.finish_booking_expiration(uuid,uuid,boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.claim_booking_expiration(),public.lock_booking_expiration(uuid,uuid),public.finish_booking_expiration(uuid,uuid,boolean) TO agendou_runtime;
