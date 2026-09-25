package br.com.agendou.catalog;

import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/availability/slots")
public class SlotPreviewController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final ObjectMapper json;private final Clock clock;private final CalendarService calendar;
 public SlotPreviewController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,ObjectMapper json,Clock clock,CalendarService calendar){this.calendar=calendar;this.jdbc=jdbc;this.tenants=tenants;this.json=json;this.clock=clock;}
 @GetMapping @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ResponseEntity<?> preview(@RequestParam UUID serviceId,@RequestParam LocalDate date)throws Exception{
  tenants.applyCurrentTenant();UUID tenant=TenantContext.require();Instant now=clock.instant();
  ZoneId zone=ZoneId.of(jdbc.queryForObject("SELECT timezone FROM public_profiles WHERE tenant_id=?",String.class,tenant));LocalDate today=now.atZone(zone).toLocalDate();
  if(date.isBefore(today)||!date.isBefore(today.plusDays(60)))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Escolha uma data entre hoje e os próximos 59 dias.");
  var services=jdbc.query("SELECT duration_minutes,buffer_before_minutes,buffer_after_minutes FROM services WHERE tenant_id=? AND id=? AND active",(r,n)->new SlotGenerator.Service(r.getInt(1),r.getInt(2),r.getInt(3)),tenant,serviceId);
  if(services.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Serviço ativo não encontrado.");
  var configs=jdbc.query("SELECT schedule::text FROM availability_settings WHERE tenant_id=?",(r,n)->r.getString(1),tenant);
  var schedule=configs.isEmpty()?new AvailabilityController.Schedule(List.of(),List.of(),0):json.readValue(configs.getFirst(),AvailabilityController.Schedule.class);
  var result=SlotGenerator.generate(schedule,services.getFirst(),zone,date,now,calendar.occupied());
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("date",date,"timezone",zone.getId(),"today",today,"lastDate",today.plusDays(59),"intervalMinutes",schedule.intervalMinutes(),"bookingAvailable",false,"candidates",result.candidates(),"sequence",result.sequence()));
 }
}
