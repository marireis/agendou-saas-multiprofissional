package br.com.agendou.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.agendou.identity.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ProfileJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc; @Autowired AuthService auth; @Autowired ObjectMapper json; @Autowired JdbcTemplate runtime;
 JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));}
 record Account(UUID id,UUID tenant) {}
 Account account(){String email=UUID.randomUUID()+"@example.test";auth.register(email,"synthetic-password-123","Studio teste","studio-"+UUID.randomUUID());return owner().queryForObject("SELECT u.id,m.tenant_id FROM app_users u JOIN memberships m ON m.user_id=u.id WHERE u.email=?",(r,n)->new Account(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),email);}
 Map<String,Object> draft(){return new HashMap<>(Map.of("name","Studio teste","description","Atendimento personalizado","timezone","America/Sao_Paulo","contactEmail","studio@example.test","contactPhone","+55 11 99999-9999","serviceMode","IN_PERSON","location","Rua de teste, 10"));}
 @Test void savesDraftProgressAndKeepsCompatibilityAndTrial() throws Exception {
  var a=account();var before=owner().queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?",a.tenant);
  mvc.perform(get("/api/v1/admin/profile").with(user(a.id.toString()))).andExpect(jsonPath("$.profileProgress").value(25));
  var body=draft();body.put("location","");
  mvc.perform(patch("/api/v1/admin/profile").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.profileProgress").value(75)).andExpect(jsonPath("$.profileComplete").value(false));
  body.put("location","Rua de teste, 10");
  mvc.perform(patch("/api/v1/admin/profile").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.profileComplete").value(true)).andExpect(jsonPath("$.profileProgress").value(100));
  mvc.perform(patch("/api/v1/admin/profile").with(user(a.id.toString())).with(csrf()).contentType("application/json").content("{\"name\":\"Novo nome\",\"description\":\"Descrição\",\"timezone\":\"UTC\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.contactEmail").value("studio@example.test"));
  assertThat(owner().queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?",a.tenant)).isEqualTo(before);
 }
 @Test void onlineProfileDoesNotRequireLocationAndRejectsInvalidFields() throws Exception {
  var a=account();var body=draft();body.put("serviceMode","ONLINE");body.put("location","");body.put("contactPhone","");
  mvc.perform(patch("/api/v1/admin/profile").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.profileComplete").value(true));
  for(var entry:Map.of("timezone","+04:00","contactEmail","not-an-email","serviceMode","UNKNOWN","contactPhone","<script>").entrySet()) {
   var invalid=new HashMap<>(body);invalid.put(entry.getKey(),entry.getValue());
   mvc.perform(patch("/api/v1/admin/profile").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(invalid))).andExpect(status().isUnprocessableEntity());
  }
 }
 @Test void logoRequiresCsrfSanitizesIsolatesAndDeletes() throws Exception {
  var a=account();var b=account();byte[] image=LogoSanitizerTest.image("JPEG",800,400);
  mvc.perform(put("/api/v1/admin/profile/logo").with(user(a.id.toString())).content(image)).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/profile/logo").with(user(a.id.toString())).with(csrf()).contentType("application/octet-stream").content(image)).andExpect(status().isOk()).andExpect(jsonPath("$.logoVersion").isNotEmpty());
  byte[] clean=mvc.perform(get("/api/v1/admin/profile/logo").with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
  assertThat(clean).isNotEqualTo(image);
  mvc.perform(get("/api/v1/admin/profile/logo").with(user(b.id.toString())).param("tenantId",a.tenant.toString())).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/admin/profile/logo")).andExpect(status().isUnauthorized());
  assertThat(runtime.queryForObject("SELECT count(*) FROM public_profiles WHERE logo_png IS NOT NULL",Long.class)).isZero();
  mvc.perform(put("/api/v1/admin/profile/logo").with(user(a.id.toString())).with(csrf()).contentType("image/png").content("<svg>not png</svg>")).andExpect(status().isUnprocessableEntity());
  mvc.perform(get("/api/v1/admin/profile/logo").with(user(a.id.toString()))).andExpect(content().bytes(clean));
  mvc.perform(put("/api/v1/admin/profile/logo").with(user(a.id.toString())).with(csrf()).content(new byte[LogoSanitizer.MAX_BYTES+1])).andExpect(status().isPayloadTooLarge());
  mvc.perform(delete("/api/v1/admin/profile/logo").with(user(a.id.toString())).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.logoVersion").isEmpty());
  mvc.perform(get("/api/v1/admin/profile/logo").with(user(a.id.toString()))).andExpect(status().isNotFound());
 }
 @Test void expiredSubscriptionPreservesLogoAndBlocksAllEdits() throws Exception {
  var a=account();byte[] image=LogoSanitizerTest.image("PNG",20,20);
  mvc.perform(put("/api/v1/admin/profile/logo").with(user(a.id.toString())).with(csrf()).content(image)).andExpect(status().isOk());
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(put("/api/v1/admin/profile/logo").with(user(a.id.toString())).with(csrf()).content(image)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/admin/profile/logo").with(user(a.id.toString())).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(patch("/api/v1/admin/profile").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(draft()))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/admin/profile/logo").with(user(a.id.toString()))).andExpect(status().isOk());
 }
}
