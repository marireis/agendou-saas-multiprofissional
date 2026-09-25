package br.com.agendou.catalog;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.agendou.identity.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
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
class ServiceJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc; @Autowired AuthService auth; @Autowired ObjectMapper json; @Autowired JdbcTemplate runtime;
 JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
 record Account(UUID id,UUID tenant){}
 Account account(){String email=UUID.randomUUID()+"@example.test";auth.register(email,"synthetic-password-123","Studio teste","studio-"+UUID.randomUUID());return owner().queryForObject("SELECT u.id,m.tenant_id FROM app_users u JOIN memberships m ON m.user_id=u.id WHERE u.email=?",(r,n)->new Account(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),email);}
 Map<String,Object> body(){return new HashMap<>(Map.of("name","Consulta teste","description","Atendimento sintético","durationMinutes",30,"priceCents",101,"bufferBeforeMinutes",0,"bufferAfterMinutes",15,"depositPercent",50,"active",true));}
 String create(Account a)throws Exception{return json.readTree(mvc.perform(post("/api/v1/admin/services").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body()))).andExpect(status().isCreated()).andExpect(jsonPath("$.depositCents").value(51)).andReturn().getResponse().getContentAsString()).get("id").asText();}
 @Test void lifecycleRoundingIsolationAndCsrf()throws Exception{
  var a=account();var b=account();var data=body();
  mvc.perform(post("/api/v1/admin/services").with(user(a.id.toString())).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isForbidden());
  String id=create(a);data.put("version",0);data.put("active",false);
  mvc.perform(put("/api/v1/admin/services/"+id).with(user(b.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/admin/services").with(user(b.id.toString())).param("tenantId",a.tenant.toString())).andExpect(jsonPath("$.total").value(0));
  mvc.perform(put("/api/v1/admin/services/"+id).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.version").value(1));
  mvc.perform(get("/api/v1/admin/services").with(user(a.id.toString()))).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.activeCount").value(0));
  data.put("version",1);data.put("active",true);data.put("depositPercent",100);
  mvc.perform(put("/api/v1/admin/services/"+id).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isOk()).andExpect(jsonPath("$.depositCents").value(101));
  assertThat(runtime.queryForObject("SELECT count(*) FROM services",Long.class)).isZero();
  assertThatThrownBy(()->runtime.update("DELETE FROM services")).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void validationAndDatabaseConstraints()throws Exception{
  var a=account();
  for(var pair:Map.of("durationMinutes",4,"priceCents",0,"depositPercent",49,"bufferBeforeMinutes",-1,"bufferAfterMinutes",241).entrySet()){
   var data=body();data.put(pair.getKey(),pair.getValue());
   mvc.perform(post("/api/v1/admin/services").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isUnprocessableEntity());
  }
  var data=body();data.put("priceCents",10.5);
  mvc.perform(post("/api/v1/admin/services").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isBadRequest());
  data=body();data.put("durationMinutes",480);data.put("priceCents",100000000);data.put("depositPercent",100);data.put("bufferBeforeMinutes",240);
  mvc.perform(post("/api/v1/admin/services").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isCreated()).andExpect(jsonPath("$.depositCents").value(100000000));
  assertThatThrownBy(()->owner().update("UPDATE services SET duration_minutes=481 WHERE tenant_id=?",a.tenant)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void expiredTrialPreservesListButBlocksWrites()throws Exception{
  var a=account();String id=create(a);owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  var data=body();data.put("version",0);
  mvc.perform(post("/api/v1/admin/services").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/services/"+id).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(data))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/admin/services").with(user(a.id.toString()))).andExpect(jsonPath("$.total").value(1));
 }
 @Test void competingEditsHaveOnlyOneWinner()throws Exception{
  var a=account();String id=create(a);var data=body();data.put("version",0);byte[] bytes=json.writeValueAsBytes(data);
  try(var pool=Executors.newFixedThreadPool(2)){
   var results=pool.invokeAll(List.<Callable<Integer>>of(()->mvc.perform(put("/api/v1/admin/services/"+id).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(bytes)).andReturn().getResponse().getStatus(),()->mvc.perform(put("/api/v1/admin/services/"+id).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(bytes)).andReturn().getResponse().getStatus()));
   assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);
  }
 }
}
