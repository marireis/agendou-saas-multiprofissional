ALTER TABLE payment_refunds ADD COLUMN correlation_id text NOT NULL DEFAULT '';
ALTER TABLE payment_refunds ADD CONSTRAINT refund_operator_member FOREIGN KEY(tenant_id,recorded_by) REFERENCES memberships(tenant_id,user_id);
GRANT INSERT ON payment_refunds TO agendou_runtime;
