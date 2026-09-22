package br.com.agendou.billing;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Subscription {
    private final UUID tenantId;
    private final PlanCode planCode;
    private final SubscriptionStatus status;
    private final Instant trialStartedAt;
    private final Instant trialEndsAt;
    private final Instant paidUntil;

    public Subscription(
        UUID tenantId,
        PlanCode planCode,
        SubscriptionStatus status,
        Instant trialStartedAt,
        Instant trialEndsAt,
        Instant paidUntil
    ) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.planCode = Objects.requireNonNull(planCode);
        this.status = Objects.requireNonNull(status);
        this.trialStartedAt = trialStartedAt;
        this.trialEndsAt = trialEndsAt;
        this.paidUntil = paidUntil;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public PlanCode planCode() {
        return planCode;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public Instant trialStartedAt() {
        return trialStartedAt;
    }

    public Instant trialEndsAt() {
        return trialEndsAt;
    }

    public Instant paidUntil() {
        return paidUntil;
    }

    public Subscription withStatus(SubscriptionStatus newStatus) {
        return new Subscription(tenantId, planCode, newStatus, trialStartedAt, trialEndsAt, paidUntil);
    }

    public Subscription paidUntil(Instant newPaidUntil) {
        return new Subscription(tenantId, planCode, SubscriptionStatus.PAID_ACTIVE, trialStartedAt, trialEndsAt, newPaidUntil);
    }
}
