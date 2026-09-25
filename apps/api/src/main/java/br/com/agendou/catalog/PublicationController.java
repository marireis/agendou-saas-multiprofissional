package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/publication")
public class PublicationController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final ProfileController profiles;private final SubscriptionService subscriptions;
 public PublicationController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,ProfileController profiles,SubscriptionService subscriptions){this.jdbc=jdbc;this.tenants=tenants;this.profiles=profiles;this.subscriptions=subscriptions;}
 @GetMapping @Transactional public ResponseEntity<?> status(){
  var profile=profiles.get();var subscription=subscriptions.current(TenantContext.require());
  List<String> missing=new ArrayList<>(profile.missingFields());
  if(!Set.of("TRIAL_ACTIVE","TRIAL_EXPIRING","PAID_ACTIVE").contains(subscription.status().name()))missing.add("Assinatura ativa");
  if(jdbc.queryForObject("SELECT count(*) FROM services WHERE tenant_id=? AND active",Long.class,TenantContext.require())==0)missing.add("Ao menos um serviço ativo");
  var enabled=jdbc.query("SELECT enabled FROM payment_settings_versions WHERE tenant_id=? ORDER BY version DESC LIMIT 1",(r,n)->r.getBoolean(1),TenantContext.require());
  if(enabled.isEmpty()||!enabled.getFirst())missing.add("PIX e política configurados e ativos");
  missing.add("Calendário e horários disponíveis (próxima etapa)");
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("published",jdbc.queryForObject("SELECT published FROM public_profiles WHERE tenant_id=?",Boolean.class,TenantContext.require()),"canPublish",false,"missingRequirements",missing,"path","/a/"+profile.slug()));
 }
 @PostMapping @Transactional public void publish(){
  subscriptions.requireOperational(TenantContext.require());
  throw new ResponseStatusException(HttpStatus.CONFLICT,"A publicação depende do calendário e de horários disponíveis. Consulte os requisitos da página.");
 }
 @DeleteMapping @Transactional public ResponseEntity<Void> unpublish(){
  tenants.applyCurrentTenant();
  jdbc.update("UPDATE public_profiles SET published=false,updated_at=now() WHERE tenant_id=?",TenantContext.require());
  return ResponseEntity.noContent().build();
 }
}
