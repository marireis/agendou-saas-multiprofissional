package br.com.agendou.catalog;
import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import br.com.agendou.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@RestController @RequestMapping("/api/v1/admin/bookings")
public class BookingRescheduleController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final BookingService bookings;private final ObjectMapper json;private final Clock clock;
 public BookingRescheduleController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,BookingService bookings,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.bookings=bookings;this.json=json;this.clock=clock;}
 record Booking(UUID id,UUID allocation,String status,Instant start,Instant end,Instant deadline,String timezone,int version,int before,int after,boolean active){}
 private void lock(){tenants.applyCurrentTenant();jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,TenantContext.require());bookings.expireCurrent();}
 private Booking booking(UUID id){var rows=jdbc.query("SELECT b.id,b.allocation_id,b.status,b.starts_at,b.ends_at,coalesce(b.review_expires_at,b.expires_at),b.timezone,b.schedule_version,a.buffer_before,a.buffer_after,a.active FROM bookings b JOIN calendar_allocations a ON a.tenant_id=b.tenant_id AND a.id=b.allocation_id WHERE b.tenant_id=? AND b.id=?",(r,n)->new Booking(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant(),r.getTimestamp(6).toInstant(),r.getString(7),r.getInt(8),r.getInt(9),r.getInt(10),r.getBoolean(11)),TenantContext.require(),id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reserva não encontrada.");return rows.getFirst();}
 private void eligible(Booking b){if(!Set.of("AWAITING_PAYMENT","IN_REVIEW","CONFIRMED").contains(b.status())||!b.active()||!clock.instant().isBefore(b.start())||(!b.status().equals("CONFIRMED")&&!clock.instant().isBefore(b.deadline())))throw conflict("Somente reservas ativas antes do início podem ser reagendadas.");}
 private List<SlotGenerator.Slot> slots(Booking b,LocalDate date)throws Exception{
  var configs=jdbc.query("SELECT schedule::text FROM availability_settings WHERE tenant_id=?",(r,n)->r.getString(1),TenantContext.require());if(configs.isEmpty())return List.of();
  var occupied=jdbc.query("SELECT starts_at,ends_at,buffer_before,buffer_after,kind FROM calendar_allocations WHERE tenant_id=? AND id<>? AND active AND (expires_at IS NULL OR expires_at>?)",(r,n)->new SlotGenerator.Occupied(r.getTimestamp(1).toInstant(),r.getTimestamp(2).toInstant(),r.getInt(3),r.getInt(4),r.getString(5).equals("BLOCK")),TenantContext.require(),b.allocation(),Timestamp.from(clock.instant()));
  var service=new SlotGenerator.Service(Math.toIntExact(Duration.between(b.start(),b.end()).toMinutes()),b.before(),b.after());return SlotGenerator.generate(json.readValue(configs.getFirst(),AvailabilityController.Schedule.class),service,ZoneId.of(b.timezone()),date,clock.instant(),occupied).candidates();
 }
 @GetMapping("/{id}/reschedule-options") @Transactional(rollbackFor=Exception.class) public ResponseEntity<?> options(@PathVariable UUID id,@RequestParam LocalDate date)throws Exception{lock();var b=booking(id);eligible(b);var today=clock.instant().atZone(ZoneId.of(b.timezone())).toLocalDate();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("version",b.version(),"currentStart",b.start(),"timezone",b.timezone(),"minDate",today,"maxDate",today.plusDays(59),"slots",slots(b,date).stream().filter(s->!s.start().equals(b.start())).toList()));}
 public record Edit(@NotNull UUID requestKey,@NotNull @Min(0) Integer expectedVersion,@NotNull Instant start,@NotBlank @Size(min=5,max=1000) String reason){}
 @PostMapping("/{id}/reschedule") @Transactional(rollbackFor=Exception.class) public ResponseEntity<?> move(@PathVariable UUID id,@Valid @RequestBody Edit input,HttpServletRequest request)throws Exception{
  lock();subscriptions.requireOperational(TenantContext.require());var b=booking(id);String reason=input.reason().trim();if(reason.length()<5)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe o motivo do reagendamento.");
  var old=jdbc.queryForList("SELECT expected_version,new_start,reason FROM booking_reschedules WHERE tenant_id=? AND booking_id=? AND request_key=?",TenantContext.require(),id,input.requestKey());
  if(!old.isEmpty()){var row=old.getFirst();if(((Number)row.get("expected_version")).intValue()!=input.expectedVersion()||!((Timestamp)row.get("new_start")).toInstant().equals(input.start())||!row.get("reason").equals(reason))throw conflict("Chave já utilizada para outra alteração.");return result(b);}
  eligible(b);if(b.version()!=input.expectedVersion())throw conflict("O horário mudou em outra janela. Atualize a reserva.");if(input.start().equals(b.start()))throw conflict("Escolha um horário diferente do atual.");
  var selected=slots(b,input.start().atZone(ZoneId.of(b.timezone())).toLocalDate()).stream().filter(s->s.start().equals(input.start())).findFirst().orElseThrow(()->conflict("Horário indisponível. Consulte novamente as opções."));
  UUID tenant=TenantContext.require();var month=input.start().atZone(ZoneId.of(b.timezone())).toLocalDate().withDayOfMonth(1);var originalMonth=b.start().atZone(ZoneId.of(b.timezone())).toLocalDate().withDayOfMonth(1);
  if(!month.equals(originalMonth)){var subscription=subscriptions.current(tenant);Integer limit=jdbc.queryForObject("SELECT monthly_booking_limit FROM plans WHERE code=?",Integer.class,subscription.planCode().name());long used=jdbc.queryForObject("SELECT count(*) FROM booking_usage WHERE tenant_id=? AND month=? AND booking_id<>? AND state IN ('HELD','COMMITTED')",Long.class,tenant,java.sql.Date.valueOf(month),id);if(limit!=null&&used>=limit)throw conflict("O limite de reservas do mês de destino foi atingido.");}
  jdbc.update("UPDATE calendar_allocations SET active=false,released_at=? WHERE tenant_id=? AND active AND expires_at<=?",Timestamp.from(clock.instant()),tenant,Timestamp.from(clock.instant()));
  int changed=jdbc.update("UPDATE calendar_allocations SET starts_at=?,ends_at=?,protected_start=?,protected_end=? WHERE tenant_id=? AND id=? AND active",Timestamp.from(selected.start()),Timestamp.from(selected.end()),Timestamp.from(selected.start().minusSeconds(b.before()*60L)),Timestamp.from(selected.end().plusSeconds(b.after()*60L)),tenant,b.allocation());if(changed!=1)throw conflict("A ocupação original não está mais disponível.");
  jdbc.update("UPDATE bookings SET starts_at=?,ends_at=?,schedule_version=schedule_version+1 WHERE tenant_id=? AND id=?",Timestamp.from(selected.start()),Timestamp.from(selected.end()),tenant,id);jdbc.update("UPDATE booking_usage SET month=? WHERE tenant_id=? AND booking_id=?",java.sql.Date.valueOf(month),tenant,id);
  jdbc.update("INSERT INTO booking_reschedules(id,tenant_id,booking_id,request_key,expected_version,old_start,old_end,new_start,new_end,reason,recorded_by,correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,id,input.requestKey(),input.expectedVersion(),Timestamp.from(b.start()),Timestamp.from(b.end()),Timestamp.from(selected.start()),Timestamp.from(selected.end()),reason,UUID.fromString(request.getUserPrincipal().getName()),CorrelationIdFilter.id(request));jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'RESCHEDULED')",UUID.randomUUID(),tenant,id);return result(booking(id));
 }
 private ResponseEntity<?> result(Booking b){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("id",b.id(),"start",b.start(),"end",b.end(),"version",b.version(),"status",b.status()));}
 static List<Map<String,Object>> history(JdbcTemplate jdbc,UUID id){return jdbc.query("SELECT old_start,new_start,reason,created_at FROM booking_reschedules WHERE tenant_id=? AND booking_id=? ORDER BY created_at,id",(r,n)->Map.of("oldStart",r.getTimestamp(1).toInstant(),"newStart",r.getTimestamp(2).toInstant(),"reason",r.getString(3),"at",r.getTimestamp(4).toInstant()),TenantContext.require(),id);}
 private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
