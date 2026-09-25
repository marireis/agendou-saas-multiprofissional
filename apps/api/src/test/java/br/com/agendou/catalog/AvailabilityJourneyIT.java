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
class AvailabilityJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc;@Autowired AuthService auth;@Autowired JdbcTemplate runtime;
 JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
 record Account(UUID id,UUID tenant,String slug){}
 Account account(){String email=UUID.randomUUID()+"@example.test",slug="studio-"+UUID.randomUUID();auth.register(email,"synthetic-password-123","Studio teste",slug);return owner().queryForObject("SELECT u.id,m.tenant_id FROM app_users u JOIN memberships m ON m.user_id=u.id WHERE u.email=?",(r,n)->new Account(r.getObject(1,UUID.class),r.getObject(2,UUID.class),slug),email);}
 String body(int version){return """
 {"version":%d,"schedule":{"weekly":[{"day":1,"periods":[{"start":"09:00","end":"12:00"},{"start":"13:00","end":"18:00"}]}],"exceptions":[{"date":"2026-12-25","reason":"Feriado","periods":[]}]}}
 """.formatted(version);}
 @Test void roundTripPreservesBreakAndClosedDateAndIsolation()throws Exception{
  var a=account();var b=account();
  mvc.perform(get("/api/v1/admin/availability")).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).contentType("application/json").content(body(0))).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body(0))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.schedule.weekly[0].periods.length()").value(2)).andExpect(jsonPath("$.schedule.exceptions[0].periods").isEmpty()).andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
  mvc.perform(get("/api/v1/admin/availability").with(user(b.id.toString())).param("tenantId",a.tenant.toString())).andExpect(jsonPath("$.version").value(0));
  assertThat(runtime.queryForObject("SELECT count(*) FROM availability_settings",Long.class)).isZero();
  mvc.perform(get("/api/v1/admin/availability").with(user(a.id.toString()))).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.schedule.exceptions[0].reason").value("Feriado"));
 }
 @Test void intervalPersistsAndLegacyDefaultsToZero()throws Exception{
  var a=account();
  mvc.perform(get("/api/v1/admin/availability").with(user(a.id.toString()))).andExpect(jsonPath("$.schedule.intervalMinutes").value(0));
  for(int interval:List.of(-1,241))mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body(0).replace("\"weekly\":","\"intervalMinutes\":"+interval+",\"weekly\":"))).andExpect(status().isUnprocessableEntity());
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body(0).replace("\"weekly\":","\"intervalMinutes\":60,\"weekly\":"))).andExpect(status().isOk());
  mvc.perform(get("/api/v1/admin/availability").with(user(a.id.toString()))).andExpect(jsonPath("$.schedule.intervalMinutes").value(60)).andExpect(jsonPath("$.schedule.weekly[0].periods.length()").value(2));
  owner().update("UPDATE availability_settings SET schedule=schedule-'intervalMinutes' WHERE tenant_id=?",a.tenant);
  mvc.perform(get("/api/v1/admin/availability").with(user(a.id.toString()))).andExpect(jsonPath("$.schedule.intervalMinutes").value(0));
 }
 @Test void slotPreviewUsesSavedTenantSettingsAndNeverPublishes()throws Exception{
  var a=account();var b=account();UUID service=UUID.randomUUID();
  owner().update("INSERT INTO services(id,tenant_id,name,description,duration_minutes,price_cents,buffer_before_minutes,buffer_after_minutes,deposit_percent,active) VALUES (?,?,'Consulta','',30,10000,0,0,50,true)",service,a.tenant);
  var date=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(2);
  String data=body(0).replace("\"day\":1","\"day\":"+date.getDayOfWeek().getValue()).replace("\"weekly\":","\"intervalMinutes\":30,\"weekly\":");
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(data)).andExpect(status().isOk());
  String path="/api/v1/admin/availability/slots?serviceId="+service+"&date="+date;
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).with(user(b.id.toString()))).andExpect(status().isNotFound());
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.sequence.length()").value(8)).andExpect(jsonPath("$.candidates.length()").value(30)).andExpect(jsonPath("$.bookingAvailable").value(false)).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get("/api/v1/admin/availability/slots").param("serviceId",service.toString()).param("date",date.plusDays(60).toString()).with(user(a.id.toString()))).andExpect(status().isUnprocessableEntity());
  owner().update("UPDATE services SET active=false WHERE id=?",service);
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(status().isNotFound());
 }
 @Test void invalidPeriodsAndDuplicatesNeverSave()throws Exception{
  var a=account();
  for(String data:List.of(body(0).replace("13:00","11:00"),body(0).replace("12:00","08:00"),body(0).replace("09:00","09:00:01"),body(0).replace("\"day\":1","\"day\":8"),body(0).replace("\"periods\":[]","\"periods\":[null]"),body(0).replace("2026-12-25","not-a-date"),"{\"version\":0,\"schedule\":{\"weekly\":[{\"day\":1,\"periods\":[]},{\"day\":1,\"periods\":[]}],\"exceptions\":[]}}")){
   int status=mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(data)).andReturn().getResponse().getStatus();assertThat(status).isIn(400,422);
  }
  assertThat(owner().queryForObject("SELECT count(*) FROM availability_settings WHERE tenant_id=?",Long.class,a.tenant)).isZero();
 }
 @Test void concurrentWritesHaveOneWinnerAndBlockedTrialPreservesRead()throws Exception{
  var a=account();java.util.concurrent.Callable<Integer> call=()->mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body(0))).andReturn().getResponse().getStatus();
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var results=pool.invokeAll(List.of(call,call));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);}
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body(1))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/admin/availability").with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
 }
}
