package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/publication")
public class PublicationController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final ProfileController profiles;private final SubscriptionService subscriptions;private final ObjectMapper json;private final CalendarService calendar;private final Clock clock;
 public PublicationController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,ProfileController profiles,SubscriptionService subscriptions,ObjectMapper json,CalendarService calendar,Clock clock){this.jdbc=jdbc;this.tenants=tenants;this.profiles=profiles;this.subscriptions=subscriptions;this.json=json;this.calendar=calendar;this.clock=clock;}
 public record Status(boolean published,boolean canPublish,List<String> missingRequirements,String path){}
 private Status requirements()throws Exception{
  // The same tenant lock used by configuration and allocation mutations keeps this decision consistent.
  var subscription=subscriptions.current(TenantContext.require());var profile=profiles.get();
  List<String> missing=new ArrayList<>(profile.missingFields());
  if(!Set.of("TRIAL_ACTIVE","TRIAL_EXPIRING","PAID_ACTIVE").contains(subscription.status().name()))missing.add("Assinatura ativa");
  var services=jdbc.query("SELECT duration_minutes,buffer_before_minutes,buffer_after_minutes FROM services WHERE tenant_id=? AND active",(r,n)->new SlotGenerator.Service(r.getInt(1),r.getInt(2),r.getInt(3)),TenantContext.require());
  if(services.isEmpty())missing.add("Ao menos um serviço ativo");
  var enabled=jdbc.query("SELECT enabled FROM payment_settings_versions WHERE tenant_id=? ORDER BY version DESC LIMIT 1",(r,n)->r.getBoolean(1),TenantContext.require());
  if(enabled.isEmpty()||!enabled.getFirst())missing.add("PIX e política configurados e ativos");
  var configs=jdbc.query("SELECT schedule::text FROM availability_settings WHERE tenant_id=?",(r,n)->r.getString(1),TenantContext.require());
  boolean available=false;
  if(!configs.isEmpty()&&!services.isEmpty()){
   var schedule=json.readValue(configs.getFirst(),AvailabilityController.Schedule.class);var occupied=calendar.occupied();
   var now=clock.instant();var zone=ZoneId.of(profile.timezone());var today=now.atZone(zone).toLocalDate();
   for(int i=0;i<60&&!available;i++){var date=today.plusDays(i);available=services.stream().anyMatch(service->!SlotGenerator.generate(schedule,service,zone,date,now,occupied).candidates().isEmpty());}
  }
  if(!available)missing.add("Ao menos um horário livre para um serviço ativo nos próximos 60 dias, respeitando antecedência de 24h, duração, intervalos e bloqueios");
  return new Status(jdbc.queryForObject("SELECT published FROM public_profiles WHERE tenant_id=?",Boolean.class,TenantContext.require()),missing.isEmpty(),List.copyOf(missing),"/a/"+profile.slug());
 }
 @GetMapping @Transactional public ResponseEntity<Status> status()throws Exception{
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requirements());
 }
 @PostMapping @Transactional public ResponseEntity<Void> publish()throws Exception{
  subscriptions.requireOperational(TenantContext.require());
  var state=requirements();
  if(!state.canPublish())throw new ResponseStatusException(HttpStatus.CONFLICT,"Complete os requisitos antes de publicar: "+String.join("; ",state.missingRequirements()));
  jdbc.update("UPDATE public_profiles SET published=true,updated_at=now() WHERE tenant_id=? AND NOT published",TenantContext.require());
  return ResponseEntity.noContent().build();
 }
 @DeleteMapping @Transactional public ResponseEntity<Void> unpublish(){
  subscriptions.current(TenantContext.require());tenants.applyCurrentTenant();
  jdbc.update("UPDATE public_profiles SET published=false,updated_at=now() WHERE tenant_id=? AND published",TenantContext.require());
  return ResponseEntity.noContent().build();
 }
}
