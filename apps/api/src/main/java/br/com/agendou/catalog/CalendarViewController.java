package br.com.agendou.catalog;

import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/calendar")
public class CalendarViewController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final ObjectMapper json;private final Clock clock;private final CalendarService calendar;
 public CalendarViewController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,ObjectMapper json,Clock clock,CalendarService calendar){this.calendar=calendar;this.jdbc=jdbc;this.tenants=tenants;this.json=json;this.clock=clock;}
 public record Day(LocalDate date,Instant start,Instant end,boolean exception,String reason,List<AvailabilityController.Period> periods,String status,long occupiedCount){}
 public record Event(UUID id,String kind,Instant start,Instant end,Instant protectedStart,Instant protectedEnd,Instant expiresAt,String reason){}
 @GetMapping @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ResponseEntity<?> view(@RequestParam(required=false) LocalDate from,@RequestParam(defaultValue="7") int days)throws Exception{
  if(days!=1&&days!=7&&days!=42)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Escolha um dia, uma semana ou a grade mensal.");
  tenants.applyCurrentTenant();var tenant=TenantContext.require();Instant now=clock.instant();
  ZoneId zone=ZoneId.of(jdbc.queryForObject("SELECT timezone FROM public_profiles WHERE tenant_id=?",String.class,tenant));LocalDate today=now.atZone(zone).toLocalDate();
  LocalDate first=from==null?today:from;
  if(first.getYear()<2000||first.getYear()>2100)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Escolha uma data entre 2000 e 2100.");
  Instant start=first.atStartOfDay(zone).toInstant(),end=first.plusDays(days).atStartOfDay(zone).toInstant();
  var configs=jdbc.query("SELECT schedule::text FROM availability_settings WHERE tenant_id=?",(r,n)->r.getString(1),tenant);
  var schedule=configs.isEmpty()?new AvailabilityController.Schedule(List.of(),List.of(),0):json.readValue(configs.getFirst(),AvailabilityController.Schedule.class);
  var occupied=calendar.occupied();
  var services=jdbc.query("SELECT duration_minutes,buffer_before_minutes,buffer_after_minutes FROM services WHERE tenant_id=? AND active",(r,n)->new SlotGenerator.Service(r.getInt(1),r.getInt(2),r.getInt(3)),tenant);
  List<Day> dates=new ArrayList<>();
  for(int i=0;i<days;i++){
   LocalDate date=first.plusDays(i);var special=schedule.exceptions().stream().filter(e->e.date().equals(date)).findFirst();
   var periods=special.isPresent()?special.get().periods():schedule.weekly().stream().filter(d->d.day()==date.getDayOfWeek().getValue()).findFirst().map(AvailabilityController.Day::periods).orElse(List.of());
   Instant dayStart=date.atStartOfDay(zone).toInstant(),dayEnd=date.plusDays(1).atStartOfDay(zone).toInstant();
   long count=occupied.stream().filter(o->!o.block()&&o.start().isBefore(dayEnd)&&o.end().isAfter(dayStart)).count();
   boolean available=services.stream().anyMatch(service->!SlotGenerator.generate(schedule,service,zone,date,now,occupied).candidates().isEmpty());
   dates.add(new Day(date,date.atStartOfDay(zone).toInstant(),date.plusDays(1).atStartOfDay(zone).toInstant(),special.isPresent(),special.map(AvailabilityController.ExceptionDay::reason).orElse(""),periods.stream().sorted(Comparator.comparing(AvailabilityController.Period::start)).toList(),!available?"UNAVAILABLE":count>0?"OCCUPIED":"FREE",count));
  }
  var events=jdbc.query("""
   SELECT id,kind,starts_at,ends_at,protected_start,protected_end,expires_at,reason FROM calendar_allocations
   WHERE tenant_id=? AND active AND (expires_at IS NULL OR expires_at>?)
    AND protected_start<? AND protected_end>? ORDER BY starts_at,id
   """,(r,n)->new Event(r.getObject(1,UUID.class),r.getString(2),r.getTimestamp(3).toInstant(),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant(),r.getTimestamp(6).toInstant(),r.getTimestamp(7)==null?null:r.getTimestamp(7).toInstant(),r.getString(8)),tenant,Timestamp.from(now),Timestamp.from(end),Timestamp.from(start));
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("timezone",zone.getId(),"today",today,"from",first,"days",dates,"events",events,"generatedAt",now,"intervalMinutes",schedule.intervalMinutes()));
 }
}
