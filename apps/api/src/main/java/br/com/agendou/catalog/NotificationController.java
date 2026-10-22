package br.com.agendou.catalog;

import br.com.agendou.tenancy.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationController {
 private final JdbcTemplate jdbc;
 private final TenantSessionConfigurer tenants;
 public NotificationController(JdbcTemplate jdbc,TenantSessionConfigurer tenants){this.jdbc=jdbc;this.tenants=tenants;}
 // ASSISTED_REQUESTED supplements REQUESTED and is not a second notification.
 private static final String TYPES="('REQUESTED','IN_REVIEW','CONFIRMED','RESCHEDULED','EXPIRED','CANCELED','COMPLETED','NO_SHOW','PAYMENT_RECORDED','REFUND_RECORDED')";

 @GetMapping @Transactional
 public ResponseEntity<?> list(@RequestParam(defaultValue="false") boolean unreadOnly,@RequestParam(required=false) UUID before,HttpServletRequest request){
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require(),user=UUID.fromString(request.getUserPrincipal().getName());
  var args=new ArrayList<Object>(List.of(user,tenant));
  String cursor="";
  if(before!=null){
   var anchor=jdbc.queryForList("SELECT created_at,id FROM booking_events WHERE tenant_id=? AND id=? AND event_type IN "+TYPES,tenant,before);
   if(anchor.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Aviso não encontrado.");
   cursor=" AND (e.created_at,e.id)<(?,?)";args.add(anchor.getFirst().get("created_at"));args.add(before);
  }
  var rows=jdbc.query("SELECT e.id,e.booking_id,e.event_type,e.created_at,b.service_snapshot->>'name' AS service_name,b.starts_at,b.timezone,r.event_id IS NOT NULL AS is_read FROM booking_events e JOIN bookings b ON b.tenant_id=e.tenant_id AND b.id=e.booking_id LEFT JOIN notification_reads r ON r.tenant_id=e.tenant_id AND r.event_id=e.id AND r.user_id=? WHERE e.tenant_id=? AND e.event_type IN "+TYPES+(unreadOnly?" AND r.event_id IS NULL":"")+cursor+" ORDER BY e.created_at DESC,e.id DESC LIMIT 51",(rs,n)->Map.<String,Object>of("id",rs.getObject("id",UUID.class),"bookingId",rs.getObject("booking_id",UUID.class),"type",rs.getString("event_type"),"at",rs.getTimestamp("created_at").toInstant(),"serviceName",rs.getString("service_name"),"start",rs.getTimestamp("starts_at").toInstant(),"timezone",rs.getString("timezone"),"read",rs.getBoolean("is_read")),args.toArray());
  Long unread=jdbc.queryForObject("SELECT count(*) FROM booking_events e WHERE e.tenant_id=? AND e.event_type IN "+TYPES+" AND NOT EXISTS(SELECT 1 FROM notification_reads r WHERE r.tenant_id=e.tenant_id AND r.event_id=e.id AND r.user_id=?)",Long.class,tenant,user);
  boolean more=rows.size()>50;var items=more?rows.subList(0,50):rows;
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("items",items,"unreadCount",unread,"nextCursor",more?items.getLast().get("id").toString():""));
 }

 @PostMapping("/{id}/read") @Transactional
 public ResponseEntity<Void> read(@PathVariable UUID id,HttpServletRequest request){
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require(),user=UUID.fromString(request.getUserPrincipal().getName());
  if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_events WHERE tenant_id=? AND id=? AND event_type IN "+TYPES+")",Boolean.class,tenant,id)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Aviso não encontrado.");
  jdbc.update("INSERT INTO notification_reads(tenant_id,event_id,user_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",tenant,id,user);
  return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
 }
}
