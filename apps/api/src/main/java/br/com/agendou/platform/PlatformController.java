package br.com.agendou.platform;

import br.com.agendou.identity.AuthRateLimiter;
import br.com.agendou.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
    private final PlatformAccess access; private final PlatformMfaService mfa; private final PlatformBillingService billing;
    private final AuthRateLimiter limiter; private final JdbcTemplate jdbc;
    public PlatformController(PlatformAccess access,PlatformMfaService mfa,PlatformBillingService billing,AuthRateLimiter limiter,JdbcTemplate jdbc) {
        this.access=access;this.mfa=mfa;this.billing=billing;this.limiter=limiter;this.jdbc=jdbc;
    }
    private UUID actor(HttpServletRequest request) {return UUID.fromString(request.getUserPrincipal().getName());}
    @GetMapping("/session") public Map<String,Object> session(HttpServletRequest req,HttpServletResponse res) {
        res.setHeader("Cache-Control","no-store");UUID actor=actor(req);access.requireRole(actor);
        boolean enrolled=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_mfa WHERE user_id=? AND enabled)",Boolean.class,actor));
        return Map.of("enrolled",enrolled,"verified",access.verified(actor,req.getSession(false)));
    }
    @PostMapping("/mfa/enrollment") public Map<String,String> enrollment(@Valid @RequestBody Password body,HttpServletRequest req,HttpServletResponse res) {
        UUID actor=actor(req);access.requireRole(actor);limiter.checkMfa(actor);res.setHeader("Cache-Control","no-store");
        return mfa.enroll(actor,body.password(),CorrelationIdFilter.id(req));
    }
    @PostMapping("/mfa/verify") public void verify(@Valid @RequestBody Code body,HttpServletRequest req) {
        UUID actor=actor(req);access.requireRole(actor);limiter.checkMfa(actor);
        UUID version=mfa.verify(actor,body.code(),CorrelationIdFilter.id(req));
        req.changeSessionId();access.grant(actor,version,req.getSession());
    }
    @GetMapping("/tenants") public Object tenants(@RequestParam(defaultValue="0") int offset,HttpServletRequest req) {
        return billing.list(actor(req),req.getSession(false),offset(offset),CorrelationIdFilter.id(req));
    }
    @GetMapping("/tenants/{tenantId}") public Object detail(@PathVariable UUID tenantId,HttpServletRequest req) {
        return billing.detail(actor(req),req.getSession(false),tenantId,CorrelationIdFilter.id(req));
    }
    @PostMapping("/tenants/{tenantId}/decisions") public Object decide(@PathVariable UUID tenantId,@Valid @RequestBody PlatformBillingService.Decision body,HttpServletRequest req) {
        return billing.decide(actor(req),req.getSession(false),tenantId,body,CorrelationIdFilter.id(req));
    }
    @GetMapping("/audit") public Object audit(@RequestParam(defaultValue="0") int offset,HttpServletRequest req) {
        return billing.audit(actor(req),req.getSession(false),offset(offset),CorrelationIdFilter.id(req));
    }
    private int offset(int value) {if(value<0 || value>100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página inválida.");return value;}
    public record Password(@NotBlank @Size(max=64) String password) {}
    public record Code(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
}
