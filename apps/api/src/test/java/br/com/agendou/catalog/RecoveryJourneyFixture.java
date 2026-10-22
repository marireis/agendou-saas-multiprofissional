package br.com.agendou.catalog;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import br.com.agendou.identity.AuthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/** Synthetic setup for the recovery journey; production controllers and storage are used. */
final class RecoveryJourneyFixture {
 final JdbcTemplate database;
 final MockMvc mvc;
 final AuthService auth;
 final com.fasterxml.jackson.databind.ObjectMapper json;
 RecoveryJourneyFixture(WebApplicationContext context, PostgreSQLContainer<?> postgres) {
  database=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
  mvc=MockMvcBuilders.webAppContextSetup(context)
   .addFilters(context.getBean("springSessionRepositoryFilter",jakarta.servlet.Filter.class))
   .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
  auth=context.getBean(AuthService.class); json=context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
 }
 JdbcTemplate owner(){return database;}
 record Account(UUID id,UUID tenant,String slug){}
String publishAndFindStart(Account a)throws Exception{
  ready(a);mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isNoContent());
  return java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(2).atTime(9,0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant().toString();
 }
UUID serviceId(Account a){return owner().queryForObject("SELECT id FROM services WHERE tenant_id=?",UUID.class,a.tenant);}
void ready(Account a)throws Exception{
  owner().update("UPDATE public_profiles SET description='Atendimento de teste',contact_email='public@example.test',service_mode='ONLINE' WHERE tenant_id=?",a.tenant);
  owner().update("INSERT INTO services(id,tenant_id,name,description,duration_minutes,price_cents,buffer_before_minutes,buffer_after_minutes,deposit_percent,active) VALUES (?,?,'Consulta','Serviço de teste',30,10000,0,0,50,true)",UUID.randomUUID(),a.tenant);
  mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("version",0,"keyType","EMAIL","pixKey","private@example.test","recipientName","Recebedor teste","paymentInstructions","Instruções privadas para pagamento.","cancellationPolicy","Solicite cancelamento pelo contato.","enabled",true,"confirmed",true,"changeReason","Configuração para teste")))).andExpect(status().isOk());
  var date=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(2);
  var schedule=Map.of("intervalMinutes",0,"weekly",List.of(),"exceptions",List.of(Map.of("date",date.toString(),"reason","Motivo privado","periods",List.of(Map.of("start","09:00","end","10:00")))));
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("version",0,"schedule",schedule)))).andExpect(status().isOk());
 }
Account account(){String email=UUID.randomUUID()+"@example.test",slug="studio-"+UUID.randomUUID();auth.register(email,"synthetic-password-123","Studio teste",slug);return owner().queryForObject("SELECT u.id,m.tenant_id FROM app_users u JOIN memberships m ON m.user_id=u.id WHERE u.email=?",(r,n)->new Account(r.getObject(1,UUID.class),r.getObject(2,UUID.class),slug),email);}
jakarta.servlet.http.Cookie phoneSession(Account a,String start,String phone)throws Exception{
  var result=mvc.perform(post("/api/v1/public/"+a.slug+"/phone/review").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("serviceId",serviceId(a),"start",start,"firstName","Maria","lastName","Silva","phone",phone)))).andExpect(status().isOk()).andReturn();return result.getResponse().getCookie("SESSION");
 }
com.fasterxml.jackson.databind.JsonNode phoneReserve(Account a,jakarta.servlet.http.Cookie session,UUID key)throws Exception{
  String quote=json.readTree(mvc.perform(get("/api/v1/public/"+a.slug+"/phone/review").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("quote").asText();
  return json.readTree(mvc.perform(post("/api/v1/public/"+a.slug+"/phone/bookings").cookie(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(Map.of("quote",quote,"accepted",true)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
 }
String receiptBody(long cents,String reference)throws Exception{return json.writeValueAsString(Map.of("amountCents",cents,"bankReference",reference,"receivingAccount","Conta teste","receivedAt",java.time.Instant.now().toString(),"verified",true));}
}
