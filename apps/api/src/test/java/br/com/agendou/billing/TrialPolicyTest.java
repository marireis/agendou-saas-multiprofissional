package br.com.agendou.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrialPolicyTest {
    private final Instant base = Instant.parse("2026-09-22T10:00:00Z");

    @Test void paidSubscriptionExpiresExactlyAtDeadlineWithoutGrace() {
        var subscription=new Subscription(UUID.randomUUID(),PlanCode.BASIC,SubscriptionStatus.PAID_ACTIVE,null,null,base);
        assertThat(new TrialPolicy(Clock.fixed(base.minusSeconds(1),ZoneOffset.UTC)).refreshStatus(subscription).status()).isEqualTo(SubscriptionStatus.PAID_ACTIVE);
        assertThat(new TrialPolicy(Clock.fixed(base,ZoneOffset.UTC)).refreshStatus(subscription).status()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void preservesAdministrativeStatesWithoutTrialDates() {
        TrialPolicy policy = new TrialPolicy(Clock.fixed(base, ZoneOffset.UTC));
        for (var status : new SubscriptionStatus[]{SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELED, SubscriptionStatus.PAST_DUE}) {
            var subscription = new Subscription(UUID.randomUUID(), PlanCode.BASIC, status, null, null, null);
            assertThat(policy.refreshStatus(subscription).status()).isEqualTo(status);
        }
    }

    @Test
    void warnsExactlyTwoDaysBeforeExpiry() {
        var subscription = new TrialPolicy(Clock.fixed(base, ZoneOffset.UTC)).startTrial(UUID.randomUUID(), PlanCode.PREMIUM_TOP);
        assertThat(new TrialPolicy(Clock.fixed(base.plus(java.time.Duration.ofDays(5)), ZoneOffset.UTC)).refreshStatus(subscription).status())
            .isEqualTo(SubscriptionStatus.TRIAL_EXPIRING);
    }

    @Test
    void startsOnlyPremiumTopTrialForSevenDays() {
        TrialPolicy policy = new TrialPolicy(Clock.fixed(base, ZoneOffset.UTC));

        Subscription subscription = policy.startTrial(UUID.randomUUID(), PlanCode.PREMIUM_TOP);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.TRIAL_ACTIVE);
        assertThat(subscription.trialEndsAt()).isEqualTo(base.plus(TrialPolicy.TRIAL_DURATION));
    }

    @Test
    void rejectsTrialForLowerPlans() {
        TrialPolicy policy = new TrialPolicy(Clock.fixed(base, ZoneOffset.UTC));

        assertThatThrownBy(() -> policy.startTrial(UUID.randomUUID(), PlanCode.BASIC))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Premium/Top");
    }

    @Test
    void blocksExpiredTrialWithoutPayment() {
        TrialPolicy startPolicy = new TrialPolicy(Clock.fixed(base, ZoneOffset.UTC));
        Subscription subscription = startPolicy.startTrial(UUID.randomUUID(), PlanCode.PREMIUM_TOP);
        TrialPolicy expiredPolicy = new TrialPolicy(Clock.fixed(base.plus(TrialPolicy.TRIAL_DURATION), ZoneOffset.UTC));

        Subscription refreshed = expiredPolicy.refreshStatus(subscription);

        assertThat(refreshed.status()).isEqualTo(SubscriptionStatus.TRIAL_EXPIRED_BLOCKED);
    }
}
