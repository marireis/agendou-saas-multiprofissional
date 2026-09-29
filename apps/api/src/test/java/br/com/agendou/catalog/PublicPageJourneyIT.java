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

@SpringBootTest(properties={"agendou.mail-worker-initial-delay=3600000","agendou.trial-worker-delay=3600000","agendou.booking-expiration-delay=3600000"})
@AutoConfigureMockMvc @Testcontainers
class PublicPageJourneyIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc;@Autowired AuthService auth;@Autowired JdbcTemplate runtime;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @org.springframework.boot.test.mock.mockito.MockBean org.springframework.mail.javamail.JavaMailSender sender;
 @Autowired br.com.agendou.identity.MailDeliveryService delivery;
 @Autowired BookingService bookings;
 @Test void manualPixUsesBookingSnapshotAndExpiresWithoutExposingKey()throws Exception{
  var a=account();var start=publishAndFindStart(a);var c=client(a,start);var key=UUID.randomUUID();
  var response=reserve(a,c,key).andExpect(status().isCreated()).andReturn();String id=json.readTree(response.getResponse().getContentAsString()).get("id").asText();
  reserve(a,c,key).andExpect(status().isCreated());
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_intents WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
  owner().update("UPDATE payment_settings_versions SET pix_key='changed@example.test',recipient_name='Alterado' WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE services SET price_cents=20000 WHERE tenant_id=?",a.tenant);
  String path="/api/v1/public/"+a.slug+"/client/bookings/"+id+"/payment";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).cookie(c.cookie)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.pixKey").value("private@example.test")).andExpect(jsonPath("$.recipientName").value("Recebedor teste")).andExpect(jsonPath("$.totalCents").value(10000)).andExpect(jsonPath("$.depositCents").value(5000)).andExpect(jsonPath("$.remainingAfterDepositCents").value(5000)).andExpect(jsonPath("$.paymentAvailable").value(false));
  var other=client(a,java.time.Instant.parse(start).plusSeconds(1800).toString());reserve(a,other,UUID.randomUUID()).andExpect(status().isCreated());
  mvc.perform(get(path).cookie(other.cookie)).andExpect(status().isNotFound());
  owner().update("UPDATE public_profiles SET published=false WHERE tenant_id=?",a.tenant);
  mvc.perform(get(path).cookie(c.cookie)).andExpect(status().isOk());
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE id=?",UUID.fromString(id));
  mvc.perform(get(path).cookie(c.cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.pixKey").doesNotExist()).andExpect(jsonPath("$.instructions").doesNotExist());
  assertThat(owner().queryForObject("SELECT status FROM payment_intents WHERE booking_id=?",String.class,UUID.fromString(id))).isEqualTo("EXPIRED");
  assertThat(runtime.queryForObject("SELECT count(*) FROM payment_intents",Long.class)).isZero();
  for(String table:List.of("payment_transactions","payment_evidence","payment_refunds"))assertThat(runtime.queryForObject("SELECT count(*) FROM "+table,Long.class)).isZero();
 }
 @Autowired BookingExpirationJobs expirationJobs;
 BookingExpirationJobs.Claim claimFor(Account a){
  for(int i=0;i<100;i++){var claim=expirationJobs.claim().orElseThrow();if(claim.tenant().equals(a.tenant))return claim;expirationJobs.process(claim);}
  throw new AssertionError("Tenant não selecionado");
 }
 @Test void expirationLeaseCanBeRecoveredAndStaleWorkerCannotFinishNewLease()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));reserve(a,c,UUID.randomUUID()).andExpect(status().isCreated());
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE tenant_id=?",a.tenant);
  var first=claimFor(a);
  assertThat(owner().queryForObject("SELECT lease_token FROM booking_expiration_jobs WHERE tenant_id=?",UUID.class,a.tenant)).isEqualTo(first.token());
  var another=expirationJobs.claim();assertThat(another.map(value->value.tenant().equals(a.tenant)).orElse(false)).isFalse();another.ifPresent(expirationJobs::process);
  owner().update("UPDATE booking_expiration_jobs SET leased_until=now()-interval '1 second' WHERE tenant_id=?",a.tenant);
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
  BookingExpirationJobs.Claim replacement;
  try{
   var futures=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{gate.await();return expirationJobs.claim();})).toList();gate.countDown();
   var claimed=new ArrayList<BookingExpirationJobs.Claim>();for(var future:futures)future.get().ifPresent(claimed::add);
   var matches=claimed.stream().filter(value->value.tenant().equals(a.tenant)).toList();assertThat(matches).hasSize(1);replacement=matches.getFirst();
   claimed.stream().filter(value->!value.tenant().equals(a.tenant)).forEach(expirationJobs::process);
  }finally{pool.shutdownNow();}
  assertThat(replacement.token()).isNotEqualTo(first.token());
  expirationJobs.failed(first);assertThat(expirationJobs.process(first)).isZero();
  assertThat(owner().queryForObject("SELECT lease_token FROM booking_expiration_jobs WHERE tenant_id=?",UUID.class,a.tenant)).isEqualTo(replacement.token());
  assertThat(expirationJobs.process(replacement)).isEqualTo(1);assertThat(expirationJobs.process(replacement)).isZero();
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_events WHERE tenant_id=? AND event_type='EXPIRED'",Long.class,a.tenant)).isEqualTo(1);
 }
 @Test void expirationFailureRollsBackAndBackoffAllowsRetry()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));reserve(a,c,UUID.randomUUID()).andExpect(status().isCreated());
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE tenant_id=?",a.tenant);var claim=claimFor(a);
  owner().execute("ALTER TABLE booking_events ADD CONSTRAINT test_reject_expiry CHECK(event_type<>'EXPIRED') NOT VALID");
  try{assertThatThrownBy(()->expirationJobs.process(claim)).isInstanceOf(org.springframework.dao.DataAccessException.class);}finally{owner().execute("ALTER TABLE booking_events DROP CONSTRAINT test_reject_expiry");}
  assertThat(owner().queryForObject("SELECT status FROM bookings WHERE tenant_id=?",String.class,a.tenant)).isEqualTo("AWAITING_PAYMENT");
  assertThat(owner().queryForObject("SELECT active FROM calendar_allocations WHERE tenant_id=?",Boolean.class,a.tenant)).isTrue();
  assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE tenant_id=?",String.class,a.tenant)).isEqualTo("HELD");
  expirationJobs.failed(claim);
  assertThat(owner().queryForObject("SELECT failures FROM booking_expiration_jobs WHERE tenant_id=?",Integer.class,a.tenant)).isEqualTo(1);
  assertThat(owner().queryForObject("SELECT next_attempt_at>now() AND lease_token IS NULL FROM booking_expiration_jobs WHERE tenant_id=?",Boolean.class,a.tenant)).isTrue();
  owner().update("UPDATE booking_expiration_jobs SET next_attempt_at=now()-interval '1 second' WHERE tenant_id=?",a.tenant);
  assertThat(expirationJobs.process(claimFor(a))).isEqualTo(1);
  assertThat(owner().queryForObject("SELECT failures FROM booking_expiration_jobs WHERE tenant_id=?",Integer.class,a.tenant)).isZero();
 }
 @Test void expirationProcessesBoundedBatchesAndKeepsFutureReservations()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));reserve(a,c,UUID.randomUUID()).andExpect(status().isCreated());
  UUID original=owner().queryForObject("SELECT id FROM bookings WHERE tenant_id=?",UUID.class,a.tenant);
  for(int i=0;i<101;i++){
   UUID allocation=UUID.randomUUID(),booking=UUID.randomUUID();
   owner().update("INSERT INTO calendar_allocations(id,tenant_id,resource_id,kind,service_id,starts_at,ends_at,buffer_before,buffer_after,protected_start,protected_end,active,expires_at,reason) SELECT ?,tenant_id,resource_id,kind,service_id,starts_at,ends_at,buffer_before,buffer_after,protected_start,protected_end,false,expires_at,reason FROM calendar_allocations WHERE id=(SELECT allocation_id FROM bookings WHERE id=?)",allocation,original);
   owner().update("INSERT INTO bookings(id,tenant_id,customer_id,service_id,allocation_id,status,starts_at,ends_at,expires_at,timezone,service_snapshot,pix_snapshot,price_cents,deposit_cents,payment_version) SELECT ?,tenant_id,customer_id,service_id,?,'AWAITING_PAYMENT',starts_at,ends_at,now()-interval '1 minute',timezone,service_snapshot,pix_snapshot,price_cents,deposit_cents,payment_version FROM bookings WHERE id=?",booking,allocation,original);
   owner().update("INSERT INTO booking_usage(tenant_id,booking_id,month,state) SELECT tenant_id,?,month,'HELD' FROM booking_usage WHERE booking_id=?",booking,original);
  }
  assertThat(expirationJobs.process(claimFor(a))).isEqualTo(100);
  assertThat(expirationJobs.process(claimFor(a))).isEqualTo(1);
  assertThat(owner().queryForObject("SELECT count(*) FROM bookings WHERE tenant_id=? AND status='EXPIRED'",Long.class,a.tenant)).isEqualTo(101);
  assertThat(owner().queryForObject("SELECT status FROM bookings WHERE id=?",String.class,original)).isEqualTo("AWAITING_PAYMENT");
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_usage WHERE tenant_id=? AND state='RELEASED'",Long.class,a.tenant)).isEqualTo(101);
 }
 record Client(jakarta.servlet.http.Cookie cookie,String quote){}
 String portalEmail(Account a){return owner().queryForObject("SELECT email FROM customers WHERE tenant_id=? LIMIT 1",String.class,a.tenant);}
 String portalToken(Account a){String body=owner().queryForObject("SELECT body FROM mail_outbox WHERE purpose='CUSTOMER_PORTAL' AND token_hash IN (SELECT token_hash FROM customer_portal_tokens WHERE tenant_id=?)",String.class,a.tenant);return body.split("#token=")[1].split("&email=")[0];}
 org.springframework.test.web.servlet.ResultActions portalRequest(String slug,String email)throws Exception{return mvc.perform(post("/api/v1/public/"+slug+"/client/access-links").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("email",email))));}
 org.springframework.test.web.servlet.ResultActions portalConsume(Account a,String email,String token)throws Exception{return mvc.perform(post("/api/v1/public/"+a.slug+"/client/access-links/consume").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("email",email,"token",token))));}
 @Test void portalReopensHistoryAfterUnpublishingAndSubscriptionExpiry()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));var created=reserve(a,c,UUID.randomUUID()).andExpect(status().isCreated()).andReturn();var id=json.readTree(created.getResponse().getContentAsString()).get("id").asText();
  String email=portalEmail(a);owner().update("UPDATE public_profiles SET published=false WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE tenant_id=?",a.tenant);
  portalRequest(a.slug,email).andExpect(status().isAccepted());String token=portalToken(a);
  for(int i=0;i<100&&delivery.deliverNext();i++){}
  assertThat(owner().queryForObject("SELECT status FROM mail_outbox WHERE purpose='CUSTOMER_PORTAL' AND token_hash IN (SELECT token_hash FROM customer_portal_tokens WHERE tenant_id=?)",String.class,a.tenant)).isEqualTo("SENT");
  var access=portalConsume(a,email,token).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings").cookie(access)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].status").value("EXPIRED")).andExpect(jsonPath("$.hasMore").value(false));
  String detail=mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings/"+id).cookie(access)).andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(2)).andExpect(jsonPath("$.cancellationPolicy").value("Solicite cancelamento pelo contato.")).andReturn().getResponse().getContentAsString();
  assertThat(detail).doesNotContain("pixKey","private@example.test","paymentInstructions");
  portalConsume(a,email,token).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings").cookie(access).param("offset","-1")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/public/"+a.slug+"/client/logout").cookie(access)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/public/"+a.slug+"/client/logout").cookie(access).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings").cookie(access)).andExpect(status().isUnauthorized());
 }
 @Test void portalIsScopedToVerifiedEmailAndTenant()throws Exception{
  var a=account();var start=publishAndFindStart(a);var first=client(a,start);var second=client(a,java.time.Instant.parse(start).plusSeconds(1800).toString());
  reserve(a,first,UUID.randomUUID()).andExpect(status().isCreated());var other=reserve(a,second,UUID.randomUUID()).andExpect(status().isCreated()).andReturn();String otherId=json.readTree(other.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings").cookie(first.cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings/"+otherId).cookie(first.cookie)).andExpect(status().isNotFound());
  var b=account();mvc.perform(get("/api/v1/public/"+b.slug+"/client/bookings").cookie(first.cookie)).andExpect(status().isUnauthorized());
  assertThat(runtime.queryForObject("SELECT count(*) FROM customer_portal_tokens",Long.class)).isZero();
 }
 @Test void portalLinksAreGenericRevocableExpiringAndRequireCsrf()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));reserve(a,c,UUID.randomUUID()).andExpect(status().isCreated());String email=portalEmail(a);
  var unknown=portalRequest("unknown",UUID.randomUUID()+"@example.test").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
  var known=portalRequest(a.slug,email).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();assertThat(unknown).isEqualTo(known);String first=portalToken(a);
  portalRequest(a.slug,email).andExpect(status().isAccepted());String second=portalToken(a);assertThat(second).isNotEqualTo(first);
  portalConsume(a,email,first).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/public/"+a.slug+"/client/access-links/consume").contentType("application/json").content(json.writeValueAsBytes(Map.of("email",email,"token",second)))).andExpect(status().isForbidden());
  owner().update("UPDATE customer_portal_tokens SET expires_at=now()-interval '1 minute' WHERE tenant_id=?",a.tenant);
  portalConsume(a,email,second).andExpect(status().isBadRequest());
  owner().execute("SELECT public.purge_customer_access()");assertThat(owner().queryForObject("SELECT count(*) FROM customer_portal_tokens WHERE tenant_id=?",Long.class,a.tenant)).isZero();
 }
 Client client(Account a,String start)throws Exception{
  requestAccess(a,start,UUID.randomUUID()+"@example.test");var token=customerToken(a);
  var result=mvc.perform(post("/api/v1/public/"+a.slug+"/access-links/consume").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("token",token)))).andExpect(status().isOk()).andReturn();
  return new Client(result.getResponse().getCookie("SESSION"),json.readTree(result.getResponse().getContentAsString()).get("quote").asText());
 }
 org.springframework.test.web.servlet.ResultActions reserve(Account a,Client client,UUID key)throws Exception{return mvc.perform(post("/api/v1/public/"+a.slug+"/bookings").cookie(client.cookie).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(Map.of("quote",client.quote))));}
 @Test void temporaryBookingIsAtomicIdempotentPrivateAndKeepsSnapshots()throws Exception{
  var a=account();var start=publishAndFindStart(a);var c=client(a,start);var key=UUID.randomUUID();
  var result=reserve(a,c,key).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT")).andReturn();String body=result.getResponse().getContentAsString();var id=UUID.fromString(json.readTree(body).get("id").asText());
  assertThat(body).doesNotContain("pixKey","private@example.test");
  reserve(a,c,key).andExpect(status().isCreated()).andExpect(content().json(body));
  reserve(a,c,UUID.randomUUID()).andExpect(status().isConflict());
  for(var table:List.of("bookings","customers","booking_usage","booking_requests","payment_intents","calendar_allocations"))assertThat(owner().queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
  assertThat(owner().queryForObject("SELECT count(*) FROM mail_outbox WHERE booking_id=?",Long.class,id)).isEqualTo(1);
  assertThat(runtime.queryForObject("SELECT count(*) FROM bookings",Long.class)).isZero();
  mvc.perform(get("/api/v1/admin/calendar").with(user(a.id.toString())).param("from",java.time.Instant.parse(start).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().toString()).param("days","1")).andExpect(status().isOk()).andExpect(jsonPath("$.events[0].bookingId").value(id.toString())).andExpect(jsonPath("$.events[0].serviceName").value("Consulta"));
  owner().update("UPDATE services SET price_cents=99999,name='Novo nome' WHERE tenant_id=?",a.tenant);
  mvc.perform(get("/api/v1/public/"+a.slug+"/bookings/"+id).cookie(c.cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.priceCents").value(10000)).andExpect(jsonPath("$.serviceName").value("Consulta"));
  var other=client(a,java.time.Instant.parse(start).plusSeconds(1800).toString());
  mvc.perform(get("/api/v1/public/"+a.slug+"/bookings/"+id).cookie(other.cookie)).andExpect(status().isNotFound());
  reserve(a,new Client(c.cookie,"0".repeat(64)),key).andExpect(status().isConflict());
  assertThat(owner().queryForObject("SELECT pix_snapshot->>'pixKey' FROM bookings WHERE id=?",String.class,id)).isEqualTo("private@example.test");
  for(int i=0;i<100&&delivery.deliverNext();i++){}
  assertThat(owner().queryForObject("SELECT status FROM mail_outbox WHERE booking_id=?",String.class,id)).isEqualTo("SENT");
 }
 @Test void failureWritingOutboxRollsBackEveryBookingEffect()throws Exception{
  var a=account();var start=publishAndFindStart(a);var c=client(a,start);
  owner().execute("ALTER TABLE mail_outbox ADD CONSTRAINT test_reject_booking_notice CHECK(booking_id IS NULL) NOT VALID");
  try{reserve(a,c,UUID.randomUUID()).andExpect(status().isConflict());
   for(var table:List.of("bookings","customers","booking_usage","booking_requests","payment_intents","calendar_allocations","booking_events"))assertThat(owner().queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id=?",Long.class,a.tenant)).isZero();
  }finally{owner().execute("ALTER TABLE mail_outbox DROP CONSTRAINT test_reject_booking_notice");}
 }
 @Test void quoteChangeCsrfAndPublicationPreventUnintendedBooking()throws Exception{
  var a=account();var start=publishAndFindStart(a);var c=client(a,start);
  mvc.perform(post("/api/v1/public/"+a.slug+"/bookings").cookie(c.cookie).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsBytes(Map.of("quote",c.quote)))).andExpect(status().isForbidden());
  owner().update("UPDATE services SET price_cents=12000 WHERE tenant_id=?",a.tenant);
  reserve(a,c,UUID.randomUUID()).andExpect(status().isConflict());
  owner().update("UPDATE public_profiles SET published=false WHERE tenant_id=?",a.tenant);
  reserve(a,c,UUID.randomUUID()).andExpect(status().isNotFound());
  assertThat(owner().queryForObject("SELECT count(*) FROM bookings WHERE tenant_id=?",Long.class,a.tenant)).isZero();
 }
 @Test void concurrentClaimsHaveOneWinnerAndExpirationReleasesQuota()throws Exception{
  var a=account();var start=publishAndFindStart(a);var first=client(a,start);var second=client(a,start);
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
  try{var futures=List.of(first,second).stream().map(c->pool.submit(()->{gate.await();return reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getStatus();})).toList();gate.countDown();assertThat(List.of(futures.get(0).get(),futures.get(1).get())).containsExactlyInAnyOrder(201,409);}finally{pool.shutdownNow();}
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE tenant_id=?",a.tenant);
  bookings.expireTenant(a.tenant);bookings.expireTenant(a.tenant);
  assertThat(owner().queryForObject("SELECT status FROM bookings WHERE tenant_id=?",String.class,a.tenant)).isEqualTo("EXPIRED");
  assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE tenant_id=?",String.class,a.tenant)).isEqualTo("RELEASED");
  assertThat(owner().queryForObject("SELECT active FROM calendar_allocations WHERE tenant_id=?",Boolean.class,a.tenant)).isFalse();
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_events WHERE tenant_id=? AND event_type='EXPIRED'",Long.class,a.tenant)).isEqualTo(1);
 }
 @Test void monthlyQuotaRejectsOnlyNewRequestsAndExpiredUsageIsReleased()throws Exception{
  var a=account();var start=publishAndFindStart(a);var c=client(a,start);var later=client(a,java.time.Instant.parse(start).plusSeconds(1800).toString());
  owner().update("UPDATE subscriptions SET plan_code='BASIC',status='PAID_ACTIVE',trial_started_at=NULL,trial_ends_at=NULL,paid_until=now()+interval '30 days' WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE plans SET monthly_booking_limit=1 WHERE code='BASIC'");
  try{var key=UUID.randomUUID();reserve(a,c,key).andExpect(status().isCreated());reserve(a,c,key).andExpect(status().isCreated());reserve(a,later,UUID.randomUUID()).andExpect(status().isConflict());
   owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE tenant_id=?",a.tenant);
   reserve(a,later,UUID.randomUUID()).andExpect(status().isCreated());
   assertThat(owner().queryForObject("SELECT count(*) FROM booking_usage WHERE tenant_id=? AND state='HELD'",Long.class,a.tenant)).isEqualTo(1);
  }finally{owner().update("UPDATE plans SET monthly_booking_limit=NULL WHERE code='BASIC'");}
 }
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
  var result=mvc.perform(post("/api/v1/public/"+a.slug+"/access-links/consume").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("token",token)))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.priceCents").value(10000)).andExpect(jsonPath("$.depositCents").value(5000)).andExpect(jsonPath("$.slotAvailable").value(true)).andExpect(jsonPath("$.reservationEnabled").value(true)).andReturn();
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
