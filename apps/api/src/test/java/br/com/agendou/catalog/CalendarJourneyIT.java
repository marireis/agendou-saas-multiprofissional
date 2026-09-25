package br.com.agendou.catalog;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.agendou.identity.AuthService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties={"agendou.mail-worker-initial-delay=3600000","agendou.trial-worker-delay=3600000"})
@AutoConfigureMockMvc @Testcontainers
class CalendarJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc;@Autowired AuthService auth;@Autowired JdbcTemplate runtime;
 JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
 record Account(UUID id,UUID tenant,String slug){}
 Account account(){String email=UUID.randomUUID()+"@example.test",slug="studio-"+UUID.randomUUID();auth.register(email,"synthetic-password-123","Studio teste",slug);return owner().queryForObject("SELECT u.id,m.tenant_id FROM app_users u JOIN memberships m ON m.user_id=u.id WHERE u.email=?",(r,n)->new Account(r.getObject(1,UUID.class),r.getObject(2,UUID.class),slug),email);}
 @Autowired CalendarService calendar;
 record Fixture(Account account,UUID service,java.time.LocalDate date,java.time.Instant start){}
 Fixture fixture()throws Exception{
  var a=account();UUID service=UUID.randomUUID();var date=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(2);
  owner().update("INSERT INTO services(id,tenant_id,name,description,duration_minutes,price_cents,buffer_before_minutes,buffer_after_minutes,deposit_percent,active) VALUES (?,?,'Consulta','',30,10000,0,0,50,true)",service,a.tenant);
  String body="{\"version\":0,\"schedule\":{\"intervalMinutes\":30,\"weekly\":[{\"day\":"+date.getDayOfWeek().getValue()+",\"periods\":[{\"start\":\"09:00\",\"end\":\"12:00\"}]}],\"exceptions\":[]}}";
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
  return new Fixture(a,service,date,date.atTime(9,0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant());
 }
 UUID hold(Fixture f,java.time.Instant start)throws Exception{br.com.agendou.tenancy.TenantContext.set(f.account.tenant);try{return calendar.hold(f.service,start);}finally{br.com.agendou.tenancy.TenantContext.clear();}}
 String blockBody(Fixture f){return "{\"start\":\""+f.date+"T09:00\",\"end\":\""+f.date+"T10:00\",\"reason\":\"Compromisso sintético\"}";}
 int createBlock(Fixture f)throws Exception{return mvc.perform(post("/api/v1/admin/calendar/blocks").with(user(f.account.id.toString())).with(csrf()).contentType("application/json").content(blockBody(f))).andReturn().getResponse().getStatus();}
 @Test void concurrentHoldAndBlockHaveOnlyOneWinner()throws Exception{
  var f=fixture();java.util.concurrent.Callable<Integer> h=()->{try{hold(f,f.start);return 200;}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}catch(org.springframework.dao.DataIntegrityViolationException e){return 409;}};
  java.util.concurrent.Callable<Integer> b=()->{int code=createBlock(f);return code==201?200:code;};
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var results=pool.invokeAll(List.of(h,b));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);}
  assertThat(owner().queryForObject("SELECT count(*) FROM calendar_allocations WHERE tenant_id=? AND active",Long.class,f.account.tenant)).isEqualTo(1);
 }
 @Test void holdsRespectGapExpireAndGuardConfiguration()throws Exception{
  var f=fixture();UUID id=hold(f,f.start);
  assertThatThrownBy(()->hold(f,f.start.plusSeconds(1800))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  mvc.perform(put("/api/v1/admin/availability").with(user(f.account.id.toString())).with(csrf()).contentType("application/json").content("{\"version\":1,\"schedule\":{\"weekly\":[],\"exceptions\":[]}}")).andExpect(status().isConflict());
  mvc.perform(patch("/api/v1/admin/profile").with(user(f.account.id.toString())).with(csrf()).contentType("application/json").content("{\"name\":\"Studio\",\"description\":\"Teste\",\"timezone\":\"UTC\"}")).andExpect(status().isConflict());
  String url="/api/v1/admin/availability/slots?serviceId="+f.service+"&date="+f.date;
  mvc.perform(get(url).with(user(f.account.id.toString()))).andExpect(jsonPath("$.candidates[0].start").value(f.start.plusSeconds(3600).toString()));
  owner().update("UPDATE calendar_allocations SET expires_at=now()-interval '1 second' WHERE id=?",id);
  mvc.perform(get(url).with(user(f.account.id.toString()))).andExpect(jsonPath("$.candidates[0].start").value(f.start.toString()));
  hold(f,f.start);
  assertThat(owner().queryForObject("SELECT active FROM calendar_allocations WHERE id=?",Boolean.class,id)).isFalse();
 }
 @Test void blockLifecycleIsolationAndSubscription()throws Exception{
  var f=fixture();var b=account();
  mvc.perform(post("/api/v1/admin/calendar/blocks").with(user(f.account.id.toString())).contentType("application/json").content(blockBody(f))).andExpect(status().isForbidden());
  assertThat(createBlock(f)).isEqualTo(201);assertThat(createBlock(f)).isEqualTo(409);
  UUID id=owner().queryForObject("SELECT id FROM calendar_allocations WHERE tenant_id=?",UUID.class,f.account.tenant);
  mvc.perform(get("/api/v1/admin/calendar/blocks")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/admin/calendar/blocks").with(user(b.id.toString()))).andExpect(jsonPath("$.items").isEmpty());
  mvc.perform(delete("/api/v1/admin/calendar/blocks/"+id).with(user(b.id.toString())).with(csrf())).andExpect(status().isNotFound());
  assertThat(runtime.queryForObject("SELECT count(*) FROM calendar_allocations",Long.class)).isZero();
  mvc.perform(get("/api/v1/admin/availability/slots?serviceId="+f.service+"&date="+f.date).with(user(f.account.id.toString()))).andExpect(jsonPath("$.candidates[0].start").value(f.start.plusSeconds(3600).toString()));
  mvc.perform(delete("/api/v1/admin/calendar/blocks/"+id).with(user(f.account.id.toString())).with(csrf())).andExpect(status().isNoContent());
  assertThat(owner().queryForObject("SELECT active FROM calendar_allocations WHERE id=?",Boolean.class,id)).isFalse();
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",f.account.tenant);
  assertThat(createBlock(f)).isEqualTo(403);mvc.perform(get("/api/v1/admin/calendar/blocks").with(user(f.account.id.toString()))).andExpect(status().isOk());
 }
 @Test void databaseRejectsOverlapWithoutApplicationLockButAllowsAdjacentRanges()throws Exception{
  var f=fixture();assertThat(createBlock(f)).isEqualTo(201);
  String sql="INSERT INTO calendar_allocations(id,tenant_id,resource_id,kind,starts_at,ends_at,buffer_before,buffer_after,protected_start,protected_end) SELECT ?,tenant_id,resource_id,kind,starts_at,ends_at,buffer_before,buffer_after,protected_start,protected_end FROM calendar_allocations WHERE tenant_id=?";
  assertThatThrownBy(()->owner().update(sql,UUID.randomUUID(),f.account.tenant)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  owner().update("INSERT INTO calendar_allocations(id,tenant_id,resource_id,kind,starts_at,ends_at,buffer_before,buffer_after,protected_start,protected_end) SELECT ?,tenant_id,resource_id,kind,ends_at,ends_at+interval '1 hour',0,0,ends_at,ends_at+interval '1 hour' FROM calendar_allocations WHERE tenant_id=?",UUID.randomUUID(),f.account.tenant);
  assertThat(owner().queryForObject("SELECT count(*) FROM calendar_allocations WHERE tenant_id=? AND active",Long.class,f.account.tenant)).isEqualTo(2);
 }
 @Test void changingHoursAndHoldingAreSerialized()throws Exception{
  var f=fixture();java.util.concurrent.Callable<Integer> h=()->{try{hold(f,f.start);return 200;}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}};
  java.util.concurrent.Callable<Integer> close=()->mvc.perform(put("/api/v1/admin/availability").with(user(f.account.id.toString())).with(csrf()).contentType("application/json").content("{\"version\":1,\"schedule\":{\"weekly\":[],\"exceptions\":[]}}")).andReturn().getResponse().getStatus();
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var results=pool.invokeAll(List.of(h,close));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);}
 }
}
