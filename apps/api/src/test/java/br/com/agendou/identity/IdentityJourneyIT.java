package br.com.agendou.identity;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;
import br.com.agendou.billing.*;
import br.com.agendou.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties={"agendou.mail-worker-delay=3600000","agendou.trial-worker-delay=3600000"})
@AutoConfigureMockMvc
@Testcontainers
class IdentityJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
  properties.add("spring.datasource.url",postgres::getJdbcUrl);
  properties.add("spring.flyway.url",postgres::getJdbcUrl);
 }
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired SubscriptionService subscriptions;
 @Autowired TrialExpirationWorker expiration;
 JdbcTemplate owner() {return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
 @Test void registrationVerificationSessionIsolationExpirationAndReactivation() throws Exception {
  String email="admin-"+UUID.randomUUID()+"@example.test";
  String slug="studio-"+UUID.randomUUID();
  String registration="{\"email\":\""+email+"\",\"password\":\"strong-password-123\",\"name\":\"Studio Teste\",\"slug\":\""+slug+"\"}";
  mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(registration)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json").content(registration)).andExpect(status().isAccepted());
  var tenant=owner().queryForObject("SELECT tenant_id FROM memberships m JOIN app_users u ON u.id=m.user_id WHERE u.email=?",UUID.class,email);
  assertThat(owner().queryForObject("SELECT count(*) FROM subscriptions WHERE tenant_id=?",Long.class,tenant)).isEqualTo(1);
  String login="{\"email\":\""+email+"\",\"password\":\"strong-password-123\"}";
  mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(login)).andExpect(status().isUnauthorized());
  String body=owner().queryForObject("SELECT body FROM mail_outbox WHERE recipient=?",String.class,email);
  String token=body.split("token=")[1].split("\\n")[0];
  mvc.perform(post("/api/v1/auth/verify").with(csrf()).contentType("application/json").content("{\"token\":\""+token+"\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/v1/auth/verify").with(csrf()).contentType("application/json").content("{\"token\":\""+token+"\"}")).andExpect(status().isBadRequest());
  jakarta.servlet.http.Cookie session=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(login)).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
  mvc.perform(get("/api/v1/admin/subscription").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TRIAL_ACTIVE"));
  mvc.perform(get("/api/v1/subscriptions/"+UUID.randomUUID()).cookie(session)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/subscriptions/"+tenant+"/confirm-payment").cookie(session).with(csrf())).andExpect(status().isForbidden());
  String edit="{\"name\":\"Studio Preservado\",\"description\":\"Minha descricao\",\"timezone\":\"America/Sao_Paulo\"}";
  mvc.perform(patch("/api/v1/admin/profile").cookie(session).with(csrf()).contentType("application/json").content(edit)).andExpect(status().isOk());
  var end=Instant.now().minusSeconds(60);
  owner().update("UPDATE subscriptions SET trial_started_at=?,trial_ends_at=? WHERE tenant_id=?",Timestamp.from(end.minusSeconds(7*86400)),Timestamp.from(end),tenant);
  expiration.expire();
  mvc.perform(get("/api/v1/admin/subscription").cookie(session)).andExpect(jsonPath("$.status").value("TRIAL_EXPIRED_BLOCKED"));
  mvc.perform(patch("/api/v1/admin/profile").cookie(session).with(csrf()).contentType("application/json").content(edit)).andExpect(status().isForbidden());
  TenantContext.set(tenant);try{subscriptions.confirmPayment(tenant);}finally{TenantContext.clear();}
  mvc.perform(get("/api/v1/admin/subscription").cookie(session)).andExpect(jsonPath("$.status").value("PAID_ACTIVE"));
  mvc.perform(get("/api/v1/admin/profile").cookie(session)).andExpect(jsonPath("$.name").value("Studio Preservado"));
  mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json").content(registration)).andExpect(status().isAccepted());
  assertThat(owner().queryForObject("SELECT count(*) FROM subscriptions WHERE tenant_id=?",Long.class,tenant)).isEqualTo(1);
  mvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType("application/json").content("{\"email\":\""+email+"\"}")).andExpect(status().isAccepted());
  String resetBody=owner().queryForObject("SELECT body FROM mail_outbox WHERE recipient=? AND body LIKE '%/recuperar%'",String.class,email);
  String resetToken=resetBody.split("token=")[1].split("\\n")[0];
  mvc.perform(post("/api/v1/auth/password-reset/confirm").with(csrf()).contentType("application/json").content("{\"token\":\""+resetToken+"\",\"password\":\"new-strong-password\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/admin/subscription").cookie(session)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(login)).andExpect(status().isUnauthorized());
  var newSession=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(login.replace("strong-password-123","new-strong-password"))).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
  mvc.perform(post("/api/v1/auth/logout").cookie(newSession).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/admin/subscription").cookie(newSession)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/admin/subscription")).andExpect(status().isUnauthorized());
 }
}

