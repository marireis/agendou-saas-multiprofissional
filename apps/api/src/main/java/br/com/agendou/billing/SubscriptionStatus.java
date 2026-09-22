package br.com.agendou.billing;

public enum SubscriptionStatus {
    TRIAL_ACTIVE,
    TRIAL_EXPIRING,
    TRIAL_EXPIRED_BLOCKED,
    PAID_ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELED
}
