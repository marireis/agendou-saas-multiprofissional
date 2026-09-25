package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/admin/availability")
public class AvailabilityController {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final ObjectMapper json;private final CalendarService calendar;
 public AvailabilityController(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,ObjectMapper json,CalendarService calendar){this.calendar=calendar;this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.json=json;}
 public record Period(@NotNull LocalTime start,@NotNull LocalTime end){}
 public record Day(@Min(1) @Max(7) int day,@NotNull @Size(max=8) List<@NotNull @Valid Period> periods){}
 public record ExceptionDay(@NotNull LocalDate date,@NotNull @Size(max=100) String reason,@NotNull @Size(max=8) List<@NotNull @Valid Period> periods){}
 public record Schedule(@NotNull @Size(max=7) List<@NotNull @Valid Day> weekly,@NotNull @Size(max=366) List<@NotNull @Valid ExceptionDay> exceptions,@Min(0) @Max(240) Integer intervalMinutes){ public Schedule { if(intervalMinutes==null)intervalMinutes=0; } }
 public record Edit(@NotNull @Min(0) Integer version,@NotNull @Valid Schedule schedule){}
 @GetMapping @Transactional(readOnly=true) public ResponseEntity<?> get(){
  tenants.applyCurrentTenant();var rows=jdbc.query("SELECT version,schedule::text FROM availability_settings WHERE tenant_id=?",(r,n)->Map.of("version",r.getInt(1),"schedule",decode(r.getString(2))),TenantContext.require());
  var result=new LinkedHashMap<String,Object>(rows.isEmpty()?Map.of("version",0,"schedule",new Schedule(List.of(),List.of(),0)):rows.getFirst());
  result.put("timezone",jdbc.queryForObject("SELECT timezone FROM public_profiles WHERE tenant_id=?",String.class,TenantContext.require()));
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
 }
 @PutMapping @Transactional public ResponseEntity<?> save(@Valid @RequestBody Edit body)throws Exception{
  subscriptions.requireOperational(TenantContext.require());tenants.applyCurrentTenant();validate(body.schedule());calendar.requireNoFutureHolds();
  int version=jdbc.queryForObject("SELECT coalesce((SELECT version FROM availability_settings WHERE tenant_id=?),0)",Integer.class,TenantContext.require());
  if(version!=body.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Os horários foram alterados em outra janela. Recarregue antes de salvar.");
  jdbc.update("INSERT INTO availability_settings(tenant_id,version,schedule) VALUES (?,?,?::jsonb) ON CONFLICT(tenant_id) DO UPDATE SET version=excluded.version,schedule=excluded.schedule,updated_at=now()",TenantContext.require(),version+1,json.writeValueAsString(body.schedule()));
  return get();
 }
 private Schedule decode(String value){try{return json.readValue(value,Schedule.class);}catch(Exception e){throw new IllegalStateException("Configuração de disponibilidade inválida",e);}}
 static void validate(Schedule schedule){
  Set<Integer> days=new HashSet<>();for(var day:schedule.weekly()){if(!days.add(day.day()))invalid("Dia da semana repetido.");periods(day.periods());}
  Set<LocalDate> dates=new HashSet<>();for(var exception:schedule.exceptions()){if(!dates.add(exception.date()))invalid("Data de exceção repetida.");periods(exception.periods());}
 }
 private static void periods(List<Period> periods){
  var sorted=periods.stream().sorted(Comparator.comparing(Period::start)).toList();LocalTime end=null;
  for(var p:sorted){if(!p.start().isBefore(p.end())||p.start().getSecond()!=0||p.end().getSecond()!=0||p.start().getNano()!=0||p.end().getNano()!=0)invalid("Use períodos no mesmo dia, com início anterior ao fim e precisão de minutos.");if(end!=null&&p.start().isBefore(end))invalid("Os períodos do mesmo dia não podem se sobrepor.");end=p.end();}
 }
 private static void invalid(String message){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
}
