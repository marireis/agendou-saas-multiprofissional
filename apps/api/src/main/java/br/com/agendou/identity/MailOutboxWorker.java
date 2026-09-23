package br.com.agendou.identity;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MailOutboxWorker {
    private final MailDeliveryService delivery;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MailOutboxWorker(MailDeliveryService delivery, JdbcTemplate jdbc, Clock clock) {
        this.delivery = delivery;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${agendou.mail-worker-delay:5000}",
            initialDelayString = "${agendou.mail-worker-initial-delay:5000}")
    public void deliver() {
        for (int i = 0; i < 10 && delivery.deliverNext(); i++) { /* bounded batch */ }
    }

    @Scheduled(fixedDelayString = "${agendou.auth.cleanup-delay:3600000}",
            initialDelayString = "${agendou.mail-worker-initial-delay:5000}")
    public void cleanup() {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("DELETE FROM auth_tokens WHERE expires_at<=?", now);
        jdbc.update("""
            UPDATE mail_outbox SET status='EXPIRED', body='', recipient='', last_error_code='LINK_EXPIRED'
            WHERE status='PENDING' AND expires_at<=?
            """, now);
    }
}
