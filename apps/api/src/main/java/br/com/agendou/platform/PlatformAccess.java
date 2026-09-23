package br.com.agendou.platform;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformAccess {
    public static final String MFA_USER="platform.mfa.user", MFA_VERSION="platform.mfa.version", MFA_UNTIL="platform.mfa.until";
    private final JdbcTemplate jdbc; private final Clock clock;
    public PlatformAccess(JdbcTemplate jdbc,Clock clock) {this.jdbc=jdbc;this.clock=clock;}
    public void requireRole(UUID actor) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_roles WHERE user_id=? AND role='SUPER_ADMIN')",Boolean.class,actor)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Acesso exclusivo da administração da plataforma.");
    }
    public boolean verified(UUID actor,HttpSession session) {
        if(session==null || !actor.toString().equals(session.getAttribute(MFA_USER)) || !(session.getAttribute(MFA_UNTIL) instanceof Long until) || until<=clock.instant().getEpochSecond()) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_mfa WHERE user_id=? AND enabled AND version::text=?)",Boolean.class,actor,session.getAttribute(MFA_VERSION)));
    }
    public void require(UUID actor,HttpSession session) {
        requireRole(actor);
        if(!verified(actor,session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Confirme o MFA para acessar a plataforma.");
    }
    public void grant(UUID actor,UUID version,HttpSession session) {
        session.setAttribute(MFA_USER,actor.toString());session.setAttribute(MFA_VERSION,version.toString());
        session.setAttribute(MFA_UNTIL,clock.instant().plusSeconds(900).getEpochSecond());
    }
    public static void clear(HttpSession session) {
        session.removeAttribute(MFA_USER);session.removeAttribute(MFA_VERSION);session.removeAttribute(MFA_UNTIL);
    }
    public void audit(UUID actor,String action,UUID tenant,String correlation) {
        jdbc.update("INSERT INTO platform_audit(id,actor_id,action,tenant_id,correlation_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),actor,action,tenant,correlation);
    }
}
