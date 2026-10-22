ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK(status IN ('AWAITING_PAYMENT','IN_REVIEW','CONFIRMED','EXPIRED'));
ALTER TABLE payment_intents DROP CONSTRAINT payment_intents_status_check;
ALTER TABLE payment_intents ADD CONSTRAINT payment_intents_status_check CHECK(status IN ('AWAITING_PAYMENT','IN_REVIEW','CONFIRMED','EXPIRED'));
ALTER TABLE calendar_allocations DROP CONSTRAINT calendar_allocations_kind_check;
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='calendar_allocations'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%kind%' AND pg_get_constraintdef(oid) LIKE '%service_id%' LOOP
 EXECUTE format('ALTER TABLE calendar_allocations DROP CONSTRAINT %I',c.conname);
 END LOOP;
END $$;
ALTER TABLE calendar_allocations ADD CONSTRAINT calendar_kind CHECK(kind IN ('BLOCK','HOLD','BOOKING'));
ALTER TABLE calendar_allocations ADD CONSTRAINT calendar_kind_fields CHECK(
 (kind='BLOCK' AND service_id IS NULL AND expires_at IS NULL AND buffer_before=0 AND buffer_after=0)
 OR (kind='HOLD' AND service_id IS NOT NULL AND expires_at IS NOT NULL)
 OR (kind='BOOKING' AND service_id IS NOT NULL AND expires_at IS NULL));
ALTER TABLE payment_transactions ADD COLUMN receiving_account text NOT NULL DEFAULT 'Registro anterior' CHECK(length(trim(receiving_account)) BETWEEN 2 AND 120);
ALTER TABLE payment_transactions ADD COLUMN correlation_id text NOT NULL DEFAULT '';
ALTER TABLE payment_transactions ADD CONSTRAINT payment_operator_member FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id);
GRANT INSERT ON payment_transactions TO agendou_runtime;
