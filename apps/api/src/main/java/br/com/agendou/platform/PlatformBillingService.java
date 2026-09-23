package br.com.agendou.platform;

import br.com.agendou.billing.*;
import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformBillingService {
    private final JdbcTemplate jdbc; private final PlatformAccess access; private final SubscriptionService subscriptions;
    private final TenantSessionConfigurer tenants; private final Clock clock; private final ObjectMapper json;
    public PlatformBillingService(JdbcTemplate jdbc,PlatformAccess access,SubscriptionService subscriptions,TenantSessionConfigurer tenants,Clock clock,ObjectMapper json) {
        this.jdbc=jdbc;this.access=access;this.subscriptions=subscriptions;this.tenants=tenants;this.clock=clock;this.json=json;
    }
    @Transactional public List<Map<String,Object>> list(UUID actor,HttpSession session,int offset,String correlation) {
        access.require(actor,session);
        access.audit(actor,"TENANTS_LISTED",null,correlation);
        return jdbc.queryForList("SELECT * FROM platform_tenants(?,?)",actor,offset);
    }
    @Transactional public List<Map<String,Object>> audit(UUID actor,HttpSession session,int offset,String correlation) {
        access.require(actor,session);access.audit(actor,"AUDIT_VIEWED",null,correlation);
        return jdbc.queryForList("SELECT id,actor_id,action,tenant_id,correlation_id,created_at FROM platform_audit ORDER BY created_at DESC,id LIMIT 50 OFFSET ?",offset);
    }
    @Transactional public Map<String,Object> detail(UUID actor,HttpSession session,UUID tenant,String correlation) {
        access.require(actor,session);TenantContext.set(tenant);
        try {
            prepare(tenant); var subscription=subscriptions.current(tenant);
            var profile=jdbc.queryForMap("SELECT id,slug,display_name FROM tenants WHERE id=?",tenant);
            access.audit(actor,"TENANT_VIEWED",tenant,correlation);
            return Map.of("tenant",profile,"subscription",state(subscription),"decisions",jdbc.queryForList("""
                SELECT id,actor_id,action,reason,reference,amount_cents,before_state::text,after_state::text,created_at
                FROM billing_decisions WHERE tenant_id=? ORDER BY created_at DESC,id LIMIT 50
                """,tenant));
        } finally {TenantContext.clear();}
    }
    @Transactional public Map<String,Object> decide(UUID actor,HttpSession session,UUID tenant,Decision decision,String correlation) {
        access.require(actor,session);TenantContext.set(tenant);
        try {
            prepare(tenant);
            String reason=decision.reason().trim();
            if(reason.length()<10) throw bad("Descreva o motivo com pelo menos 10 caracteres.");
            boolean payment=decision.action()==Action.CONFIRM_PAYMENT;
            String reference=decision.reference()==null?null:decision.reference().trim().toUpperCase(Locale.ROOT);
            if(payment && (reference==null || reference.length()<3 || decision.amountCents()==null || decision.amountCents()<=0 || decision.planCode()==null)) throw bad("Informe plano, valor e referência do pagamento conferido.");
            if(!payment && (decision.reference()!=null || decision.amountCents()!=null || decision.planCode()!=null)) throw bad("Dados de pagamento não se aplicam a esta decisão.");
            var existing=jdbc.queryForList("SELECT * FROM billing_decisions WHERE id=?",decision.id());
            if(!existing.isEmpty()) {
                var old=existing.getFirst();
                var after=parse(old.get("after_state").toString());
                if(!actor.equals(old.get("actor_id")) || !decision.action().name().equals(old.get("action")) || !reason.equals(old.get("reason"))
                    || !Objects.equals(reference,old.get("reference")) || !Objects.equals(decision.amountCents(),old.get("amount_cents"))
                    || (payment && !decision.planCode().name().equals(after.get("planCode")))) throw conflict("Identificador já usado para outra decisão.");
                return after;
            }
            Subscription before=subscriptions.current(tenant),after;
            switch(decision.action()) {
                case CONFIRM_PAYMENT -> {
                    // Suspension/cancellation needs an explicit administrative resolution first.
                    if(before.status()==SubscriptionStatus.SUSPENDED || before.status()==SubscriptionStatus.CANCELED) throw conflict("Resolva a suspensão antes de registrar pagamento.");
                    subscriptions.confirmPayment(tenant);
                    jdbc.update("UPDATE subscriptions SET plan_code=? WHERE tenant_id=?",decision.planCode().name(),tenant);
                    after=subscriptions.current(tenant);
                }
                case SUSPEND -> {
                    if(before.status()==SubscriptionStatus.SUSPENDED || before.status()==SubscriptionStatus.CANCELED) throw conflict("Assinatura já suspensa ou cancelada.");
                    jdbc.update("UPDATE subscriptions SET status='SUSPENDED',blocked_at=?,updated_at=now() WHERE tenant_id=?",Timestamp.from(clock.instant()),tenant);
                    after=before.withStatus(SubscriptionStatus.SUSPENDED);
                }
                case REACTIVATE -> {
                    if(before.status()!=SubscriptionStatus.SUSPENDED) throw conflict("A reativação administrativa exige assinatura suspensa.");
                    SubscriptionStatus restored;
                    if(before.paidUntil()!=null && before.paidUntil().isAfter(clock.instant())) restored=SubscriptionStatus.PAID_ACTIVE;
                    else if(before.paidUntil()==null && before.trialEndsAt()!=null && before.trialEndsAt().isAfter(clock.instant()))
                        restored=new TrialPolicy(clock).refreshStatus(before.withStatus(SubscriptionStatus.TRIAL_ACTIVE)).status();
                    else restored=before.paidUntil()==null?SubscriptionStatus.TRIAL_EXPIRED_BLOCKED:SubscriptionStatus.PAST_DUE;
                    // Lifts the operational suspension; never grants a free period or restarts trial.
                    jdbc.update("UPDATE subscriptions SET status=?,updated_at=now() WHERE tenant_id=?",restored.name(),tenant);
                    if(restored==SubscriptionStatus.PAID_ACTIVE || restored==SubscriptionStatus.TRIAL_ACTIVE || restored==SubscriptionStatus.TRIAL_EXPIRING)
                        jdbc.update("UPDATE subscriptions SET blocked_at=NULL,reactivated_at=? WHERE tenant_id=?",Timestamp.from(clock.instant()),tenant);
                    after=before.withStatus(restored);
                }
                default -> throw bad("Decisão inválida.");
            }
            Map<String,Object> result=state(after);
            jdbc.update("""
                INSERT INTO billing_decisions(id,tenant_id,actor_id,action,reason,reference,amount_cents,before_state,after_state,correlation_id)
                VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)
                """,decision.id(),tenant,actor,decision.action().name(),reason,reference,decision.amountCents(),encode(state(before)),encode(result),correlation);
            jdbc.update("INSERT INTO subscription_events(id,tenant_id,subscription_id,event_type,details) SELECT ?,tenant_id,id,?,jsonb_build_object('decision_id',?::text) FROM subscriptions WHERE tenant_id=?",
                UUID.randomUUID(),"PLATFORM_"+decision.action().name(),decision.id().toString(),tenant);
            access.audit(actor,decision.action().name(),tenant,correlation);
            return result;
        } finally {TenantContext.clear();}
    }
    private void prepare(UUID tenant) {
        tenants.applyCurrentTenant();
        if(jdbc.queryForList("SELECT id FROM tenants WHERE id=? FOR UPDATE",tenant).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Profissional não encontrado.");
    }
    private Map<String,Object> state(Subscription s) {
        Map<String,Object> map=new LinkedHashMap<>();map.put("status",s.status().name());map.put("planCode",s.planCode().name());
        map.put("trialStartedAt",s.trialStartedAt()==null?null:s.trialStartedAt().toString());map.put("trialEndsAt",s.trialEndsAt()==null?null:s.trialEndsAt().toString());map.put("paidUntil",s.paidUntil()==null?null:s.paidUntil().toString());return map;
    }
    private String encode(Map<String,Object> value) {try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private Map<String,Object> parse(String value) {try{return json.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception ex){throw new IllegalStateException(ex);}}
    private ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message) {return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    public enum Action {CONFIRM_PAYMENT,SUSPEND,REACTIVATE}
    public record Decision(
        @jakarta.validation.constraints.NotNull UUID id,
        @jakarta.validation.constraints.NotNull Action action,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=10,max=500) String reason,
        @jakarta.validation.constraints.Size(min=3,max=100) String reference,
        @jakarta.validation.constraints.Positive @jakarta.validation.constraints.Max(100000000) Long amountCents,
        PlanCode planCode) {}
}
