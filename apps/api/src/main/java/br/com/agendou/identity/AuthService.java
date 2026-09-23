package br.com.agendou.identity;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.TenantContext;
import br.com.agendou.tenancy.TenantSessionConfigurer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final TenantSessionConfigurer tenants;
    private final SubscriptionService subscriptions;
    private final Clock clock;
    private final String publicUrl;
    private final String dummyPassword;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, TenantSessionConfigurer tenants,
            SubscriptionService subscriptions, Clock clock, @Value("${agendou.public-url}") String publicUrl) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.clock = clock;
        this.publicUrl = publicUrl;
        this.dummyPassword = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public void register(String email, String password, String name, String slug) {
        String normalized = normalize(email);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app_users WHERE email=?)", Boolean.class, normalized))) return;
        UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        try {
            tenants.applyCurrentTenant();
            jdbc.update("INSERT INTO app_users(id,email,password_hash) VALUES (?,?,?)", user, normalized, passwords.encode(password));
            jdbc.update("INSERT INTO tenants(id,slug,display_name,status) VALUES (?,?,?,'ACTIVE')", tenant, slug, name);
            jdbc.update("INSERT INTO memberships(user_id,tenant_id,role) VALUES (?,?,'ADMIN')", user, tenant);
            jdbc.update("INSERT INTO public_profiles(tenant_id) VALUES (?)", tenant);
            subscriptions.startPremiumTrial(tenant);
            issueToken(user, normalized, "VERIFY");
        } finally { TenantContext.clear(); }
    }

    @Transactional
    public void resendVerification(String email) { requestToken(email, "VERIFY"); }

    @Transactional
    public void verify(String token) {
        UUID user = consume(token, "VERIFY");
        jdbc.update("UPDATE app_users SET verified=true WHERE id=?", user);
    }

    public UUID login(String email, String password) {
        var users = jdbc.query("SELECT id,password_hash,verified FROM app_users WHERE email=?",
                (rs, n) -> new User(rs.getObject("id", UUID.class), rs.getString("password_hash"), rs.getBoolean("verified")), normalize(email));
        User user = users.isEmpty() ? null : users.getFirst();
        boolean valid = passwords.matches(password, user == null ? dummyPassword : user.hash());
        if (!valid || user == null || !user.verified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas ou email nao verificado.");
        }
        return user.id();
    }

    @Transactional
    public void requestReset(String email) { requestToken(email, "RESET"); }

    @Transactional
    public void reset(String token, String password) {
        UUID user = consume(token, "RESET");
        jdbc.update("UPDATE app_users SET password_hash=? WHERE id=?", passwords.encode(password), user);
        jdbc.update("DELETE FROM spring_session WHERE principal_name=?", user.toString());
    }

    private void requestToken(String email, String purpose) {
        // Serialize requests and token consumption for this user, including concurrent resends.
        var users = jdbc.query("SELECT id,verified FROM app_users WHERE email=? FOR UPDATE",
                (rs, n) -> new User(rs.getObject("id", UUID.class), "", rs.getBoolean("verified")), normalize(email));
        if (users.isEmpty() || (purpose.equals("VERIFY") && users.getFirst().verified())) return;
        UUID user = users.getFirst().id();
        Boolean recent = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM auth_tokens WHERE user_id=? AND purpose=? AND expires_at>?)
            """, Boolean.class, user, purpose, Timestamp.from(clock.instant().plus(Duration.ofMinutes(14))));
        if (!Boolean.TRUE.equals(recent)) issueToken(user, normalize(email), purpose);
    }

    private UUID consume(String token, String purpose) {
        String tokenHash = hash(token);
        var users = jdbc.query("SELECT user_id FROM auth_tokens WHERE token_hash=? AND purpose=?",
                (rs, n) -> rs.getObject(1, UUID.class), tokenHash, purpose);
        if (users.isEmpty()) throw invalidToken();
        UUID user = users.getFirst();
        jdbc.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE", UUID.class, user);
        int consumed = jdbc.update("DELETE FROM auth_tokens WHERE token_hash=? AND purpose=? AND expires_at>?",
                tokenHash, purpose, Timestamp.from(clock.instant()));
        if (consumed != 1) throw invalidToken();
        revokeTokens(user, purpose);
        return user;
    }

    private void revokeTokens(UUID user, String purpose) {
        jdbc.update("DELETE FROM auth_tokens WHERE user_id=? AND purpose=?", user, purpose);
        jdbc.update("""
            UPDATE mail_outbox SET status='CANCELED', recipient='', body=''
            WHERE user_id=? AND purpose=? AND status='PENDING'
            """, user, purpose);
    }

    private void issueToken(UUID user, String email, String purpose) {
        revokeTokens(user, purpose);
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = hash(token);
        Timestamp expires = Timestamp.from(clock.instant().plus(Duration.ofMinutes(15)));
        jdbc.update("INSERT INTO auth_tokens(token_hash,user_id,purpose,expires_at) VALUES (?,?,?,?)", tokenHash, user, purpose, expires);
        String route = purpose.equals("VERIFY") ? "verificar" : "recuperar";
        jdbc.update("""
            INSERT INTO mail_outbox(id,recipient,subject,body,user_id,purpose,token_hash,expires_at,created_at,next_attempt_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, UUID.randomUUID(), email, "Agendou: confirme sua solicitacao",
                publicUrl + "/" + route + "#token=" + token + "\nEste link expira em 15 minutos.",
                user, purpose, tokenHash, expires, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link invalido ou expirado. Solicite um novo email.");
    }

    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private record User(UUID id, String hash, boolean verified) {}
}
