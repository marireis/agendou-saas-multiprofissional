package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final CalendarService calendar;private final ObjectMapper json;private final Clock clock;private final PaymentProvider payments;
 public BookingService(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,CalendarService calendar,ObjectMapper json,Clock clock,PaymentProvider payments){this.payments=payments;this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.calendar=calendar;this.json=json;this.clock=clock;}
 record Quote(String service,String pix,int paymentVersion,long price,long deposit,String timezone,String fingerprint){}
 private Quote currentQuote(UUID id)throws Exception{
  var rows=jdbc.query("""
   SELECT jsonb_build_object('id',s.id,'name',s.name,'description',s.description,'durationMinutes',s.duration_minutes,
    'bufferBeforeMinutes',s.buffer_before_minutes,'bufferAfterMinutes',s.buffer_after_minutes,'priceCents',s.price_cents,
    'depositPercent',s.deposit_percent,'version',s.version,'businessName',t.display_name,'serviceMode',p.service_mode,'location',p.location)::text,
    s.price_cents,(s.price_cents::bigint*s.deposit_percent+99)/100,p.timezone
   FROM services s JOIN public_profiles p ON p.tenant_id=s.tenant_id JOIN tenants t ON t.id=s.tenant_id
   WHERE s.tenant_id=? AND s.id=? AND s.active
   """,(r,n)->new Object[]{r.getString(1),r.getLong(2),r.getLong(3),r.getString(4)},TenantContext.require(),id);
  if(rows.isEmpty())throw conflict("Serviço indisponível. Escolha outro atendimento.");
  var payment=jdbc.query("""
   SELECT version,enabled,jsonb_build_object('keyType',key_type,'pixKey',pix_key,'recipientName',recipient_name,
    'paymentInstructions',payment_instructions,'cancellationPolicy',cancellation_policy)::text
   FROM payment_settings_versions WHERE tenant_id=? ORDER BY version DESC LIMIT 1
   """,(r,n)->new Object[]{r.getInt(1),r.getBoolean(2),r.getString(3)},TenantContext.require());
  if(payment.isEmpty()||!(boolean)payment.getFirst()[1])throw conflict("O profissional precisa revisar a configuração de pagamento.");
  var row=rows.getFirst();var pay=payment.getFirst();String service=(String)row[0],pix=(String)pay[2],zone=(String)row[3];
  return new Quote(service,pix,(int)pay[0],(long)row[1],(long)row[2],zone,hash(service+"\n"+pay[0]+"\n"+zone));
 }
 public Map<String,Object> reviewQuote(UUID serviceId)throws Exception{var quote=currentQuote(serviceId);return Map.of("quote",quote.fingerprint(),"cancellationPolicy",json.readTree(quote.pix()).get("cancellationPolicy").asText());}
 private void bind(CustomerJourneyController.Verified actor){bind(actor.slug(),actor.email());}
 private void bind(String slug,String email){UUID tenant=jdbc.queryForObject("SELECT public.resolve_customer_tenant(?,?)",UUID.class,slug,email);if(tenant==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Página indisponível.");TenantContext.set(tenant);tenants.applyCurrentTenant();jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenant);}
 @Transactional(rollbackFor=Exception.class) public Map<String,Object> create(CustomerJourneyController.Verified actor,UUID key,String acceptedQuote)throws Exception{
  try{bind(actor);var tenant=TenantContext.require();expireCurrent();String requestHash=hash(actor.serviceId()+"\n"+actor.start()+"\n"+actor.name()+"\n"+acceptedQuote);
   var previous=jdbc.query("SELECT request_hash,response::text FROM booking_requests WHERE tenant_id=? AND actor_email=? AND request_key=?",(r,n)->new String[]{r.getString(1),r.getString(2)},tenant,actor.email(),key);
   if(!previous.isEmpty()){if(!previous.getFirst()[0].equals(requestHash))throw conflict("Esta chave já foi usada com outra solicitação.");return json.readValue(previous.getFirst()[1],new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
   var subscription=subscriptions.current(tenant);subscriptions.requireOperational(tenant);
   if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT published FROM public_profiles WHERE tenant_id=?",Boolean.class,tenant)))throw conflict("A página não está disponível para novas reservas.");
   var quote=currentQuote(actor.serviceId());if(!quote.fingerprint().equals(acceptedQuote))throw conflict("Os valores ou as condições mudaram. Atualize a revisão antes de solicitar.");
   var month=actor.start().atZone(ZoneId.of(quote.timezone())).toLocalDate().withDayOfMonth(1);
   Integer limit=jdbc.queryForObject("SELECT monthly_booking_limit FROM plans WHERE code=?",Integer.class,subscription.planCode().name());
   long used=jdbc.queryForObject("SELECT count(*) FROM booking_usage WHERE tenant_id=? AND month=? AND state IN ('HELD','COMMITTED')",Long.class,tenant,java.sql.Date.valueOf(month));
   if(limit!=null&&used>=limit)throw conflict("O limite de reservas do profissional neste mês foi atingido. Entre em contato com ele.");
   UUID allocation=calendar.hold(actor.serviceId(),actor.start());
   var span=jdbc.queryForMap("SELECT ends_at,expires_at FROM calendar_allocations WHERE tenant_id=? AND id=?",tenant,allocation);
   Instant end=((Timestamp)span.get("ends_at")).toInstant(),expires=((Timestamp)span.get("expires_at")).toInstant();
   UUID customer=UUID.randomUUID();jdbc.update("INSERT INTO customers(id,tenant_id,email,name) VALUES (?,?,?,?) ON CONFLICT(tenant_id,email) DO NOTHING",customer,tenant,actor.email(),actor.name());
   customer=jdbc.queryForObject("SELECT id FROM customers WHERE tenant_id=? AND email=?",UUID.class,tenant,actor.email());UUID booking=UUID.randomUUID();
   jdbc.update("""
    INSERT INTO bookings(id,tenant_id,customer_id,service_id,allocation_id,status,starts_at,ends_at,expires_at,timezone,service_snapshot,pix_snapshot,price_cents,deposit_cents,payment_version)
    VALUES (?,?,?,?,?,'AWAITING_PAYMENT',?,?,?,?,?::jsonb,?::jsonb,?,?,?)
    """,booking,tenant,customer,actor.serviceId(),allocation,Timestamp.from(actor.start()),Timestamp.from(end),Timestamp.from(expires),quote.timezone(),quote.service(),quote.pix(),quote.price(),quote.deposit(),quote.paymentVersion());
   jdbc.update("INSERT INTO payment_intents(id,tenant_id,booking_id,provider,status,amount_due_cents,deadline_at) VALUES (?,?,?,'MANUAL_PIX','AWAITING_PAYMENT',?,?)",UUID.randomUUID(),tenant,booking,quote.deposit(),Timestamp.from(expires));
   jdbc.update("INSERT INTO booking_usage(tenant_id,booking_id,month,state) VALUES (?,?,?,'HELD')",tenant,booking,java.sql.Date.valueOf(month));
   jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'REQUESTED')",UUID.randomUUID(),tenant,booking);
   var response=receipt(booking,actor.email());
   jdbc.update("INSERT INTO booking_requests(tenant_id,actor_email,request_key,request_hash,booking_id,response) VALUES (?,?,?,?,?,?::jsonb)",tenant,actor.email(),key,requestHash,booking,json.writeValueAsString(response));
   jdbc.update("""
    INSERT INTO mail_outbox(id,recipient,subject,body,purpose,booking_id,expires_at,created_at,next_attempt_at)
    VALUES (?,?,?,?,'BOOKING_REQUEST',?,?,?,?)
    """,UUID.randomUUID(),actor.email(),"Agendou: solicitação registrada","Sua solicitação "+booking+" foi registrada. O horário fica ocupado temporariamente até "+expires.atZone(ZoneId.of(quote.timezone()))+". Não é atendimento confirmado. A etapa de pagamento online ainda não está habilitada; não faça transferência por esta mensagem.",booking,Timestamp.from(expires),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
   return response;
  }finally{TenantContext.clear();}
 }
 @Transactional public Map<String,Object> get(CustomerJourneyController.Verified actor,UUID id){try{bind(actor);expireCurrent();return receipt(id,actor.email());}finally{TenantContext.clear();}}
 @Transactional public Map<String,Object> listForCustomer(String slug,String email,int offset){
  try{bind(slug,email);expireCurrent();
   var ids=jdbc.query("SELECT b.id FROM bookings b JOIN customers c ON c.tenant_id=b.tenant_id AND c.id=b.customer_id WHERE b.tenant_id=? AND c.email=? ORDER BY b.starts_at DESC,b.id DESC LIMIT 21 OFFSET ?",(r,n)->r.getObject(1,UUID.class),TenantContext.require(),email,offset);
   var items=ids.stream().limit(20).map(id->receipt(id,email)).toList();
   return Map.of("items",items,"hasMore",ids.size()>20,"offset",offset,"email",email);
  }finally{TenantContext.clear();}
 }
 @Transactional public Map<String,Object> detailForCustomer(String slug,String email,UUID id){
  try{bind(slug,email);expireCurrent();var result=receipt(id,email);
   var details=jdbc.queryForMap("SELECT service_snapshot->>'businessName' AS business_name,service_snapshot->>'serviceMode' AS service_mode,service_snapshot->>'location' AS location,pix_snapshot->>'cancellationPolicy' AS cancellation_policy FROM bookings WHERE tenant_id=? AND id=?",TenantContext.require(),id);
   result.put("businessName",details.get("business_name"));result.put("serviceMode",details.get("service_mode"));result.put("location",details.get("location"));result.put("cancellationPolicy",details.get("cancellation_policy"));
   result.put("events",jdbc.query("SELECT event_type,created_at FROM booking_events WHERE tenant_id=? AND booking_id=? ORDER BY created_at,id",(r,n)->Map.of("type",r.getString(1),"at",r.getTimestamp(2).toInstant()),TenantContext.require(),id));
   return result;
  }finally{TenantContext.clear();}
 }
 @Transactional public Map<String,Object> paymentForCustomer(String slug,String email,UUID id){
  try{bind(slug,email);expireCurrent();receipt(id,email);return payments.view(id);}finally{TenantContext.clear();}
 }
 private Map<String,Object> receipt(UUID id,String email){
  var rows=jdbc.query("""
   SELECT b.id,b.status,b.starts_at,b.ends_at,b.expires_at,b.timezone,b.price_cents,b.deposit_cents,b.service_snapshot->>'name'
   FROM bookings b JOIN customers c ON c.tenant_id=b.tenant_id AND c.id=b.customer_id
   WHERE b.tenant_id=? AND b.id=? AND c.email=?
   """,(r,n)->{Map<String,Object> result=new LinkedHashMap<>();result.put("id",r.getObject(1,UUID.class));result.put("status",r.getString(2));result.put("start",r.getTimestamp(3).toInstant());result.put("end",r.getTimestamp(4).toInstant());result.put("expiresAt",r.getTimestamp(5).toInstant());result.put("timezone",r.getString(6));result.put("priceCents",r.getLong(7));result.put("depositCents",r.getLong(8));result.put("serviceName",r.getString(9));result.put("paymentAvailable",false);return result;},TenantContext.require(),id,email);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Solicitação não encontrada.");return rows.getFirst();
 }
 // Caller holds the tenant lock. Expiration preserves all snapshots, events and idempotency receipts.
 public void expireCurrent(){
  expireCurrent(Integer.MAX_VALUE);
 }
 public int expireCurrent(int limit){
  var tenant=TenantContext.require();var now=Timestamp.from(clock.instant());var expired=jdbc.query("UPDATE bookings SET status='EXPIRED' WHERE tenant_id=? AND id IN (SELECT id FROM bookings WHERE tenant_id=? AND status='AWAITING_PAYMENT' AND expires_at<=? ORDER BY expires_at,id LIMIT ?) RETURNING id,allocation_id",(r,n)->new UUID[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class)},tenant,tenant,now,limit);
  for(var row:expired){jdbc.update("UPDATE payment_intents SET status='EXPIRED' WHERE tenant_id=? AND booking_id=? AND status='AWAITING_PAYMENT'",tenant,row[0]);jdbc.update("UPDATE calendar_allocations SET active=false,released_at=coalesce(released_at,?) WHERE tenant_id=? AND id=?",now,tenant,row[1]);jdbc.update("UPDATE booking_usage SET state='RELEASED' WHERE tenant_id=? AND booking_id=? AND state='HELD'",tenant,row[0]);jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'EXPIRED')",UUID.randomUUID(),tenant,row[0]);}
  return expired.size();
 }
 @Transactional public void expireTenant(UUID tenant){TenantContext.set(tenant);try{tenants.applyCurrentTenant();jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenant);expireCurrent();}finally{TenantContext.clear();}}
 private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
