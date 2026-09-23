package br.com.agendou.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int loginLimit;
    private final int mailLimit;
    private final int peerLimit;

    public AuthRateLimiter(JdbcTemplate jdbc, Clock clock,
            @Value("${agendou.auth.login-limit:10}") int loginLimit,
            @Value("${agendou.auth.mail-limit:3}") int mailLimit,
            @Value("${agendou.auth.peer-limit:120}") int peerLimit) {
        if (loginLimit < 1 || mailLimit < 1 || peerLimit < 1) throw new IllegalArgumentException("Limites devem ser positivos.");
        this.jdbc = jdbc;
        this.clock = clock;
        this.loginLimit = loginLimit;
        this.mailLimit = mailLimit;
        this.peerLimit = peerLimit;
    }

    // Independent transactions ensure failed authentication does not roll back the counter.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = RateLimitExceededException.class)
    public void checkAccount(String email, boolean login) {
        consume(login ? "login" : "mail", email.trim().toLowerCase(Locale.ROOT),
                login ? loginLimit : mailLimit, Duration.ofMinutes(15));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = RateLimitExceededException.class)
    public void checkPeer(String address) {
        consume("peer", address, peerLimit, Duration.ofMinutes(1));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = RateLimitExceededException.class)
    public void checkMfa(java.util.UUID actor) {
        consume("mfa", actor.toString(), 5, Duration.ofMinutes(5));
    }

    private void consume(String scope, String subject, int limit, Duration window) {
        long now = clock.instant().getEpochSecond();
        long start = Math.floorDiv(now, window.toSeconds()) * window.toSeconds();
        long end = start + window.toSeconds();
        Integer hits = jdbc.queryForObject("""
            INSERT INTO auth_rate_limits(bucket_key, window_start, hits, expires_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT (bucket_key, window_start) DO UPDATE
            SET hits = LEAST(auth_rate_limits.hits + 1, ?)
            RETURNING hits
            """, Integer.class, digest(scope + ":" + subject), start,
                Timestamp.from(java.time.Instant.ofEpochSecond(end)), limit + 1);
        if (hits != null && hits > limit) throw new RateLimitExceededException(Math.max(1, end - now));
    }

    @Scheduled(fixedDelayString = "${agendou.auth.cleanup-delay:3600000}")
    public void cleanup() {
        jdbc.update("DELETE FROM auth_rate_limits WHERE expires_at < ?", Timestamp.from(clock.instant()));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
