package br.com.agendou.billing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class TrialPolicy {
    public static final Duration TRIAL_DURATION = Duration.ofDays(7);
    public static final Duration EXPIRING_WINDOW = Duration.ofDays(2);

    private final Clock clock;

    public TrialPolicy(Clock clock) {
        this.clock = clock;
    }

    public Subscription startTrial(java.util.UUID tenantId, PlanCode planCode) {
        if (!planCode.trialAllowed()) {
            throw new IllegalArgumentException("Trial gratis permitido somente no plano Premium/Top.");
        }

        Instant now = clock.instant();
        return new Subscription(
            tenantId,
            planCode,
            SubscriptionStatus.TRIAL_ACTIVE,
            now,
            now.plus(TRIAL_DURATION),
            null
        );
    }

    public Subscription refreshStatus(Subscription subscription) {
        if (subscription.status() == SubscriptionStatus.PAID_ACTIVE &&
                (subscription.paidUntil() == null || !clock.instant().isBefore(subscription.paidUntil()))) {
            return subscription.withStatus(SubscriptionStatus.PAST_DUE);
        }
        if (subscription.status() != SubscriptionStatus.TRIAL_ACTIVE && subscription.status() != SubscriptionStatus.TRIAL_EXPIRING) {
            return subscription;
        }

        Instant now = clock.instant();
        if (!now.isBefore(subscription.trialEndsAt())) {
            return subscription.withStatus(SubscriptionStatus.TRIAL_EXPIRED_BLOCKED);
        }

        if (!now.isBefore(subscription.trialEndsAt().minus(EXPIRING_WINDOW))) {
            return subscription.withStatus(SubscriptionStatus.TRIAL_EXPIRING);
        }

        return subscription.withStatus(SubscriptionStatus.TRIAL_ACTIVE);
    }
}
