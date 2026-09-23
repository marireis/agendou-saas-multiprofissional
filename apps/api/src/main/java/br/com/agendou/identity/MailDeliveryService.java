package br.com.agendou.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);
    private static final int MAX_ATTEMPTS = 6;
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final Clock clock;
    private final String from;

    public MailDeliveryService(JdbcTemplate jdbc, JavaMailSender mail, Clock clock,
            @Value("${agendou.mail-from}") String from) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.clock = clock;
        this.from = from;
    }

    // One message per transaction: one slow SMTP call does not roll back a whole batch.
    @Transactional
    public boolean deliverNext() {
        Timestamp now = Timestamp.from(clock.instant());
        var messages = jdbc.query("""
            SELECT id, recipient, subject, body, attempts, expires_at, token_hash
            FROM mail_outbox WHERE status='PENDING' AND (next_attempt_at<=? OR expires_at<=?)
            ORDER BY next_attempt_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, (rs, n) -> new Message(rs.getObject("id", UUID.class), rs.getString("recipient"),
                rs.getString("subject"), rs.getString("body"), rs.getInt("attempts"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getString("token_hash")), now, now);
        if (messages.isEmpty()) return false;
        Message message = messages.getFirst();
        if (message.expiresAt() == null || !clock.instant().isBefore(message.expiresAt())) {
            finish(message.id(), "EXPIRED", "LINK_EXPIRED");
            return true;
        }
        Boolean valid = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auth_tokens WHERE token_hash=? AND expires_at>?)",
                Boolean.class, message.tokenHash(), now);
        if (!Boolean.TRUE.equals(valid)) {
            finish(message.id(), "CANCELED", "LINK_REVOKED");
            return true;
        }
        int attempt = message.attempts() + 1;
        var email = new SimpleMailMessage();
        email.setFrom(from);
        email.setTo(message.recipient());
        email.setSubject(message.subject());
        email.setText(message.body());
        try {
            mail.send(email);
            jdbc.update("""
                UPDATE mail_outbox SET status='SENT', sent_at=?, last_attempt_at=?, attempts=?,
                    last_error_code=NULL, body='', recipient='' WHERE id=?
                """, Timestamp.from(clock.instant()), now, attempt, message.id());
        } catch (MailException ex) {
            // Never log SMTP exception messages: they can include recipients or message content.
            boolean terminal = attempt >= MAX_ATTEMPTS;
            Instant next = clock.instant().plusSeconds(Math.min(300, 30L << Math.min(attempt - 1, 4)));
            jdbc.update("""
                UPDATE mail_outbox SET status=?, attempts=?, last_attempt_at=?, next_attempt_at=?,
                    last_error_code='SMTP_DELIVERY_FAILED',
                    body=CASE WHEN ? THEN '' ELSE body END,
                    recipient=CASE WHEN ? THEN '' ELSE recipient END WHERE id=?
                """, terminal ? "FAILED" : "PENDING", attempt, now, Timestamp.from(next), terminal, terminal, message.id());
            log.warn("mail_delivery_failed message_id={} attempt={} terminal={}", message.id(), attempt, terminal);
        }
        return true;
    }

    private void finish(UUID id, String status, String reason) {
        jdbc.update("UPDATE mail_outbox SET status=?, last_error_code=?, body='', recipient='' WHERE id=?", status, reason, id);
    }

    private record Message(UUID id, String recipient, String subject, String body, int attempts,
            Instant expiresAt, String tokenHash) {}
}
