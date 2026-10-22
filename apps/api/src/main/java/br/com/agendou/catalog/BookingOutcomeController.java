package br.com.agendou.catalog;
import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import br.com.agendou.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/bookings")
public class BookingOutcomeController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final Clock clock;
 public BookingOutcomeController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,Clock clock){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.clock=clock;}
 public enum Status {COMPLETED,NO_SHOW}
 public record Edit(@NotNull Status status,@NotNull @Min(0) Integer expectedVersion,@NotBlank @Size(min=5,max=1000) String reason,@NotNull @AssertTrue Boolean confirmed){@Override public String toString(){return "Outcome[redacted]";}}
 @PostMapping("/{id}/outcome") @Transactional public ResponseEntity<?> record(@PathVariable UUID id,@Valid @RequestBody Edit input,HttpServletRequest request){
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require();jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenant);subscriptions.requireOperational(tenant);
  var rows=jdbc.queryForList("SELECT status,ends_at,schedule_version FROM bookings WHERE tenant_id=? AND id=?",tenant,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Atendimento não encontrado.");var booking=rows.getFirst();String reason=input.reason().trim();if(reason.length()<5)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe uma observação com pelo menos cinco caracteres.");
  var previous=jdbc.queryForList("SELECT status,expected_version,reason FROM booking_outcomes WHERE tenant_id=? AND booking_id=?",tenant,id);
  if(!previous.isEmpty()){var old=previous.getFirst();if(!old.get("status").equals(input.status().name())||((Number)old.get("expected_version")).intValue()!=input.expectedVersion()||!old.get("reason").equals(reason))throw conflict("O atendimento já foi encerrado com outro registro. Atualize a consulta.");return response(id,input.status());}
  if(!"CONFIRMED".equals(booking.get("status")))throw conflict("Somente atendimentos confirmados podem ser concluídos ou marcados como falta.");
  if(((Number)booking.get("schedule_version")).intValue()!=input.expectedVersion())throw conflict("O horário foi alterado. Atualize o atendimento antes de registrar.");
  if(clock.instant().isBefore(((Timestamp)booking.get("ends_at")).toInstant()))throw conflict("Aguarde o término previsto do atendimento para registrar conclusão ou falta.");
  jdbc.update("INSERT INTO booking_outcomes(tenant_id,booking_id,status,expected_version,reason,recorded_by,correlation_id) VALUES (?,?,?,?,?,?,?)",tenant,id,input.status().name(),input.expectedVersion(),reason,UUID.fromString(request.getUserPrincipal().getName()),CorrelationIdFilter.id(request));
  jdbc.update("UPDATE bookings SET status=? WHERE tenant_id=? AND id=?",input.status().name(),tenant,id);jdbc.update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,?)",UUID.randomUUID(),tenant,id,input.status().name());return response(id,input.status());
 }
 private ResponseEntity<?> response(UUID id,Status status){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("id",id,"status",status.name()));}
 static void add(JdbcTemplate jdbc,UUID id,Map<String,Object> result){var rows=jdbc.query("SELECT status,reason,created_at FROM booking_outcomes WHERE tenant_id=? AND booking_id=?",(r,n)->Map.<String,Object>of("status",r.getString(1),"reason",r.getString(2),"at",r.getTimestamp(3).toInstant()),TenantContext.require(),id);if(!rows.isEmpty())result.put("outcome",rows.getFirst());}
 private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
