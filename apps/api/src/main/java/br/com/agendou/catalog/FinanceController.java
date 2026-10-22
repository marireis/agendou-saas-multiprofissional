package br.com.agendou.catalog;

import br.com.agendou.tenancy.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/finance")
public class FinanceController {
 private final JdbcTemplate jdbc;
 private final TenantSessionConfigurer tenants;
 private final Clock clock;
 public FinanceController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,Clock clock){this.jdbc=jdbc;this.tenants=tenants;this.clock=clock;}
 public enum View {MOVEMENTS,BALANCES}
 private static final String MOVEMENTS="""
  WITH movements AS (
   SELECT t.id,p.booking_id,t.received_at AS at,t.amount_cents AS amount,'RECEIPT' AS kind FROM payment_transactions t
   JOIN payment_intents p ON p.tenant_id=t.tenant_id AND p.id=t.intent_id WHERE t.tenant_id=?
   UNION ALL
   SELECT r.id,p.booking_id,r.refunded_at,r.amount_cents,'REFUND' FROM payment_refunds r
   JOIN payment_transactions t ON t.tenant_id=r.tenant_id AND t.id=r.transaction_id
   JOIN payment_intents p ON p.tenant_id=t.tenant_id AND p.id=t.intent_id WHERE r.tenant_id=?
  ), period AS (SELECT * FROM movements WHERE at>=? AND at<?)
  """;
 private static final String BALANCES="""
  WITH amounts AS (
   SELECT b.*,b.service_snapshot->>'name' AS service_name,c.name AS customer_name,
    CASE WHEN b.status IN ('AWAITING_PAYMENT','IN_REVIEW') AND coalesce(b.review_expires_at,b.expires_at)<=? THEN 'EXPIRED' ELSE b.status END AS current_status,
    coalesce((SELECT sum(t.amount_cents) FROM payment_transactions t WHERE t.tenant_id=p.tenant_id AND t.intent_id=p.id),0)
    -coalesce((SELECT sum(r.amount_cents) FROM payment_refunds r JOIN payment_transactions t ON t.tenant_id=r.tenant_id AND t.id=r.transaction_id WHERE t.tenant_id=p.tenant_id AND t.intent_id=p.id),0) AS net,
    x.retained_cents
   FROM bookings b JOIN customers c ON c.tenant_id=b.tenant_id AND c.id=b.customer_id
   JOIN payment_intents p ON p.tenant_id=b.tenant_id AND p.booking_id=b.id
   LEFT JOIN booking_cancellations x ON x.tenant_id=b.tenant_id AND x.booking_id=b.id
   WHERE b.tenant_id=? AND b.starts_at>=? AND b.starts_at<?
  ), balances AS (
   SELECT *,
    CASE WHEN current_status IN ('AWAITING_PAYMENT','IN_REVIEW') THEN greatest(0,deposit_cents-net) ELSE 0 END AS deposit_due,
    CASE WHEN current_status NOT IN ('CANCELED','EXPIRED') THEN greatest(0,price_cents-net) ELSE 0 END AS total_due,
    CASE WHEN current_status='CANCELED' THEN greatest(0,net-coalesce(retained_cents,0)) ELSE 0 END AS refund_due,
    CASE WHEN current_status='EXPIRED' THEN greatest(0,net) WHEN current_status<>'CANCELED' THEN greatest(0,net-price_cents) ELSE 0 END AS review_amount
   FROM amounts
  )
  """;
 @GetMapping
 @Transactional(isolation=Isolation.REPEATABLE_READ)
 public ResponseEntity<?> get(@RequestParam LocalDate from,@RequestParam LocalDate to,
   @RequestParam(defaultValue="MOVEMENTS") View view,@RequestParam(defaultValue="0") int offset){
  if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365||from.getYear()<2000||to.getYear()>2100||offset<0||offset>100000)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe um período de até 366 dias e uma página válida.");
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require();
  ZoneId zone=ZoneId.of(jdbc.queryForObject("SELECT timezone FROM public_profiles WHERE tenant_id=?",String.class,tenant));
  Timestamp begin=Timestamp.from(from.atStartOfDay(zone).toInstant()),end=Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()),now=Timestamp.from(clock.instant());
  var cash=jdbc.queryForMap(MOVEMENTS+"SELECT coalesce(sum(amount) FILTER(WHERE kind='RECEIPT'),0) AS received,coalesce(sum(amount) FILTER(WHERE kind='REFUND'),0) AS refunded FROM period",tenant,tenant,begin,end);
  var pending=jdbc.queryForMap(BALANCES+"SELECT coalesce(sum(deposit_due),0) AS deposit,coalesce(sum(total_due),0) AS total,coalesce(sum(refund_due),0) AS refund,coalesce(sum(review_amount),0) AS review FROM balances",now,tenant,begin,end);
  List<Map<String,Object>> rows;
  if(view==View.MOVEMENTS)rows=jdbc.query(MOVEMENTS+"SELECT m.*,b.service_snapshot->>'name' AS service_name,c.name AS customer_name FROM period m JOIN bookings b ON b.tenant_id=? AND b.id=m.booking_id JOIN customers c ON c.tenant_id=b.tenant_id AND c.id=b.customer_id ORDER BY m.at DESC,m.id DESC,m.kind LIMIT 51 OFFSET ?",(r,n)->Map.<String,Object>of("id",r.getObject("id",UUID.class),"bookingId",r.getObject("booking_id",UUID.class),"kind",r.getString("kind"),"at",r.getTimestamp("at").toInstant(),"amountCents",r.getLong("amount"),"serviceName",r.getString("service_name"),"customerName",r.getString("customer_name")),tenant,tenant,begin,end,tenant,offset);
  else rows=jdbc.query(BALANCES+"SELECT * FROM balances WHERE total_due>0 OR refund_due>0 OR review_amount>0 ORDER BY starts_at,id LIMIT 51 OFFSET ?",(r,n)->{
   Map<String,Object> item=new LinkedHashMap<>();item.put("bookingId",r.getObject("id",UUID.class));item.put("customerName",r.getString("customer_name"));item.put("serviceName",r.getString("service_name"));item.put("at",r.getTimestamp("starts_at").toInstant());item.put("status",r.getString("current_status"));item.put("netCents",r.getLong("net"));item.put("depositDueCents",r.getLong("deposit_due"));item.put("totalDueCents",r.getLong("total_due"));item.put("refundDueCents",r.getLong("refund_due"));item.put("reviewCents",r.getLong("review_amount"));return item;
  },now,tenant,begin,end,offset);
  long received=((Number)cash.get("received")).longValue(),refunded=((Number)cash.get("refunded")).longValue();
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("from",from,"to",to,"timezone",zone.getId(),"view",view,"items",rows.stream().limit(50).toList(),"hasMore",rows.size()>50,"summary",Map.of("receivedCents",received,"refundedCents",refunded,"netCents",received-refunded,"depositDueCents",pending.get("deposit"),"totalDueCents",pending.get("total"),"refundDueCents",pending.get("refund"),"reviewCents",pending.get("review"))));
 }
}
