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
class PaymentSettingsJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc;@Autowired AuthService auth;@Autowired ObjectMapper json;@Autowired JdbcTemplate runtime;
 JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
 record Account(UUID id,UUID tenant){}
 Account account(){String email=UUID.randomUUID()+"@example.test";auth.register(email,"synthetic-password-123","Studio teste","studio-"+UUID.randomUUID());return owner().queryForObject("SELECT u.id,m.tenant_id FROM app_users u JOIN memberships m ON m.user_id=u.id WHERE u.email=?",(r,n)->new Account(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),email);}
 Map<String,Object> body(){return new HashMap<>(Map.of("version",0,"keyType","EMAIL","pixKey","studio@example.test","recipientName","Recebedor sintético","paymentInstructions","Conferência manual após pagamento.","cancellationPolicy","Solicite alterações pelo contato comercial.","enabled",true,"confirmed",true,"changeReason","Configuração inicial de teste"));}
 byte[] bytes(Map<String,Object> value)throws Exception{return json.writeValueAsBytes(value);}
 void save(Account a,Map<String,Object> value)throws Exception{mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(bytes(value))).andExpect(status().isOk());}
 @Test void versionsPreserveOriginalAndAuditOmitsSensitiveValues()throws Exception{
  var a=account();mvc.perform(get("/api/v1/admin/payment-settings").with(user(a.id.toString()))).andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.version").value(0));
  var subscription=owner().queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?",a.tenant);
  save(a,body());var original=owner().queryForObject("SELECT row_to_json(p)::text FROM payment_settings_versions p WHERE tenant_id=? AND version=1",String.class,a.tenant);
  var edit=body();edit.put("version",1);edit.put("pixKey","another@example.test");edit.put("enabled",false);save(a,edit);
  assertThat(owner().queryForObject("SELECT row_to_json(p)::text FROM payment_settings_versions p WHERE tenant_id=? AND version=1",String.class,a.tenant)).isEqualTo(original);
  mvc.perform(get("/api/v1/admin/payment-settings").with(user(a.id.toString()))).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.pixKey").value("another@example.test")).andExpect(jsonPath("$.enabled").value(false)).andExpect(header().string("Cache-Control","no-store"));
  String audit=mvc.perform(get("/api/v1/admin/payment-settings/history").with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$[0].changedFields[0]").value("pix_key")).andReturn().getResponse().getContentAsString();
  assertThat(audit).doesNotContain("studio@example.test","another@example.test","Recebedor sintético","Conferência manual");
  assertThat(owner().queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?",a.tenant)).isEqualTo(subscription);
  edit.put("version",2);save(a,edit);
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_settings_versions WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(2);
 }
 @Test void csrfAuthenticationIsolationAndImmutableDatabaseGrants()throws Exception{
  var a=account();var b=account();
  mvc.perform(get("/api/v1/admin/payment-settings")).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).contentType("application/json").content(bytes(body()))).andExpect(status().isForbidden());
  save(a,body());
  mvc.perform(get("/api/v1/admin/payment-settings").with(user(b.id.toString())).param("tenantId",a.tenant.toString())).andExpect(jsonPath("$.configured").value(false));
  mvc.perform(get("/api/v1/admin/payment-settings/history").with(user(b.id.toString()))).andExpect(content().json("[]"));
  var malicious=body();malicious.put("tenant_id",a.tenant);save(b,malicious);
  assertThat(owner().queryForObject("SELECT actor_id FROM payment_settings_versions WHERE tenant_id=?",UUID.class,b.tenant)).isEqualTo(b.id);
  assertThat(runtime.queryForObject("SELECT count(*) FROM payment_settings_versions",Long.class)).isZero();
  assertThatThrownBy(()->runtime.update("UPDATE payment_settings_versions SET enabled=false")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->runtime.update("DELETE FROM payment_settings_versions")).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void invalidOrUnconfirmedSettingsNeverCreateVersion()throws Exception{
  var a=account();
  for(var entry:Map.<String,Object>of("pixKey","invalid","confirmed",false,"recipientName"," ","paymentInstructions","short","cancellationPolicy","short","changeReason","short").entrySet()){
   var data=body();data.put(entry.getKey(),entry.getValue());mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(bytes(data))).andExpect(status().isUnprocessableEntity());
  }
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_settings_versions WHERE tenant_id=?",Long.class,a.tenant)).isZero();
 }
 @Test void expiredSubscriptionPreservesReadAndBlocksChange()throws Exception{
  var a=account();save(a,body());owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  var data=body();data.put("version",1);data.put("pixKey","new@example.test");mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(bytes(data))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/admin/payment-settings").with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.pixKey").value("studio@example.test"));
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_settings_versions WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
 }
 @Test void concurrentInitialConfigurationHasOneWinnerAndOneAuditVersion()throws Exception{
  var a=account();byte[] bytes=bytes(body());
  Callable<Integer> call=()->mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(bytes)).andReturn().getResponse().getStatus();
  try(var pool=Executors.newFixedThreadPool(2)){var results=pool.invokeAll(List.of(call,call));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);}
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_settings_versions WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
 }
}
