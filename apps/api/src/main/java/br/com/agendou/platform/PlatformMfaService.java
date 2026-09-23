package br.com.agendou.platform;

import br.com.agendou.identity.AuthService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PlatformMfaService {
    private final JdbcTemplate jdbc; private final Clock clock; private final MfaSecrets secrets;
    private final PlatformAccess access; private final AuthService auth;
    public PlatformMfaService(JdbcTemplate jdbc,Clock clock,MfaSecrets secrets,PlatformAccess access,AuthService auth) {
        this.jdbc=jdbc;this.clock=clock;this.secrets=secrets;this.access=access;this.auth=auth;
    }
    @Transactional public Map<String,String> enroll(UUID actor,String password,String correlation) {
        access.requireRole(actor);
        // Also serializes competing enrollment requests. Replacing an enabled secret is never public.
        String email=jdbc.queryForObject("SELECT email FROM app_users WHERE id=? FOR UPDATE",String.class,actor);
        auth.login(email,password);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_mfa WHERE user_id=? AND enabled)",Boolean.class,actor)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MFA já configurado. Use seu aplicativo autenticador.");
        String secret=Totp.newSecret(); UUID version=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO platform_mfa(user_id,encrypted_secret,enrollment_expires_at,version) VALUES (?,?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET encrypted_secret=excluded.encrypted_secret,
            enrollment_expires_at=excluded.enrollment_expires_at,version=excluded.version,last_step=-1,updated_at=now()
            """,actor,secrets.encrypt(actor,secret),Timestamp.from(clock.instant().plusSeconds(600)),version);
        access.audit(actor,"MFA_ENROLLMENT_STARTED",null,correlation);
        return Map.of("secret",secret,"issuer","Agendou","account",email,"algorithm","SHA1","digits","6","period","30");
    }
    @Transactional public UUID verify(UUID actor,String code,String correlation) {
        access.requireRole(actor);
        jdbc.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE",UUID.class,actor);
        var rows=jdbc.queryForList("SELECT * FROM platform_mfa WHERE user_id=? FOR UPDATE",actor);
        if(rows.isEmpty()) throw invalid();
        var row=rows.getFirst(); boolean enabled=(Boolean)row.get("enabled");
        if(!enabled && !((Timestamp)row.get("enrollment_expires_at")).toInstant().isAfter(clock.instant())) throw invalid();
        long step=Totp.match(secrets.decrypt(actor,(String)row.get("encrypted_secret")),code,clock.instant().getEpochSecond()/30,((Number)row.get("last_step")).longValue());
        if(step<0) throw invalid();
        jdbc.update("UPDATE platform_mfa SET enabled=true,last_step=?,updated_at=now() WHERE user_id=?",step,actor);
        access.audit(actor,enabled?"MFA_VERIFIED":"MFA_ENABLED",null,correlation);
        return (UUID)row.get("version");
    }
    private ResponseStatusException invalid() {return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Código inválido, expirado ou já utilizado. Aguarde o próximo código.");}
}
