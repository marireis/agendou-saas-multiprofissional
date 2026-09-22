package br.com.agendou.billing;

public enum PlanCode {
    BASIC(false),
    INTERMEDIATE(false),
    PREMIUM_TOP(true);

    private final boolean trialAllowed;

    PlanCode(boolean trialAllowed) {
        this.trialAllowed = trialAllowed;
    }

    public boolean trialAllowed() {
        return trialAllowed;
    }
}
