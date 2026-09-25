package br.com.agendou.catalog;
import br.com.agendou.tenancy.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/calendar/blocks")
public class CalendarController {
 private final CalendarService calendar;private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final Clock clock;
 public CalendarController(CalendarService calendar,JdbcTemplate jdbc,TenantSessionConfigurer tenants,Clock clock){this.calendar=calendar;this.jdbc=jdbc;this.tenants=tenants;this.clock=clock;}
 public record Block(@NotNull LocalDateTime start,@NotNull LocalDateTime end,@NotBlank @Size(max=200) String reason){}
 @GetMapping @Transactional(readOnly=true) public ResponseEntity<?> list(@RequestParam(defaultValue="0") int offset){
  if(offset<0||offset>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página inválida.");tenants.applyCurrentTenant();
  var rows=jdbc.query("SELECT id,starts_at,ends_at,reason FROM calendar_allocations WHERE tenant_id=? AND kind='BLOCK' AND active AND ends_at>? ORDER BY starts_at,id LIMIT 50 OFFSET ?",(r,n)->Map.of("id",r.getObject(1,UUID.class),"start",r.getTimestamp(2).toInstant(),"end",r.getTimestamp(3).toInstant(),"reason",r.getString(4)),TenantContext.require(),Timestamp.from(clock.instant()),offset);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("items",rows,"timezone",calendar.zone()));
 }
 @PostMapping public ResponseEntity<?> create(@Valid @RequestBody Block body){return ResponseEntity.status(201).body(Map.of("id",calendar.block(body.start(),body.end(),body.reason())));}
 @DeleteMapping("/{id}") public ResponseEntity<Void> release(@PathVariable UUID id){calendar.releaseBlock(id);return ResponseEntity.noContent().build();}
}
