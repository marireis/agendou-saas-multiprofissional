package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.identity.AuthRateLimiter;
import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/public/{slug}")
public class CustomerJourneyController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final CalendarService calendar;private final ObjectMapper json;private final Clock clock;private final AuthRateLimiter limiter;private final String publicUrl;
 public CustomerJourneyController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,CalendarService calendar,ObjectMapper json,Clock clock,AuthRateLimiter limiter,@Value("${agendou.public-url}") String publicUrl){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.calendar=calendar;this.json=json;this.clock=clock;this.limiter=limiter;this.publicUrl=publicUrl;}
 public record Access(@NotNull UUID serviceId,@NotNull Instant start,@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=254) String email){}
 public record Token(@NotBlank @Size(max=128) String token){}
 public record Verified(String slug,UUID serviceId,Instant start,String name,String email,Instant expiresAt) implements java.io.Serializable{}
 private record Service(String name,int duration,int before,int after,long price,int deposit){SlotGenerator.Service timing(){return new SlotGenerator.Service(duration,before,after);}}
 private void bind(String slug){
  UUID tenant=jdbc.queryForObject("SELECT public.resolve_booking_tenant(?)",UUID.class,slug);
  if(tenant==null)throw unavailable();TenantContext.set(tenant);tenants.applyCurrentTenant();subscriptions.requireOperational(tenant);
  if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT published FROM public_profiles WHERE tenant_id=?",Boolean.class,tenant)))throw unavailable();
  var enabled=jdbc.query("SELECT enabled FROM payment_settings_versions WHERE tenant_id=? ORDER BY version DESC LIMIT 1",(r,n)->r.getBoolean(1),tenant);
  if(enabled.isEmpty()||!enabled.getFirst())throw unavailable();
 }
 private Service service(UUID id){var services=jdbc.query("SELECT name,duration_minutes,buffer_before_minutes,buffer_after_minutes,price_cents,deposit_percent FROM services WHERE tenant_id=? AND id=? AND active",(r,n)->new Service(r.getString(1),r.getInt(2),r.getInt(3),r.getInt(4),r.getLong(5),r.getInt(6)),TenantContext.require(),id);if(services.isEmpty())throw unavailable();return services.getFirst();}
 private AvailabilityController.Schedule schedule()throws Exception{var rows=jdbc.query("SELECT schedule::text FROM availability_settings WHERE tenant_id=?",(r,n)->r.getString(1),TenantContext.require());return rows.isEmpty()?new AvailabilityController.Schedule(List.of(),List.of(),0):json.readValue(rows.getFirst(),AvailabilityController.Schedule.class);}
 private List<SlotGenerator.Slot> slots(Service service,LocalDate date)throws Exception{return SlotGenerator.generate(schedule(),service.timing(),calendar.zone(),date,clock.instant(),calendar.occupied()).candidates();}
 private boolean available(Service service,Instant start)throws Exception{return slots(service,start.atZone(calendar.zone()).toLocalDate()).stream().anyMatch(s->s.start().equals(start));}
 @GetMapping("/availability") @Transactional public ResponseEntity<?> availability(@PathVariable String slug,@RequestParam UUID serviceId,@RequestParam(required=false) LocalDate date)throws Exception{
  try{bind(slug);var service=service(serviceId);var zone=calendar.zone();var now=clock.instant();var today=now.atZone(zone).toLocalDate();var config=schedule();var occupied=calendar.occupied();
   if(date!=null&&(date.isBefore(today)||!date.isBefore(today.plusDays(60))))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Escolha uma data nos próximos 60 dias.");
   var days=new ArrayList<Map<String,Object>>();for(int i=0;i<60;i++){var day=today.plusDays(i);days.add(Map.of("date",day,"available",!SlotGenerator.generate(config,service.timing(),zone,day,now,occupied).candidates().isEmpty()));}
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("timezone",zone.getId(),"today",today,"days",days,"slots",date==null?List.of():SlotGenerator.generate(config,service.timing(),zone,date,now,occupied).candidates(),"reservationEnabled",false));
  }finally{TenantContext.clear();}
 }
 @PostMapping("/access-links") @Transactional public ResponseEntity<?> request(@PathVariable String slug,@Valid @RequestBody Access body)throws Exception{
  limiter.checkAccount(body.email(),false);
  try{bind(slug);var service=service(body.serviceId());if(!available(service,body.start()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Este horário não está mais disponível. Escolha outro.");
   String email=body.email().trim().toLowerCase(Locale.ROOT);var tenant=TenantContext.require();var now=clock.instant();
   var old=jdbc.query("SELECT token_hash FROM customer_access_tokens WHERE tenant_id=? AND email=?",(r,n)->r.getString(1),tenant,email);
   for(var hash:old)jdbc.update("UPDATE mail_outbox SET status='CANCELED',body='',recipient='' WHERE token_hash=? AND status='PENDING'",hash);
   jdbc.update("DELETE FROM customer_access_tokens WHERE tenant_id=? AND email=?",tenant,email);
   byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);var token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);var hash=hash(token);var expires=Timestamp.from(now.plusSeconds(900));
   jdbc.update("INSERT INTO customer_access_tokens(token_hash,tenant_id,service_id,starts_at,customer_name,email,expires_at) VALUES (?,?,?,?,?,?,?)",hash,tenant,body.serviceId(),Timestamp.from(body.start()),body.name().trim(),email,expires);
   jdbc.update("INSERT INTO mail_outbox(id,recipient,subject,body,purpose,token_hash,expires_at,created_at,next_attempt_at) VALUES (?,?,?,?,'CUSTOMER_ACCESS',?,?,?,?)",UUID.randomUUID(),email,"Agendou: confira sua solicitação",publicUrl+"/a/"+slug+"/agendar#token="+token+"\nConfirme seu email para revisar os dados. Link de uso único, válido por 15 minutos. Nenhum horário foi reservado ainda.",hash,expires,Timestamp.from(now),Timestamp.from(now));
   return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("message","Se a solicitação puder ser atendida, você receberá um link. Confira seu email e a pasta de spam."));
  }finally{TenantContext.clear();}
 }
 @PostMapping("/access-links/consume") @Transactional public ResponseEntity<?> consume(@PathVariable String slug,@Valid @RequestBody Token body,HttpServletRequest request)throws Exception{
  try{bind(slug);var records=jdbc.query("DELETE FROM customer_access_tokens WHERE tenant_id=? AND token_hash=? AND expires_at>? RETURNING service_id,starts_at,customer_name,email",(r,n)->new Verified(slug,r.getObject(1,UUID.class),r.getTimestamp(2).toInstant(),r.getString(3),r.getString(4),clock.instant().plusSeconds(900)),TenantContext.require(),hash(body.token()),Timestamp.from(clock.instant()));
   if(records.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Link inválido ou expirado. Solicite outro email.");
   var verified=records.getFirst();var result=reviewData(verified);
   jdbc.update("UPDATE mail_outbox SET status='CANCELED',recipient='',body='' WHERE token_hash=? AND status='PENDING'",hash(body.token()));
   request.getSession(true);request.changeSessionId();request.getSession().setAttribute("customerReview",verified);
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }finally{TenantContext.clear();}
 }
 @GetMapping("/review") @Transactional public ResponseEntity<?> review(@PathVariable String slug,HttpServletRequest request)throws Exception{
  var session=request.getSession(false);var value=session==null?null:session.getAttribute("customerReview");
  if(!(value instanceof Verified verified)||!verified.slug().equals(slug)||!clock.instant().isBefore(verified.expiresAt()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verifique seu email para revisar a solicitação.");
  try{bind(slug);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviewData(verified));}finally{TenantContext.clear();}
 }
 private Map<String,Object> reviewData(Verified value)throws Exception{var service=service(value.serviceId());var deposit=(service.price()*service.deposit()+99)/100;return Map.of("name",value.name(),"email",value.email(),"serviceName",service.name(),"start",value.start(),"end",value.start().plusSeconds(service.duration()*60L),"timezone",calendar.zone().getId(),"priceCents",service.price(),"depositCents",deposit,"slotAvailable",available(service,value.start()),"reservationEnabled",false);}
 private static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Página ou serviço indisponível.");}
}
