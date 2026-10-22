package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import br.com.agendou.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/payment-reviews")
public class PaymentReviewController {
 private final JdbcTemplate jdbc; private final TenantSessionConfigurer tenants; private final SubscriptionService subscriptions;
 private final BookingService bookings; private final EvidenceStorage storage; private final Clock clock;
 public PaymentReviewController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,BookingService bookings,EvidenceStorage storage,Clock clock){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.bookings=bookings;this.storage=storage;this.clock=clock;}
 private UUID lock(){tenants.applyCurrentTenant();UUID tenant=TenantContext.require();jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenant);bookings.expireCurrent();return tenant;}
 private ResponseEntity<Object> response(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
 @GetMapping @Transactional public ResponseEntity<Object> list(@RequestParam(defaultValue="0") int offset){
  if(offset<0||offset>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página inválida.");UUID tenant=lock();
  var rows=jdbc.query("SELECT b.id,b.status,c.name,b.service_snapshot->>'name',b.starts_at,b.timezone,b.deposit_cents,coalesce(b.review_expires_at,b.expires_at) FROM bookings b JOIN customers c ON c.tenant_id=b.tenant_id AND c.id=b.customer_id WHERE b.tenant_id=? ORDER BY CASE b.status WHEN 'IN_REVIEW' THEN 0 WHEN 'AWAITING_PAYMENT' THEN 1 WHEN 'CONFIRMED' THEN 2 ELSE 3 END,b.created_at DESC,b.id LIMIT 51 OFFSET ?",(r,n)->Map.of("id",r.getObject(1,UUID.class),"status",r.getString(2),"customerName",r.getString(3),"serviceName",r.getString(4),"start",r.getTimestamp(5).toInstant(),"timezone",r.getString(6),"depositCents",r.getLong(7),"deadline",r.getTimestamp(8).toInstant()),tenant,offset);
  return response(Map.of("items",rows.stream().limit(50).toList(),"hasMore",rows.size()>50));
 }
 private Map<String,Object> detail(UUID id){
  var rows=jdbc.query("SELECT b.id,b.status,c.name,b.service_snapshot->>'name',b.starts_at,b.timezone,b.price_cents,b.deposit_cents,coalesce(b.review_expires_at,b.expires_at),p.id,b.created_at FROM bookings b JOIN customers c ON c.tenant_id=b.tenant_id AND c.id=b.customer_id JOIN payment_intents p ON p.tenant_id=b.tenant_id AND p.booking_id=b.id WHERE b.tenant_id=? AND b.id=?",(r,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getObject(1,UUID.class));m.put("status",r.getString(2));m.put("customerName",r.getString(3));m.put("serviceName",r.getString(4));m.put("start",r.getTimestamp(5).toInstant());m.put("timezone",r.getString(6));m.put("totalCents",r.getLong(7));m.put("depositCents",r.getLong(8));m.put("deadline",r.getTimestamp(9).toInstant());m.put("intentId",r.getObject(10,UUID.class));m.put("createdAt",r.getTimestamp(11).toInstant());return m;},TenantContext.require(),id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reserva não encontrada.");var m=rows.getFirst();UUID tenant=TenantContext.require();Object intent=m.get("intentId");
  long paid=jdbc.queryForObject("SELECT coalesce(sum(amount_cents),0) FROM payment_transactions WHERE tenant_id=? AND intent_id=?",Long.class,tenant,intent);
  long refunded=jdbc.queryForObject("SELECT coalesce(sum(r.amount_cents),0) FROM payment_refunds r JOIN payment_transactions t ON t.tenant_id=r.tenant_id AND t.id=r.transaction_id WHERE t.tenant_id=? AND t.intent_id=?",Long.class,tenant,intent);
  m.put("receivedCents",paid-refunded);m.put("grossReceivedCents",paid);m.put("refundedCents",refunded);m.put("financialStatus",financialStatus((String)m.get("status"),paid-refunded,((Number)m.get("depositCents")).longValue(),((Number)m.get("totalCents")).longValue(),refunded));m.put("refunds",jdbc.query("SELECT r.id,r.transaction_id,r.amount_cents,r.bank_reference,r.reason,r.refunded_at FROM payment_refunds r JOIN payment_transactions t ON t.tenant_id=r.tenant_id AND t.id=r.transaction_id WHERE t.tenant_id=? AND t.intent_id=? ORDER BY r.created_at,r.id",(r,n)->Map.of("id",r.getObject(1,UUID.class),"transactionId",r.getObject(2,UUID.class),"amountCents",r.getLong(3),"bankReference",r.getString(4),"reason",r.getString(5),"refundedAt",r.getTimestamp(6).toInstant()),tenant,intent));m.put("evidence",jdbc.query("SELECT id,created_at FROM payment_evidence WHERE tenant_id=? AND intent_id=? ORDER BY created_at,id",(r,n)->Map.of("id",r.getObject(1,UUID.class),"createdAt",r.getTimestamp(2).toInstant()),tenant,intent));
  m.put("transactions",jdbc.query("SELECT id,amount_cents,bank_reference,receiving_account,received_at FROM payment_transactions WHERE tenant_id=? AND intent_id=? ORDER BY created_at,id",(r,n)->Map.of("id",r.getObject(1,UUID.class),"amountCents",r.getLong(2),"bankReference",r.getString(3),"receivingAccount",r.getString(4),"receivedAt",r.getTimestamp(5).toInstant()),tenant,intent));m.put("cancellationPolicy",jdbc.queryForObject("SELECT pix_snapshot->>'cancellationPolicy' FROM bookings WHERE tenant_id=? AND id=?",String.class,tenant,id));CancellationView.add(jdbc,id,m,paid-refunded);m.put("reschedules",BookingRescheduleController.history(jdbc,id));m.put("scheduleVersion",jdbc.queryForObject("SELECT schedule_version FROM bookings WHERE tenant_id=? AND id=?",Integer.class,tenant,id));m.put("end",jdbc.queryForObject("SELECT ends_at FROM bookings WHERE tenant_id=? AND id=?",Timestamp.class,tenant,id).toInstant());BookingOutcomeController.add(jdbc,id,m);return m;
 }
 @GetMapping("/{id}") @Transactional public ResponseEntity<Object> get(@PathVariable UUID id){lock();return response(detail(id));}
 @GetMapping("/{id}/evidence/{evidence}") @Transactional public ResponseEntity<byte[]> download(@PathVariable UUID id,@PathVariable UUID evidence){
  tenants.applyCurrentTenant();var d=detail(id);var rows=jdbc.query("SELECT object_key,media_type FROM payment_evidence WHERE tenant_id=? AND intent_id=? AND id=?",(r,n)->new String[]{r.getString(1),r.getString(2)},TenantContext.require(),d.get("intentId"),evidence);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Comprovante não encontrado.");var file=rows.getFirst();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox").header("Content-Disposition","attachment; filename=\""+evidence+(file[1].equals("application/pdf")?".pdf":".png")+"\"").contentType(MediaType.parseMediaType(file[1])).body(storage.get(file[0]));
 }
 public record Receipt(@NotNull @Min(1) @Max(1000000000) Long amountCents,@NotBlank @Size(max=200) String bankReference,@NotBlank @Size(min=2,max=120) String receivingAccount,@NotNull Instant receivedAt,@NotNull @AssertTrue Boolean verified){@Override public String toString(){return "Receipt[redacted]";}}
 @PostMapping("/{id}/receipts") @Transactional public ResponseEntity<Object> receive(@PathVariable UUID id,@Valid @RequestBody Receipt input,HttpServletRequest request){
  UUID tenant=lock();subscriptions.requireOperational(tenant);var d=detail(id);String reference=input.bankReference().trim().toUpperCase(Locale.ROOT);String account=input.receivingAccount().trim();
  if(reference.isEmpty()||account.length()<2)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe a referência bancária e a conta recebedora.");
  Instant received=input.receivedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  var old=jdbc.queryForList("SELECT intent_id,amount_cents,receiving_account,received_at FROM payment_transactions WHERE tenant_id=? AND bank_reference=?",tenant,reference);
  if(!old.isEmpty()){var row=old.getFirst();if(row.get("intent_id").equals(d.get("intentId"))&&((Number)row.get("amount_cents")).longValue()==input.amountCents()&&row.get("receiving_account").equals(account)&&((Timestamp)row.get("received_at")).toInstant().equals(received))return response(d);throw conflict("Referência bancária já registrada. Confira o histórico antes de continuar.");}
  
  if(received.isBefore((Instant)d.get("createdAt"))||received.isAfter(clock.instant()))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"A data do recebimento deve estar entre a solicitação e o momento atual.");
  
  UUID actor=UUID.fromString(request.getUserPrincipal().getName());
  jdbc.update("INSERT INTO payment_transactions(id,tenant_id,intent_id,amount_cents,bank_reference,receiving_account,received_at,recorded_by,correlation_id) VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,d.get("intentId"),input.amountCents(),reference,account,Timestamp.from(received),actor,CorrelationIdFilter.id(request));
  jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'PAYMENT_RECORDED')",UUID.randomUUID(),tenant,id);
  confirmIfEligible(id,detail(id));
  return response(detail(id));
 }
 private void confirmIfEligible(UUID id,Map<String,Object> d){UUID tenant=TenantContext.require();long net=((Number)d.get("receivedCents")).longValue();if(Set.of("AWAITING_PAYMENT","IN_REVIEW").contains(d.get("status"))&&clock.instant().isBefore((Instant)d.get("deadline"))&&net>=((Number)d.get("depositCents")).longValue()&&net<=((Number)d.get("totalCents")).longValue()){
   int changed=jdbc.update("UPDATE calendar_allocations SET kind='BOOKING',expires_at=NULL WHERE tenant_id=? AND id=(SELECT allocation_id FROM bookings WHERE tenant_id=? AND id=?) AND active AND kind='HOLD' AND expires_at>?",tenant,tenant,id,Timestamp.from(clock.instant()));
   if(changed!=1)throw conflict("O horário não está mais reservado. Nenhum recebimento foi registrado.");
   jdbc.update("UPDATE bookings SET status='CONFIRMED' WHERE tenant_id=? AND id=?",tenant,id);jdbc.update("UPDATE payment_intents SET status='CONFIRMED' WHERE tenant_id=? AND id=?",tenant,d.get("intentId"));jdbc.update("UPDATE booking_usage SET state='COMMITTED' WHERE tenant_id=? AND booking_id=?",tenant,id);
   jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'CONFIRMED')",UUID.randomUUID(),tenant,id);
  }

 }
 static String financialStatus(String status,long net,long deposit,long total,long refunded){
  if(net==0)return refunded>0?"REFUNDED":"UNPAID";
  if("EXPIRED".equals(status))return "LATE";
  if(net>total)return "EXCESS";
  if(net<deposit)return "INSUFFICIENT";
  return net==total?"PAID":"DEPOSIT_PAID";
 }
 public record Refund(@NotNull UUID transactionId,@NotNull @Min(1) @Max(1000000000) Long amountCents,@NotBlank @Size(max=200) String bankReference,@NotBlank @Size(min=5,max=1000) String reason,@NotNull Instant refundedAt,@NotNull @AssertTrue Boolean verified){@Override public String toString(){return "Refund[redacted]";}}
 @PostMapping("/{id}/refunds") @Transactional public ResponseEntity<Object> refund(@PathVariable UUID id,@Valid @RequestBody Refund input,HttpServletRequest request){
  UUID tenant=lock();subscriptions.requireOperational(tenant);var d=detail(id);String reference=input.bankReference().trim().toUpperCase(Locale.ROOT),reason=input.reason().trim();Instant at=input.refundedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  if(reference.isEmpty()||reason.length()<5)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe referência bancária e motivo da devolução.");
  var transactions=jdbc.queryForList("SELECT amount_cents,received_at FROM payment_transactions WHERE tenant_id=? AND intent_id=? AND id=?",tenant,d.get("intentId"),input.transactionId());if(transactions.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Recebimento não encontrado.");
  var old=jdbc.queryForList("SELECT transaction_id,amount_cents,reason,refunded_at FROM payment_refunds WHERE tenant_id=? AND bank_reference=?",tenant,reference);if(!old.isEmpty()){var row=old.getFirst();if(row.get("transaction_id").equals(input.transactionId())&&((Number)row.get("amount_cents")).longValue()==input.amountCents()&&row.get("reason").equals(reason)&&((Timestamp)row.get("refunded_at")).toInstant().equals(at))return response(d);throw conflict("Referência de devolução já registrada com outros dados.");}
  var transaction=transactions.getFirst();if(at.isAfter(clock.instant())||at.isBefore(((Timestamp)transaction.get("received_at")).toInstant()))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Data de devolução inválida: deve estar entre o recebimento e agora.");
  long already=jdbc.queryForObject("SELECT coalesce(sum(amount_cents),0) FROM payment_refunds WHERE tenant_id=? AND transaction_id=?",Long.class,tenant,input.transactionId());if(input.amountCents()>((Number)transaction.get("amount_cents")).longValue()-already)throw conflict("Devolução supera o valor disponível deste recebimento.");
  if("CONFIRMED".equals(d.get("status"))&&((Number)d.get("receivedCents")).longValue()-input.amountCents()<((Number)d.get("depositCents")).longValue())throw conflict("A devolução reduziria a entrada de um atendimento confirmado. Cancele o atendimento e registre a decisão conforme a política antes de devolver a entrada.");
  if("CANCELED".equals(d.get("status"))&&input.amountCents()>((Number)d.get("refundDueCents")).longValue())throw conflict("Valor supera a devolução prevista no cancelamento.");
  jdbc.update("INSERT INTO payment_refunds(id,tenant_id,transaction_id,amount_cents,reason,bank_reference,refunded_at,recorded_by,correlation_id) VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,input.transactionId(),input.amountCents(),reason,reference,Timestamp.from(at),UUID.fromString(request.getUserPrincipal().getName()),CorrelationIdFilter.id(request));
  jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'REFUND_RECORDED')",UUID.randomUUID(),tenant,id);confirmIfEligible(id,detail(id));return response(detail(id));
 }
 public record Cancel(@NotBlank @Size(min=5,max=1000) String reason,@NotNull @Min(0) Long expectedNetCents,@NotNull @Min(0) Long refundCents,@NotNull @AssertTrue Boolean policyReviewed){@Override public String toString(){return "Cancel[redacted]";}}
 @PostMapping("/{id}/cancel") @Transactional public ResponseEntity<Object> cancel(@PathVariable UUID id,@Valid @RequestBody Cancel input,HttpServletRequest request){
  UUID tenant=lock();subscriptions.requireOperational(tenant);var d=detail(id);String reason=input.reason().trim();
  if(reason.length()<5||input.refundCents()>input.expectedNetCents())throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Revise o motivo e o valor da devolução, limitado ao recebido líquido.");
  var old=jdbc.queryForList("SELECT reason,expected_net_cents,retained_cents FROM booking_cancellations WHERE tenant_id=? AND booking_id=?",tenant,id);
  if(!old.isEmpty()){var row=old.getFirst();if(row.get("reason").equals(reason)&&((Number)row.get("expected_net_cents")).longValue()==input.expectedNetCents()&&((Number)row.get("retained_cents")).longValue()==input.expectedNetCents()-input.refundCents())return response(d);throw conflict("Cancelamento já registrado com outra decisão. Consulte o histórico.");}
  if(!Set.of("AWAITING_PAYMENT","IN_REVIEW","CONFIRMED").contains(d.get("status"))||!clock.instant().isBefore((Instant)d.get("start")))throw conflict("Somente reservas ativas antes do atendimento podem ser canceladas.");
  if(((Number)d.get("receivedCents")).longValue()!=input.expectedNetCents())throw conflict("Os valores mudaram. Atualize a reserva e revise a devolução antes de cancelar.");
  jdbc.update("INSERT INTO booking_cancellations(tenant_id,booking_id,recorded_by,reason,expected_net_cents,retained_cents,correlation_id) VALUES (?,?,?,?,?,?,?)",tenant,id,UUID.fromString(request.getUserPrincipal().getName()),reason,input.expectedNetCents(),input.expectedNetCents()-input.refundCents(),CorrelationIdFilter.id(request));
  jdbc.update("UPDATE bookings SET status='CANCELED' WHERE tenant_id=? AND id=?",tenant,id);jdbc.update("UPDATE payment_intents SET status='CANCELED' WHERE tenant_id=? AND id=?",tenant,d.get("intentId"));
  jdbc.update("UPDATE calendar_allocations SET active=false,released_at=? WHERE tenant_id=? AND id=(SELECT allocation_id FROM bookings WHERE tenant_id=? AND id=?)",Timestamp.from(clock.instant()),tenant,tenant,id);
  // Confirmed bookings consumed the commercial quota; only provisional usage is released.
  jdbc.update("UPDATE booking_usage SET state='RELEASED' WHERE tenant_id=? AND booking_id=? AND state='HELD'",tenant,id);
  jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'CANCELED')",UUID.randomUUID(),tenant,id);return response(detail(id));
 }
 private static ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
}
