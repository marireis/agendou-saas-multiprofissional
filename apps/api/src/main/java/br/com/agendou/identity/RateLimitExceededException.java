package br.com.agendou.identity;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfter;

    public RateLimitExceededException(long retryAfter) {
        super("Muitas tentativas. Aguarde antes de tentar novamente.");
        this.retryAfter = retryAfter;
    }

    public long retryAfter() { return retryAfter; }
}
