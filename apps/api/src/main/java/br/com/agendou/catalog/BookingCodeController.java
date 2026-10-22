package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import br.com.agendou.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/bookings/{id}/access-code")
public class BookingCodeController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final Clock clock;
 public BookingCodeController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,Clock clock){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.clock=clock;}
 public record Reissue(@NotNull @Min(0) Integer expectedVersion,@NotBlank @Size(min=5,max=500) String reason,@NotNull @AssertTrue Boolean identityVerified){@Override public String toString(){return "Reissue[redacted]";}}
 @GetMapping @Transactional public ResponseEntity<?> get(@PathVariable UUID id){
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require();
  if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM bookings WHERE tenant_id=? AND id=?)",Boolean.class,tenant,id)))throw missing();
  var rows=jdbc.queryForList("SELECT version,name_key,phone FROM booking_access_codes WHERE tenant_id=? AND booking_id=?",tenant,id);
  if(rows.isEmpty())return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("available",false));
  var row=rows.getFirst();var history=jdbc.query("SELECT version,reason,created_at FROM booking_code_reissues WHERE tenant_id=? AND booking_id=? ORDER BY version DESC LIMIT 10",(r,n)->Map.of("version",r.getInt(1),"reason",r.getString(2),"at",r.getTimestamp(3).toInstant()),tenant,id);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("available",true,"version",row.get("version"),"originalName",row.get("name_key"),"originalPhone",row.get("phone"),"history",history));
 }
 @PostMapping @Transactional public ResponseEntity<?> reissue(@PathVariable UUID id,@Valid @RequestBody Reissue input,HttpServletRequest request){
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require();jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",UUID.class,tenant);subscriptions.requireOperational(tenant);
  var rows=jdbc.queryForList("SELECT version,rotated_at FROM booking_access_codes WHERE tenant_id=? AND booking_id=? FOR UPDATE",tenant,id);if(rows.isEmpty())throw missing();var row=rows.getFirst();
  if(((Number)row.get("version")).intValue()!=input.expectedVersion())throw conflict("O código já foi alterado. Atualize a consulta antes de emitir novamente.");
  if(row.get("rotated_at") instanceof Timestamp at && clock.instant().isBefore(at.toInstant().plusSeconds(60)))throw conflict("Aguarde um minuto entre emissões de código.");
  String reason=input.reason().trim();if(reason.length()<5)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe o motivo com pelo menos cinco caracteres.");
  String code=CustomerContact.code();int version=input.expectedVersion()+1;
  jdbc.update("UPDATE booking_access_codes SET code_hash=?,version=?,rotated_at=? WHERE tenant_id=? AND booking_id=?",CustomerContact.hash(code),version,Timestamp.from(clock.instant()),tenant,id);
  jdbc.update("INSERT INTO booking_code_reissues(tenant_id,booking_id,version,recorded_by,reason,correlation_id) VALUES (?,?,?,?,?,?)",tenant,id,version,UUID.fromString(request.getUserPrincipal().getName()),reason,CorrelationIdFilter.id(request));
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("accessCode",code,"version",version));
 }
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Reserva com acesso por código não encontrada.");}
 private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
