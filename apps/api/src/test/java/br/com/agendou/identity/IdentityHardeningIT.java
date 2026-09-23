package br.com.agendou.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "agendou.mail-worker-initial-delay=3600000", "agendou.mail-worker-delay=3600000",
    "agendou.trial-worker-delay=3600000", "agendou.auth.peer-limit=120"
})
@AutoConfigureMockMvc
@Testcontainers
class IdentityHardeningIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withUsername("agendou_app").withPassword("agendou_app");

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.flyway.url", postgres::getJdbcUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired AuthRateLimiter limiter;
    @Autowired MailDeliveryService delivery;
    @Autowired MailOutboxWorker worker;
    @Autowired TestClock clock;
    @MockBean JavaMailSender sender;

    private JdbcTemplate owner() {
        return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @BeforeEach void setup() {
        clock.now.set(Instant.ofEpochSecond(Math.floorDiv(Instant.now().getEpochSecond(), 900) * 900 + 10));
        owner().update("DELETE FROM auth_rate_limits");
        owner().update("UPDATE mail_outbox SET status='CANCELED',body='',recipient='' WHERE status='PENDING'");
        reset(sender);
    }

    @Test void expiredVerificationCanBeReissuedWithoutRestartingTrial() throws Exception {
        String email = register();
        String oldToken = token(email, "VERIFY");
        var original = owner().queryForMap("SELECT s.tenant_id,s.trial_started_at,s.trial_ends_at FROM subscriptions s JOIN memberships m ON m.tenant_id=s.tenant_id JOIN app_users u ON u.id=m.user_id WHERE u.email=?", email);
        clock.advance(Duration.ofMinutes(16));
        mvc.perform(post("/api/v1/auth/verification-email").with(csrf()).contentType("application/json").content(emailBody(email.toUpperCase(Locale.ROOT))))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        String replacement = token(email, "VERIFY");
        assertThat(replacement).isNotEqualTo(oldToken);
        verifyToken(oldToken, 400);
        verifyToken(replacement, 200);
        verifyToken(replacement, 400);
        var after = owner().queryForMap("SELECT tenant_id,trial_started_at,trial_ends_at FROM subscriptions WHERE tenant_id=?", original.get("tenant_id"));
        assertThat(after).isEqualTo(original);
        long count = owner().queryForObject("SELECT count(*) FROM mail_outbox", Long.class);
        mvc.perform(post("/api/v1/auth/verification-email").with(csrf()).contentType("application/json").content(emailBody(email))).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/verification-email").with(csrf()).contentType("application/json").content(emailBody("missing@example.test"))).andExpect(status().isAccepted());
        assertThat(owner().queryForObject("SELECT count(*) FROM mail_outbox", Long.class)).isEqualTo(count);
    }

    @Test void concurrentResendsProduceOnlyOneNewLinkAndCancelOldMail() throws Exception {
        String email = register();
        auth.resendVerification(email);
        assertThat(pending(email)).isEqualTo(1); // cooldown coalesces immediate resends
        clock.advance(Duration.ofSeconds(61));
        try (var pool = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1);
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) jobs.add(pool.submit(() -> { await(start); auth.resendVerification(email); }));
            start.countDown();
            for (var job : jobs) job.get(20, TimeUnit.SECONDS);
        }
        assertThat(pending(email)).isEqualTo(1);
        assertThat(owner().queryForObject("SELECT count(*) FROM auth_tokens t JOIN app_users u ON u.id=t.user_id WHERE email=? AND purpose='VERIFY'", Long.class, email)).isEqualTo(1);
        assertThat(owner().queryForObject("SELECT count(*) FROM mail_outbox o JOIN app_users u ON u.id=o.user_id WHERE u.email=? AND o.status='CANCELED' AND o.body='' AND o.recipient=''", Long.class, email)).isEqualTo(1);
    }

    @Test void accountLimitIsAtomicAcrossConnectionsAndResetsAfterWindow() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        int allowed = 0;
        try (var pool = Executors.newFixedThreadPool(10)) {
            var start = new CountDownLatch(1);
            List<Future<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 20; i++) jobs.add(pool.submit(() -> {
                await(start);
                try { limiter.checkAccount(email, true); return true; }
                catch (RateLimitExceededException ex) { return false; }
            }));
            start.countDown();
            for (var job : jobs) if (job.get(20, TimeUnit.SECONDS)) allowed++;
        }
        assertThat(allowed).isEqualTo(10);
        assertThatThrownBy(() -> limiter.checkAccount(email.toUpperCase(Locale.ROOT), true)).isInstanceOf(RateLimitExceededException.class);
        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.checkAccount(email, true)).doesNotThrowAnyException();
        limiter.cleanup();
        assertThat(owner().queryForObject("SELECT count(*) FROM auth_rate_limits", Long.class)).isEqualTo(1);
    }

    @Test void rejectedLoginPersistsLimitAndReturnsRetryAfterAndCorrelationId() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String body = "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 10; i++) mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED")).andReturn();
        String id = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(id).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains(id).doesNotContain(email);
    }

    @Test void mailRequestsShareLimitRegardlessOfAccountExistence() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 3; i++) mvc.perform(post("/api/v1/auth/verification-email").with(csrf()).contentType("application/json").content(emailBody(email))).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType("application/json").content(emailBody(email)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test void peerLimitCannotBeBypassedByForwardedHeaders() throws Exception {
        String peer = "192.0.2.45";
        for (int i = 0; i < 120; i++) limiter.checkPeer(peer);
        mvc.perform(get("/api/v1/auth/csrf").with(request -> { request.setRemoteAddr(peer); return request; }).header("X-Forwarded-For", "192.0.2.99"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.correlation_id").isNotEmpty());
    }

    @Test void csrfAndMalformedRequestsHaveConsistentCorrelationIds() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/verification-email").contentType("application/json").content(emailBody("test@example.test")))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(result.getResponse().getHeader("X-Correlation-ID"));
        mvc.perform(post("/api/v1/auth/verification-email").with(csrf()).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post("/api/v1/auth/verification-email").with(csrf()).contentType("application/json").content(emailBody("invalid")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void smtpFailureRetriesWithoutLosingRegistrationAndScrubsPayloadOnSuccess() {
        String email = register();
        doThrow(new MailSendException("private SMTP detail")).doNothing().when(sender).send(any(SimpleMailMessage.class));
        assertThat(delivery.deliverNext()).isTrue();
        var failed = mail(email);
        assertThat(failed.get("status")).isEqualTo("PENDING");
        assertThat(failed.get("attempts")).isEqualTo(1);
        assertThat(failed.get("last_error_code")).isEqualTo("SMTP_DELIVERY_FAILED");
        assertThat(delivery.deliverNext()).isFalse();
        clock.advance(Duration.ofSeconds(30));
        assertThat(delivery.deliverNext()).isTrue();
        var sent = mail(email);
        assertThat(sent.get("status")).isEqualTo("SENT");
        assertThat(sent.get("body")).isEqualTo("");
        assertThat(sent.get("recipient")).isEqualTo("");
        assertThat(sent.get("attempts")).isEqualTo(2);
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
        assertThat(owner().queryForObject("SELECT count(*) FROM app_users WHERE email=?", Long.class, email)).isEqualTo(1);
    }

    @Test void exhaustedDeliveryIsTerminalAndResendCreatesFreshMessage() {
        String email = register();
        doThrow(new MailSendException("failure")).when(sender).send(any(SimpleMailMessage.class));
        for (int i = 0; i < 6; i++) {
            assertThat(delivery.deliverNext()).isTrue();
            var next = (Timestamp) mail(email).get("next_attempt_at");
            clock.now.set(next.toInstant());
        }
        assertThat(mail(email).get("status")).isEqualTo("FAILED");
        assertThat(mail(email).get("body")).isEqualTo("");
        assertThat(delivery.deliverNext()).isFalse();
        worker.cleanup();
        assertThat(mail(email).get("status")).isEqualTo("FAILED");
        auth.resendVerification(email);
        assertThat(pending(email)).isEqualTo(1);
        verify(sender, times(6)).send(any(SimpleMailMessage.class));
    }

    @Test void expiredOrConsumedTokensAreNotSent() {
        String expired = register();
        clock.advance(Duration.ofMinutes(16));
        assertThat(delivery.deliverNext()).isTrue();
        assertThat(mail(expired).get("status")).isEqualTo("EXPIRED");
        assertThat(mail(expired).get("recipient")).isEqualTo("");
        String consumed = register();
        auth.verify(token(consumed, "VERIFY"));
        assertThat(delivery.deliverNext()).isFalse();
        assertThat(mail(consumed).get("status")).isEqualTo("CANCELED");
        verifyNoInteractions(sender);
        worker.cleanup();
        assertThat(owner().queryForObject("SELECT count(*) FROM auth_tokens WHERE expires_at<=?", Long.class, Timestamp.from(clock.instant()))).isZero();
    }

    @Test void twoWorkersDoNotDeliverTheSameMessageConcurrently() throws Exception {
        register();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); await(release); return null; }).when(sender).send(any(SimpleMailMessage.class));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> delivery.deliverNext());
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.submit(() -> delivery.deliverNext()).get(10, TimeUnit.SECONDS)).isFalse();
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        }
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    private String register() {
        String email = "test-" + UUID.randomUUID() + "@example.test";
        auth.register(email, "synthetic-password-123", "Studio teste", "test-" + UUID.randomUUID());
        return email;
    }

    @Test void concurrentTokenConsumptionHasExactlyOneWinner() throws Exception {
        String email = register();
        String value = token(email, "VERIFY");
        int winners = 0;
        try (var pool = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1);
            List<Future<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) jobs.add(pool.submit(() -> {
                await(start);
                try { auth.verify(value); return true; }
                catch (org.springframework.web.server.ResponseStatusException ex) {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    return false;
                }
            }));
            start.countDown();
            for (var job : jobs) if (job.get(20, TimeUnit.SECONDS)) winners++;
        }
        assertThat(winners).isEqualTo(1);
    }

    @Test void deliversThroughRealSmtpToDisposableMailpit() {
        try (var mailpit = new org.testcontainers.containers.GenericContainer<>(
                org.testcontainers.utility.DockerImageName.parse("axllent/mailpit:latest")).withExposedPorts(1025)) {
            mailpit.start();
            var smtp = new org.springframework.mail.javamail.JavaMailSenderImpl();
            smtp.setHost(mailpit.getHost());
            smtp.setPort(mailpit.getMappedPort(1025));
            smtp.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "5000");
            smtp.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
            smtp.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "5000");
            doAnswer(invocation -> { smtp.send((SimpleMailMessage) invocation.getArgument(0)); return null; })
                    .when(sender).send(any(SimpleMailMessage.class));
            String email = register();
            assertThat(delivery.deliverNext()).isTrue();
            assertThat(mail(email).get("status")).isEqualTo("SENT");
            assertThat(mail(email).get("recipient")).isEqualTo("");
        }
    }

    private long pending(String email) {
        return owner().queryForObject("SELECT count(*) FROM mail_outbox o JOIN app_users u ON u.id=o.user_id WHERE u.email=? AND o.status='PENDING'", Long.class, email);
    }

    private Map<String,Object> mail(String email) {
        return owner().queryForMap("SELECT o.* FROM mail_outbox o JOIN app_users u ON u.id=o.user_id WHERE u.email=? ORDER BY o.created_at DESC LIMIT 1", email);
    }

    private String token(String email, String purpose) {
        String body = owner().queryForObject("SELECT o.body FROM mail_outbox o JOIN app_users u ON u.id=o.user_id WHERE u.email=? AND o.purpose=? AND o.status='PENDING'", String.class, email, purpose);
        return body.split("token=")[1].split("\\n")[0];
    }

    private static String emailBody(String email) { return "{\"email\":\"" + email + "\"}"; }
    private void verifyToken(String token, int status) throws Exception {
        mvc.perform(post("/api/v1/auth/verify").with(csrf()).contentType("application/json").content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().is(status));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timeout"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }

    @TestConfiguration static class Config {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
}
