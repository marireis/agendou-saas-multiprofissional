CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE plans (
    code TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    trial_allowed BOOLEAN NOT NULL DEFAULT false,
    trial_days INTEGER NOT NULL DEFAULT 0
);

INSERT INTO plans (code, name, trial_allowed, trial_days) VALUES
    ('BASIC', 'Basico', false, 0),
    ('INTERMEDIATE', 'Intermediario', false, 0),
    ('PREMIUM_TOP', 'Premium/Top', true, 7);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    plan_code TEXT NOT NULL REFERENCES plans(code),
    status TEXT NOT NULL,
    trial_started_at TIMESTAMPTZ,
    trial_ends_at TIMESTAMPTZ,
    paid_until TIMESTAMPTZ,
    blocked_at TIMESTAMPTZ,
    reactivated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id)
);

CREATE TABLE subscription_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    event_type TEXT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_tenants ON tenants
    USING (id::text = current_setting('app.tenant_id', true));

CREATE POLICY tenant_isolation_subscriptions ON subscriptions
    USING (tenant_id::text = current_setting('app.tenant_id', true));

CREATE POLICY tenant_isolation_subscription_events ON subscription_events
    USING (tenant_id::text = current_setting('app.tenant_id', true));
