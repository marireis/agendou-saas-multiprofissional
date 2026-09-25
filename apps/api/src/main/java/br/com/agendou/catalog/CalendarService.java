package br.com.agendou.catalog;

import br.com.agendou.billing.SubscriptionService;
import br.com.agendou.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CalendarService {
 private final JdbcTemplate jdbc;private final TenantSessionConfigurer tenants;private final SubscriptionService subscriptions;private final ObjectMapper json;private final Clock clock;
 public CalendarService(JdbcTemplate jdbc,TenantSessionConfigurer tenants,SubscriptionService subscriptions,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tenants=tenants;this.subscriptions=subscriptions;this.json=json;this.clock=clock;}
 public ZoneId zone(){return ZoneId.of(jdbc.queryForObject("SELECT timezone FROM public_profiles WHERE tenant_id=?",String.class,TenantContext.require()));}
 public List<SlotGenerator.Occupied> occupied(){return jdbc.query("SELECT starts_at,ends_at,buffer_before,buffer_after,kind FROM calendar_allocations WHERE tenant_id=? AND active AND (expires_at IS NULL OR expires_at>?)",(r,n)->new SlotGenerator.Occupied(r.getTimestamp(1).toInstant(),r.getTimestamp(2).toInstant(),r.getInt(3),r.getInt(4),r.getString(5).equals("BLOCK")),TenantContext.require(),Timestamp.from(clock.instant()));}
 private void lock(){subscriptions.requireOperational(TenantContext.require());tenants.applyCurrentTenant();expire();}
 private void expire(){jdbc.update("UPDATE calendar_allocations SET active=false,released_at=? WHERE tenant_id=? AND active AND expires_at<=?",Timestamp.from(clock.instant()),TenantContext.require(),Timestamp.from(clock.instant()));}
 // Call after the shared tenant lock, before changing hours or timezone.
 public void requireNoFutureHolds(){
  if(jdbc.queryForObject("SELECT count(*) FROM calendar_allocations WHERE tenant_id=? AND kind='HOLD' AND active AND expires_at>? AND ends_at>?",Long.class,TenantContext.require(),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()))>0)conflict("Há horários temporariamente ocupados. Aguarde a liberação antes de alterar o expediente ou o fuso.");
 }
 /** Internal primitive only: booking identity, quota, payment snapshots and idempotency come in MVP-050+. */
 @Transactional public UUID hold(UUID serviceId,Instant start)throws Exception{
  lock();var service=jdbc.query("SELECT duration_minutes,buffer_before_minutes,buffer_after_minutes FROM services WHERE tenant_id=? AND id=? AND active",(r,n)->new SlotGenerator.Service(r.getInt(1),r.getInt(2),r.getInt(3)),TenantContext.require(),serviceId);
  if(service.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Serviço não encontrado.");
  var configs=jdbc.query("SELECT schedule::text FROM availability_settings WHERE tenant_id=?",(r,n)->r.getString(1),TenantContext.require());
  if(configs.isEmpty())conflict("Configure o expediente antes de ocupar horários.");
  var schedule=json.readValue(configs.getFirst(),AvailabilityController.Schedule.class);var details=service.getFirst();
  var candidates=SlotGenerator.generate(schedule,details,zone(),start.atZone(zone()).toLocalDate(),clock.instant(),occupied()).candidates();
  if(candidates.stream().noneMatch(s->s.start().equals(start)))conflict("Horário indisponível. Atualize a disponibilidade.");
  return insert("HOLD",serviceId,start,start.plusSeconds(details.duration()*60L),details.before(),details.after(),clock.instant().plusSeconds(1800),"");
 }
 @Transactional public UUID block(LocalDateTime start,LocalDateTime end,String reason){
  lock();ZoneId zone=zone();Instant from=resolve(start,zone),to=resolve(end,zone);
  if(!to.isAfter(from)||from.isBefore(clock.instant())||Duration.between(from,to).compareTo(Duration.ofDays(366))>0)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe um período futuro válido de até 366 dias.");
  return insert("BLOCK",null,from,to,0,0,null,reason.trim());
 }
 private Instant resolve(LocalDateTime local,ZoneId zone){var offsets=zone.getRules().getValidOffsets(local);if(offsets.size()!=1||local.getSecond()!=0||local.getNano()!=0)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Horário ambíguo ou inexistente no fuso. Escolha outro horário com precisão de minutos.");return local.toInstant(offsets.getFirst());}
 private UUID insert(String kind,UUID service,Instant start,Instant end,int before,int after,Instant expires,String reason){
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO calendar_allocations(id,tenant_id,resource_id,kind,service_id,starts_at,ends_at,buffer_before,buffer_after,protected_start,protected_end,expires_at,reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",id,TenantContext.require(),TenantContext.require(),kind,service,Timestamp.from(start),Timestamp.from(end),before,after,Timestamp.from(start.minusSeconds(before*60L)),Timestamp.from(end.plusSeconds(after*60L)),expires==null?null:Timestamp.from(expires),reason);return id;
 }
 @Transactional public void releaseBlock(UUID id){lock();int changed=jdbc.update("UPDATE calendar_allocations SET active=false,released_at=coalesce(released_at,?) WHERE tenant_id=? AND id=? AND kind='BLOCK'",Timestamp.from(clock.instant()),TenantContext.require(),id);if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Bloqueio não encontrado.");}
 private static void conflict(String reason){throw new ResponseStatusException(HttpStatus.CONFLICT,reason);}
}
