ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK(status IN ('AWAITING_PAYMENT','IN_REVIEW','EXPIRED'));
ALTER TABLE bookings ADD COLUMN review_expires_at timestamptz;
ALTER TABLE payment_intents DROP CONSTRAINT payment_intents_status_check;
ALTER TABLE payment_intents ADD CONSTRAINT payment_intents_status_check CHECK(status IN ('AWAITING_PAYMENT','IN_REVIEW','EXPIRED'));
ALTER TABLE payment_evidence ADD CONSTRAINT evidence_same_content UNIQUE(tenant_id,intent_id,sha256);
GRANT INSERT ON payment_evidence TO agendou_runtime;
GRANT UPDATE(review_expires_at) ON bookings TO agendou_runtime;
CREATE INDEX booking_review_expiry ON bookings(tenant_id,review_expires_at) WHERE status='IN_REVIEW';
CREATE OR REPLACE FUNCTION public.claim_booking_expiration() RETURNS TABLE(tenant_id uuid,lease_token uuid)
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 WITH candidate AS (
  SELECT j.tenant_id FROM public.booking_expiration_jobs j
  WHERE j.next_attempt_at<=now() AND (j.leased_until IS NULL OR j.leased_until<=now())
   AND EXISTS(SELECT 1 FROM public.bookings b WHERE b.tenant_id=j.tenant_id AND b.status IN ('AWAITING_PAYMENT','IN_REVIEW') AND coalesce(b.review_expires_at,b.expires_at)<=now())
  ORDER BY j.next_attempt_at,j.tenant_id LIMIT 1 FOR UPDATE OF j SKIP LOCKED
 ) UPDATE public.booking_expiration_jobs j SET lease_token=gen_random_uuid(),leased_until=now()+interval '2 minutes'
 FROM candidate c WHERE j.tenant_id=c.tenant_id RETURNING j.tenant_id,j.lease_token
$$;
