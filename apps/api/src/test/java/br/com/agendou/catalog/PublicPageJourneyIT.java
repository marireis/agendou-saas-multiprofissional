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
class PublicPageJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc;@Autowired AuthService auth;@Autowired JdbcTemplate runtime;
 JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
 record Account(UUID id,UUID tenant,String slug){}
 Account account(){String email=UUID.randomUUID()+"@example.test",slug="studio-"+UUID.randomUUID();auth.register(email,"synthetic-password-123","Studio teste",slug);return owner().queryForObject("SELECT u.id,m.tenant_id FROM app_users u JOIN memberships m ON m.user_id=u.id WHERE u.email=?",(r,n)->new Account(r.getObject(1,UUID.class),r.getObject(2,UUID.class),slug),email);}
 @Test void draftsAndUnknownSlugsDoNotExposeProfileOrLogo()throws Exception{
  var a=account();
  for(String slug:List.of(a.slug,"unknown")){
   mvc.perform(get("/api/v1/public/pages/"+slug)).andExpect(status().isNotFound());
   mvc.perform(get("/api/v1/public/pages/"+slug+"/logo")).andExpect(status().isNotFound());
  }
  mvc.perform(get("/api/v1/admin/publication")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.canPublish").value(false)).andExpect(jsonPath("$.missingRequirements").isArray());
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isConflict());
 }
 @Test void publicProjectionOnlyIncludesActiveServicesAndSanitizedLogo()throws Exception{
  var a=account();var b=account();
  owner().update("UPDATE public_profiles SET published=true,logo_png=?,logo_version=? WHERE tenant_id=?",new byte[]{1,2,3},UUID.randomUUID(),a.tenant);
  for(var tenant:List.of(a.tenant,b.tenant))for(boolean active:List.of(true,false))owner().update("INSERT INTO services(id,tenant_id,name,description,duration_minutes,price_cents,buffer_before_minutes,buffer_after_minutes,deposit_percent,active) VALUES (?,?,?,'Descrição',30,10001,0,0,50,?)",UUID.randomUUID(),tenant,active?"Serviço ativo":"Serviço secreto",active);
  String data=mvc.perform(get("/api/v1/public/pages/"+a.slug)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.services.length()").value(1)).andExpect(jsonPath("$.services[0].depositCents").value(5001)).andExpect(jsonPath("$.bookingAvailable").value(false)).andReturn().getResponse().getContentAsString();
  assertThat(data).doesNotContain("Serviço secreto",a.id.toString(),a.tenant.toString(),b.tenant.toString(),"pixKey","password","paymentInstructions");
  mvc.perform(get("/api/v1/public/pages/"+a.slug+"/logo")).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3})).andExpect(header().string("X-Content-Type-Options","nosniff"));
  assertThat(runtime.queryForObject("SELECT count(*) FROM public_profiles",Long.class)).isZero();
  mvc.perform(get("/api/v1/public/pages/"+b.slug)).andExpect(status().isNotFound());
 }
 @Test void blockedTenantCannotPublishButCanWithdrawOwnPageWithoutDataLoss()throws Exception{
  var a=account();var b=account();
  owner().update("UPDATE public_profiles SET published=true WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/admin/publication").with(user(b.id.toString())).with(csrf()).param("tenantId",a.tenant.toString())).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/public/pages/"+a.slug)).andExpect(status().isOk()).andExpect(jsonPath("$.bookingAvailable").value(false));
  mvc.perform(delete("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/public/pages/"+a.slug)).andExpect(status().isNotFound());
  assertThat(owner().queryForObject("SELECT count(*) FROM public_profiles WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
 }
}
