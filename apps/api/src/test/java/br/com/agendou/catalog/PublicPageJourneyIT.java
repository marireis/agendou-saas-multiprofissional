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
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @org.springframework.boot.test.mock.mockito.MockBean org.springframework.mail.javamail.JavaMailSender sender;
 @Autowired br.com.agendou.identity.MailDeliveryService delivery;
 @Test void customerEmailWorkerDeliversAndClearsSecretBody()throws Exception{
  var a=account();var start=publishAndFindStart(a);requestAccess(a,start,UUID.randomUUID()+"@example.test");
  var hash=owner().queryForObject("SELECT token_hash FROM customer_access_tokens WHERE tenant_id=?",String.class,a.tenant);
  for(int i=0;i<100&&delivery.deliverNext();i++){}
  assertThat(owner().queryForObject("SELECT status FROM mail_outbox WHERE token_hash=?",String.class,hash)).isEqualTo("SENT");
  assertThat(owner().queryForObject("SELECT body||recipient FROM mail_outbox WHERE token_hash=?",String.class,hash)).isEmpty();
  var messages=org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
  org.mockito.Mockito.verify(sender,org.mockito.Mockito.atLeastOnce()).send(messages.capture());
  assertThat(messages.getAllValues()).anySatisfy(message->assertThat(message.getText()).contains("/a/"+a.slug+"/agendar#token=","15 minutos"));
 }
 String publishAndFindStart(Account a)throws Exception{
  ready(a);mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isNoContent());
  return java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(2).atTime(9,0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant().toString();
 }
 UUID serviceId(Account a){return owner().queryForObject("SELECT id FROM services WHERE tenant_id=?",UUID.class,a.tenant);}
 String customerToken(Account a){String body=owner().queryForObject("SELECT body FROM mail_outbox WHERE token_hash IN (SELECT token_hash FROM customer_access_tokens WHERE tenant_id=?) AND status='PENDING'",String.class,a.tenant);return body.split("#token=")[1].split("\\n")[0];}
 void requestAccess(Account a,String start,String email)throws Exception{
  mvc.perform(post("/api/v1/public/"+a.slug+"/access-links").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("serviceId",serviceId(a),"start",start,"name","Cliente sintético","email",email)))).andExpect(status().isAccepted());
 }
 @Test void customerSelectsVerifiesAndReviewsWithoutCreatingReservation()throws Exception{
  var a=account();var start=publishAndFindStart(a);var service=serviceId(a);var day=java.time.Instant.parse(start).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().toString();
  var response=mvc.perform(get("/api/v1/public/"+a.slug+"/availability").param("serviceId",service.toString()).param("date",day)).andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(60)).andExpect(jsonPath("$.slots.length()").value(3)).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
  assertThat(response).doesNotContain("Motivo privado",a.tenant.toString(),"email","reason");
  var email=UUID.randomUUID()+"@example.test";requestAccess(a,start,email);var token=customerToken(a);
  assertThat(owner().queryForObject("SELECT token_hash FROM customer_access_tokens WHERE tenant_id=?",String.class,a.tenant)).doesNotContain(token);
  assertThat(runtime.queryForObject("SELECT count(*) FROM customer_access_tokens",Long.class)).isZero();
  mvc.perform(post("/api/v1/public/"+a.slug+"/access-links/consume").contentType("application/json").content(json.writeValueAsBytes(Map.of("token",token)))).andExpect(status().isForbidden());
  var result=mvc.perform(post("/api/v1/public/"+a.slug+"/access-links/consume").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("token",token)))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.priceCents").value(10000)).andExpect(jsonPath("$.depositCents").value(5000)).andExpect(jsonPath("$.slotAvailable").value(true)).andExpect(jsonPath("$.reservationEnabled").value(false)).andReturn();
  var session=result.getResponse().getCookie("SESSION");
  mvc.perform(get("/api/v1/public/"+a.slug+"/review").cookie(session)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/admin/profile").cookie(session)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/public/other/review").cookie(session)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/public/"+a.slug+"/access-links/consume").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("token",token)))).andExpect(status().isBadRequest());
  assertThat(owner().queryForObject("SELECT count(*) FROM calendar_allocations WHERE tenant_id=?",Long.class,a.tenant)).isZero();
 }
 @Test void customerTokensAreTenantBoundExpireAndResendRevokesOldLink()throws Exception{
  var a=account();var b=account();var start=publishAndFindStart(a);publishAndFindStart(b);var email=UUID.randomUUID()+"@example.test";
  requestAccess(a,start,email);var old=customerToken(a);requestAccess(a,start,email);var token=customerToken(a);assertThat(token).isNotEqualTo(old);
  for(var pair:List.of(new String[]{a.slug,old},new String[]{b.slug,token}))mvc.perform(post("/api/v1/public/"+pair[0]+"/access-links/consume").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("token",pair[1])))).andExpect(status().isBadRequest());
  owner().update("UPDATE customer_access_tokens SET expires_at=now()-interval '1 minute' WHERE tenant_id=?",a.tenant);
  mvc.perform(post("/api/v1/public/"+a.slug+"/access-links/consume").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("token",token)))).andExpect(status().isBadRequest());
  runtime.execute("SELECT public.purge_customer_access()");assertThat(owner().queryForObject("SELECT count(*) FROM customer_access_tokens WHERE tenant_id=?",Long.class,a.tenant)).isZero();
 }
 @Test void customerCannotSelectPrivateForeignInactiveOrBlockedResources()throws Exception{
  var a=account();var b=account();var start=publishAndFindStart(a);ready(b);
  mvc.perform(get("/api/v1/public/"+b.slug+"/availability").param("serviceId",serviceId(b).toString())).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/public/"+a.slug+"/availability").param("serviceId",serviceId(b).toString())).andExpect(status().isNotFound());
  owner().update("UPDATE services SET active=false WHERE tenant_id=?",a.tenant);
  mvc.perform(get("/api/v1/public/"+a.slug+"/availability").param("serviceId",serviceId(a).toString())).andExpect(status().isNotFound());
  owner().update("UPDATE services SET active=true WHERE tenant_id=?",a.tenant);
  mvc.perform(post("/api/v1/public/"+a.slug+"/access-links").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("serviceId",serviceId(a),"start",java.time.Instant.parse(start).plusSeconds(7200),"name","Cliente","email",UUID.randomUUID()+"@example.test")))).andExpect(status().isConflict());
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(get("/api/v1/public/"+a.slug+"/availability").param("serviceId",serviceId(a).toString())).andExpect(status().isForbidden());
 }
 void ready(Account a)throws Exception{
  owner().update("UPDATE public_profiles SET description='Atendimento de teste',contact_email='public@example.test',service_mode='ONLINE' WHERE tenant_id=?",a.tenant);
  owner().update("INSERT INTO services(id,tenant_id,name,description,duration_minutes,price_cents,buffer_before_minutes,buffer_after_minutes,deposit_percent,active) VALUES (?,?,'Consulta','Serviço de teste',30,10000,0,0,50,true)",UUID.randomUUID(),a.tenant);
  mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("version",0,"keyType","EMAIL","pixKey","private@example.test","recipientName","Recebedor teste","paymentInstructions","Instruções privadas para pagamento.","cancellationPolicy","Solicite cancelamento pelo contato.","enabled",true,"confirmed",true,"changeReason","Configuração para teste")))).andExpect(status().isOk());
  var date=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(2);
  var schedule=Map.of("intervalMinutes",0,"weekly",List.of(),"exceptions",List.of(Map.of("date",date.toString(),"reason","Motivo privado","periods",List.of(Map.of("start","09:00","end","10:00")))));
  mvc.perform(put("/api/v1/admin/availability").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("version",0,"schedule",schedule)))).andExpect(status().isOk());
 }
 @Test void eligiblePageRequiresExplicitPublicationAndCanBeWithdrawn()throws Exception{
  var a=account();var b=account();ready(a);
  mvc.perform(get("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.canPublish").value(true)).andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.missingRequirements").isEmpty());
  mvc.perform(get("/api/v1/public/pages/"+a.slug)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/admin/publication").with(user(b.id.toString())).with(csrf()).param("tenantId",a.tenant.toString())).andExpect(status().isConflict());
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(status().isForbidden());
  for(int i=0;i<2;i++)mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isNoContent());
  String page=mvc.perform(get("/api/v1/public/pages/"+a.slug)).andExpect(status().isOk()).andExpect(jsonPath("$.bookingAvailable").value(false)).andReturn().getResponse().getContentAsString();
  assertThat(page).contains("public@example.test").doesNotContain("private@example.test","Motivo privado","Instruções privadas",a.tenant.toString());
  mvc.perform(delete("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/public/pages/"+a.slug)).andExpect(status().isNotFound());
 }
 @Test void publicationRechecksRequirementsAfterPreview()throws Exception{
  var a=account();ready(a);
  mvc.perform(get("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(jsonPath("$.canPublish").value(true));
  owner().update("UPDATE services SET active=false WHERE tenant_id=?",a.tenant);
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isConflict());
  owner().update("UPDATE services SET active=true WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE public_profiles SET description='' WHERE tenant_id=?",a.tenant);
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isConflict());
  owner().update("UPDATE public_profiles SET description='Completo' WHERE tenant_id=?",a.tenant);
  var payment=Map.of("version",1,"keyType","EMAIL","pixKey","private@example.test","recipientName","Recebedor teste","paymentInstructions","Instruções privadas para pagamento.","cancellationPolicy","Solicite cancelamento pelo contato.","enabled",false,"confirmed",true,"changeReason","Desativação para teste");
  mvc.perform(put("/api/v1/admin/payment-settings").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(payment))).andExpect(status().isOk());
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/public/pages/"+a.slug)).andExpect(status().isNotFound());
 }
 @Test void actualAvailabilityIncludesBlocksDurationAndExceptions()throws Exception{
  var a=account();ready(a);
  owner().update("UPDATE services SET duration_minutes=120 WHERE tenant_id=?",a.tenant);
  mvc.perform(get("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(jsonPath("$.canPublish").value(false));
  owner().update("UPDATE services SET duration_minutes=30 WHERE tenant_id=?",a.tenant);
  var day=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(2);
  var start=day.atTime(9,0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant();
  owner().update("INSERT INTO calendar_allocations(id,tenant_id,resource_id,kind,starts_at,ends_at,buffer_before,buffer_after,protected_start,protected_end,reason) VALUES (?,?,?,'BLOCK',?,?,0,0,?,?,'Privado')",UUID.randomUUID(),a.tenant,a.tenant,java.sql.Timestamp.from(start),java.sql.Timestamp.from(start.plusSeconds(3600)),java.sql.Timestamp.from(start),java.sql.Timestamp.from(start.plusSeconds(3600)));
  mvc.perform(get("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(jsonPath("$.canPublish").value(false));
  mvc.perform(post("/api/v1/admin/publication").with(user(a.id.toString())).with(csrf())).andExpect(status().isConflict());
  owner().update("UPDATE calendar_allocations SET active=false WHERE tenant_id=?",a.tenant);
  mvc.perform(get("/api/v1/admin/publication").with(user(a.id.toString()))).andExpect(jsonPath("$.canPublish").value(true));
 }
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
