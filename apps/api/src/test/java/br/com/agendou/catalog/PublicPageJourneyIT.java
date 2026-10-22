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
class PublicPageJourneyIT extends br.com.agendou.support.IsolatedSpringIntegrationTest {
 String reissueBody(int version)throws Exception{return json.writeValueAsString(Map.of("expectedVersion",version,"reason","Cliente verificado perdeu o codigo","identityVerified",true));}
 @Test void reissuedCodeRevokesOldCodePortalReceiptAndPrivateFiles()throws Exception{
  var a=account();String start=publishAndFindStart(a);var bookingSession=phoneSession(a,start,"11999990000");UUID key=UUID.randomUUID();var booking=phoneReserve(a,bookingSession,key);String id=booking.get("id").asText(),oldCode=booking.get("accessCode").asText(),portal="/api/v1/public/"+a.slug+"/client",path="/api/v1/admin/bookings/"+id+"/access-code";
  String quote=json.readTree(mvc.perform(get("/api/v1/public/"+a.slug+"/phone/review").cookie(bookingSession)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("quote").asText();
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  var access=Map.of("firstName","Maria","lastName","Silva","phone","11999990000","code",oldCode);
  var oldSession=mvc.perform(post(portal+"/code").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(access))).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.accessCode").doesNotExist());
  mvc.perform(post(path).with(user(a.id.toString())).contentType("application/json").content(reissueBody(0))).andExpect(status().isForbidden());
  var b=account();mvc.perform(post(path).with(user(b.id.toString())).with(csrf()).contentType("application/json").content(reissueBody(0))).andExpect(status().isNotFound());
  String newCode=json.readTree(mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(reissueBody(0))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString()).get("accessCode").asText();
  assertThat(newCode).hasSize(43);assertThat(CustomerContact.hash(newCode).equals(CustomerContact.hash(oldCode))).isFalse();
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(reissueBody(0))).andExpect(status().isConflict());
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(reissueBody(1))).andExpect(status().isConflict());
  mvc.perform(post(portal+"/code").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(access))).andExpect(status().isBadRequest());
  for(String suffix:List.of("/bookings","/bookings/"+id,"/bookings/"+id+"/payment","/bookings/"+id+"/evidence/"+UUID.randomUUID()))mvc.perform(get(portal+suffix).cookie(oldSession)).andExpect(status().isNotFound());
  mvc.perform(post(portal+"/bookings/"+id+"/evidence").cookie(oldSession).with(csrf()).contentType("application/octet-stream").content(new byte[]{1,2})).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/public/"+a.slug+"/phone/current").cookie(bookingSession)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/public/"+a.slug+"/phone/bookings").cookie(bookingSession).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(Map.of("quote",quote,"accepted",true)))).andExpect(status().isNotFound());
  var fresh=mvc.perform(post(portal+"/code").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("firstName","Maria","lastName","Silva","phone","11999990000","code",newCode)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
  mvc.perform(get(portal+"/bookings/"+id+"/payment").cookie(fresh)).andExpect(status().isOk());
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.history.length()").value(1)).andExpect(jsonPath("$.accessCode").doesNotExist());
  assertThat(runtime.queryForObject("SELECT count(*) FROM booking_code_reissues",Long.class)).isZero();
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_code_reissues WHERE tenant_id=? AND recorded_by=?",Long.class,a.tenant,a.id)).isEqualTo(1);
 }
 @Test void codeReissueAuditFailureRollsBackAndRequiresConfirmationAndOperationalTenant()throws Exception{
  var a=account();var session=phoneSession(a,publishAndFindStart(a),"11999990000");var booking=phoneReserve(a,session,UUID.randomUUID());UUID id=UUID.fromString(booking.get("id").asText());String path="/api/v1/admin/bookings/"+id+"/access-code";
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("expectedVersion",0,"reason","Cliente perdeu o codigo","identityVerified",false)))).andExpect(status().isUnprocessableEntity());
  owner().execute("ALTER TABLE booking_code_reissues ADD CONSTRAINT test_reissue_failure CHECK(false) NOT VALID");
  try{mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(reissueBody(0))).andExpect(status().isConflict());}finally{owner().execute("ALTER TABLE booking_code_reissues DROP CONSTRAINT test_reissue_failure");}
  assertThat(owner().queryForObject("SELECT code_hash FROM booking_access_codes WHERE booking_id=?",String.class,id)).isEqualTo(CustomerContact.hash(booking.get("accessCode").asText()));
  assertThat(owner().queryForObject("SELECT version FROM booking_access_codes WHERE booking_id=?",Integer.class,id)).isZero();
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(status().isOk());
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(reissueBody(0))).andExpect(status().isForbidden());
 }
 @Test void concurrentCodeReissuesHaveOnlyOneWinner()throws Exception{
  var a=account();var session=phoneSession(a,publishAndFindStart(a),"11999990000");String id=phoneReserve(a,session,UUID.randomUUID()).get("id").asText(),path="/api/v1/admin/bookings/"+id+"/access-code",body=reissueBody(0);
  var gate=new java.util.concurrent.CountDownLatch(1);try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){var tasks=new ArrayList<java.util.concurrent.Future<Integer>>();for(int i=0;i<2;i++)tasks.add(executor.submit(()->{gate.await();return mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus();}));gate.countDown();assertThat(List.of(tasks.get(0).get(),tasks.get(1).get())).containsExactlyInAnyOrder(200,409);}
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_code_reissues WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
 }

 @Test void financeTotalsAreIndependentOfPaginationAndFlagLateReceipts()throws Exception{
  var a=account();String start=publishAndFindStart(a);var session=phoneSession(a,start,"11999990000");UUID id=UUID.fromString(phoneReserve(a,session,UUID.randomUUID()).get("id").asText());
  owner().update("UPDATE public_profiles SET timezone='America/Sao_Paulo' WHERE tenant_id=?",a.tenant);
  owner().update("INSERT INTO payment_transactions(id,tenant_id,intent_id,amount_cents,bank_reference,received_at,recorded_by,receiving_account,correlation_id) SELECT gen_random_uuid(),p.tenant_id,p.id,100,'fixture-'||g,?::timestamptz,?,'Conta teste','fixture' FROM payment_intents p CROSS JOIN generate_series(1,51) g WHERE p.tenant_id=? AND p.booking_id=?",start,a.id,a.tenant,id);
  String day=java.time.Instant.parse(start).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().toString(),path="/api/v1/admin/finance";
  var first=json.readTree(mvc.perform(get(path).param("from",day).param("to",day).with(user(a.id.toString()))).andExpect(jsonPath("$.summary.receivedCents").value(5100)).andExpect(jsonPath("$.items.length()").value(50)).andExpect(jsonPath("$.hasMore").value(true)).andReturn().getResponse().getContentAsString());
  var last=json.readTree(mvc.perform(get(path).param("from",day).param("to",day).param("offset","50").with(user(a.id.toString()))).andExpect(jsonPath("$.summary.receivedCents").value(5100)).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasMore").value(false)).andReturn().getResponse().getContentAsString());
  Set<String> ids=new HashSet<>();for(var page:List.of(first,last))for(var item:page.get("items"))assertThat(ids.add(item.get("id").asText())).isTrue();
  for(String state:List.of("CONFIRMED","COMPLETED","NO_SHOW")){
   owner().update("UPDATE bookings SET status=? WHERE id=?",state,id);
   mvc.perform(get(path).param("from",day).param("to",day).param("view","BALANCES").with(user(a.id.toString()))).andExpect(jsonPath("$.summary.totalDueCents").value(4900)).andExpect(jsonPath("$.summary.depositDueCents").value(0));
  }
  owner().update("UPDATE bookings SET status='EXPIRED' WHERE id=?",id);
  mvc.perform(get(path).param("from",day).param("to",day).param("view","BALANCES").with(user(a.id.toString()))).andExpect(jsonPath("$.summary.totalDueCents").value(0)).andExpect(jsonPath("$.summary.reviewCents").value(5100)).andExpect(jsonPath("$.items.length()").value(1));
 }

 @Test void financeSeparatesCashDatesFromCurrentCancellationBalances()throws Exception{
  var a=account();var b=account();String start=publishAndFindStart(a);var session=phoneSession(a,start,"11999990000");UUID id=UUID.fromString(phoneReserve(a,session,UUID.randomUUID()).get("id").asText());
  String payments="/api/v1/admin/payment-reviews/"+id;
  mvc.perform(post(payments+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(2000,"finance-one"))).andExpect(status().isOk());
  var receipt=json.readTree(mvc.perform(post(payments+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"finance-two"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  String transaction=receipt.get("transactions").get(1).get("id").asText();
  mvc.perform(post(payments+"/cancel").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("reason","Cancelamento acordado com cliente","expectedNetCents",7000,"refundCents",5000,"policyReviewed",true)))).andExpect(status().isOk());
  mvc.perform(post(payments+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(transaction,1000,"finance-refund-one"))).andExpect(status().isOk());
  mvc.perform(post(payments+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(transaction,500,"finance-refund-two"))).andExpect(status().isOk());
  owner().update("UPDATE public_profiles SET timezone='America/Sao_Paulo' WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE payment_transactions SET received_at='2026-10-01T03:00:00Z' WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE payment_refunds SET refunded_at='2026-10-02T03:00:00Z' WHERE tenant_id=?",a.tenant);
  String path="/api/v1/admin/finance";
  mvc.perform(get(path).param("from","2026-10-01").param("to","2026-10-02")).andExpect(status().isUnauthorized());
  mvc.perform(get(path).param("from","2026-10-01").param("to","2026-10-02").with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.summary.receivedCents").value(7000)).andExpect(jsonPath("$.summary.refundedCents").value(1500)).andExpect(jsonPath("$.summary.netCents").value(5500)).andExpect(jsonPath("$.items.length()").value(4));
  mvc.perform(get(path).param("from","2026-10-02").param("to","2026-10-02").with(user(a.id.toString()))).andExpect(jsonPath("$.summary.receivedCents").value(0)).andExpect(jsonPath("$.summary.netCents").value(-1500));
  mvc.perform(get(path).param("from","2026-09-30").param("to","2026-09-30").with(user(a.id.toString()))).andExpect(jsonPath("$.items.length()").value(0));
  String day=java.time.Instant.parse(start).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().toString();
  mvc.perform(get(path).param("from",day).param("to",day).param("view","BALANCES").with(user(a.id.toString()))).andExpect(jsonPath("$.summary.totalDueCents").value(0)).andExpect(jsonPath("$.summary.refundDueCents").value(3500)).andExpect(jsonPath("$.items[0].netCents").value(5500));
  mvc.perform(get(path).param("from",day).param("to",day).with(user(b.id.toString()))).andExpect(jsonPath("$.summary.totalDueCents").value(0)).andExpect(jsonPath("$.items.length()").value(0));
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(get(path).param("from",day).param("to",day).with(user(a.id.toString()))).andExpect(status().isOk());
 }
 @Test void financeExcludesExpiredForecastsAndValidatesRange()throws Exception{
  var a=account();String start=publishAndFindStart(a);var session=phoneSession(a,start,"11999990000");UUID id=UUID.fromString(phoneReserve(a,session,UUID.randomUUID()).get("id").asText());
  String day=java.time.Instant.parse(start).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().toString(),path="/api/v1/admin/finance";
  mvc.perform(get(path).param("from",day).param("to",day).param("view","BALANCES").with(user(a.id.toString()))).andExpect(jsonPath("$.summary.totalDueCents").value(10000)).andExpect(jsonPath("$.summary.depositDueCents").value(5000));
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE id=?",id);
  mvc.perform(get(path).param("from",day).param("to",day).param("view","BALANCES").with(user(a.id.toString()))).andExpect(jsonPath("$.summary.totalDueCents").value(0)).andExpect(jsonPath("$.items.length()").value(0));
  mvc.perform(get(path).param("from","2026-02-02").param("to","2026-02-01").with(user(a.id.toString()))).andExpect(status().isBadRequest());
  mvc.perform(get(path).param("from","2025-01-01").param("to","2026-12-31").with(user(a.id.toString()))).andExpect(status().isBadRequest());
  mvc.perform(get(path).param("from",day).param("to",day).param("offset","-1").with(user(a.id.toString()))).andExpect(status().isBadRequest());
 }

 @Test void notificationsArePrivateIdempotentAndPerAdministrator()throws Exception{
  var a=account();var b=account();String start=publishAndFindStart(a);
  var session=phoneSession(a,start,"11999990000");phoneReserve(a,session,UUID.randomUUID());String path="/api/v1/admin/notifications";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  var feed=json.readTree(mvc.perform(get(path).with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.unreadCount").value(1)).andReturn().getResponse().getContentAsString());
  String id=feed.get("items").get(0).get("id").asText();
  mvc.perform(get(path).with(user(b.id.toString()))).andExpect(jsonPath("$.items.length()").value(0));
  mvc.perform(get(path).param("before",id).with(user(b.id.toString()))).andExpect(status().isNotFound());
  mvc.perform(post(path+"/"+id+"/read").with(user(b.id.toString())).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(post(path+"/"+id+"/read").with(user(a.id.toString()))).andExpect(status().isForbidden());
  for(int i=0;i<2;i++)mvc.perform(post(path+"/"+id+"/read").with(user(a.id.toString())).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get(path).param("unreadOnly","true").with(user(a.id.toString()))).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.unreadCount").value(0));
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(jsonPath("$.items[0].read").value(true));
  assertThat(owner().queryForObject("SELECT count(*) FROM notification_reads WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
  assertThat(runtime.queryForObject("SELECT count(*) FROM notification_reads",Long.class)).isZero();
  owner().update("UPDATE memberships SET tenant_id=? WHERE user_id=?",a.tenant,b.id);
  mvc.perform(get(path).with(user(b.id.toString()))).andExpect(jsonPath("$.unreadCount").value(1));
 }
 @Test void notificationCursorKeepsSameTimestampEventsAndCoversLifecycle()throws Exception{
  var a=account();String start=publishAndFindStart(a);var session=phoneSession(a,start,"11999990000");
  UUID booking=UUID.fromString(phoneReserve(a,session,UUID.randomUUID()).get("id").asText());
  var kinds=List.of("REQUESTED","IN_REVIEW","CONFIRMED","RESCHEDULED","EXPIRED","CANCELED","COMPLETED","NO_SHOW","PAYMENT_RECORDED","REFUND_RECORDED");
  for(int i=0;i<60;i++)owner().update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type,created_at) VALUES (?,?,?,?,now()-interval '1 day')",UUID.randomUUID(),a.tenant,booking,kinds.get(i%kinds.size()));
  owner().update("INSERT INTO booking_events(id,tenant_id,booking_id,event_type) VALUES (?,?,?,'ASSISTED_REQUESTED')",UUID.randomUUID(),a.tenant,booking);
  owner().update("UPDATE booking_events SET created_at='2026-01-01T00:00:00Z' WHERE tenant_id=?",a.tenant);
  String path="/api/v1/admin/notifications";
  var first=json.readTree(mvc.perform(get(path).with(user(a.id.toString()))).andExpect(jsonPath("$.items.length()").value(50)).andExpect(jsonPath("$.unreadCount").value(61)).andReturn().getResponse().getContentAsString());
  var second=json.readTree(mvc.perform(get(path).param("before",first.get("nextCursor").asText()).with(user(a.id.toString()))).andExpect(jsonPath("$.items.length()").value(11)).andExpect(jsonPath("$.nextCursor").value("")).andReturn().getResponse().getContentAsString());
  Set<String> ids=new HashSet<>(),types=new HashSet<>();for(var page:List.of(first,second))for(var item:page.get("items")){assertThat(ids.add(item.get("id").asText())).isTrue();types.add(item.get("type").asText());}assertThat(types).containsExactlyInAnyOrderElementsOf(kinds);
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(status().isOk());
 }
 jakarta.servlet.http.Cookie phoneSession(Account a,String start,String phone)throws Exception{
  var result=mvc.perform(post("/api/v1/public/"+a.slug+"/phone/review").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("serviceId",serviceId(a),"start",start,"firstName","Maria","lastName","Silva","phone",phone)))).andExpect(status().isOk()).andReturn();return result.getResponse().getCookie("SESSION");
 }
 com.fasterxml.jackson.databind.JsonNode phoneReserve(Account a,jakarta.servlet.http.Cookie session,UUID key)throws Exception{
  String quote=json.readTree(mvc.perform(get("/api/v1/public/"+a.slug+"/phone/review").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("quote").asText();
  return json.readTree(mvc.perform(post("/api/v1/public/"+a.slug+"/phone/bookings").cookie(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(Map.of("quote",quote,"accepted",true)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
 }
 @Test void phoneBookingIsAnonymousIdempotentAndCodeScoped()throws Exception{
  var a=account();String start=publishAndFindStart(a);var first=phoneSession(a,start,"(11) 99999-0000");UUID key=UUID.randomUUID();var booking=phoneReserve(a,first,key);String code=booking.get("accessCode").asText(),id=booking.get("id").asText();assertThat(code).hasSize(43);assertThat(phoneReserve(a,first,key).get("id").asText()).isEqualTo(id);
  var second=phoneSession(a,java.time.Instant.parse(start).plusSeconds(1800).toString(),"+55 11 99999-0000");String other=phoneReserve(a,second,UUID.randomUUID()).get("id").asText();
  assertThat(owner().queryForObject("SELECT count(*) FROM customers WHERE tenant_id=? AND email IS NULL",Long.class,a.tenant)).isEqualTo(1);
  assertThat(owner().queryForObject("SELECT count(*) FROM mail_outbox WHERE booking_id IN (SELECT id FROM bookings WHERE tenant_id=?)",Long.class,a.tenant)).isZero();assertThat(runtime.queryForObject("SELECT count(*) FROM booking_access_codes",Long.class)).isZero();
  String path="/api/v1/public/"+a.slug+"/client";var access=json.writeValueAsBytes(Map.of("firstName","Maria","lastName","Silva","phone","11999990000","code",code));
  mvc.perform(post(path+"/code").contentType("application/json").content(access)).andExpect(status().isForbidden());
  mvc.perform(get(path+"/bookings/"+id)).andExpect(status().isUnauthorized());
  var b=account();mvc.perform(post("/api/v1/public/"+b.slug+"/client/code").with(csrf()).contentType("application/json").content(access)).andExpect(status().isBadRequest());
  mvc.perform(post(path+"/code").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("firstName","Outra","lastName","Pessoa","phone","11999990000","code",code)))).andExpect(status().isBadRequest());
  owner().update("UPDATE public_profiles SET published=false WHERE tenant_id=?",a.tenant);
  var session=mvc.perform(post(path+"/code").with(csrf()).contentType("application/json").content(access)).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
  mvc.perform(get(path+"/bookings").cookie(session)).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(id));
  mvc.perform(get(path+"/bookings/"+other).cookie(session)).andExpect(status().isNotFound());mvc.perform(get(path+"/bookings/"+other+"/payment").cookie(session)).andExpect(status().isNotFound());
  mvc.perform(get(path+"/bookings/"+id+"/payment").cookie(session)).andExpect(status().isOk());
  mvc.perform(post(path+"/logout").cookie(first).with(csrf())).andExpect(status().isNoContent());mvc.perform(get("/api/v1/public/"+a.slug+"/phone/current").cookie(first)).andExpect(status().isNotFound());
 }
 @Test void phoneBookingRollsBackAndRequiresPublishedPage()throws Exception{
  var a=account();String start=publishAndFindStart(a);var session=phoneSession(a,start,"11988887777");
  owner().execute("ALTER TABLE booking_access_codes ADD CONSTRAINT test_code_failure CHECK(false) NOT VALID");
  try{String quote=json.readTree(mvc.perform(get("/api/v1/public/"+a.slug+"/phone/review").cookie(session)).andReturn().getResponse().getContentAsString()).get("quote").asText();mvc.perform(post("/api/v1/public/"+a.slug+"/phone/bookings").cookie(session).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsBytes(Map.of("quote",quote,"accepted",true)))).andExpect(status().isConflict());
   for(String table:List.of("customers","bookings","calendar_allocations","payment_intents","booking_usage","booking_requests"))assertThat(owner().queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id=?",Long.class,a.tenant)).isZero();
  }finally{owner().execute("ALTER TABLE booking_access_codes DROP CONSTRAINT test_code_failure");}
  owner().update("UPDATE public_profiles SET published=false WHERE tenant_id=?",a.tenant);mvc.perform(get("/api/v1/public/"+a.slug+"/phone/review").cookie(session)).andExpect(status().isNotFound());
 }
 @Test void assistedPhoneCustomerReceivesPrivateCode()throws Exception{
  var a=account();String start=publishAndFindStart(a);UUID customer=UUID.fromString(json.readTree(mvc.perform(post("/api/v1/admin/customers").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(customerBody("Maria Silva","",0))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText());
  String body=assistedBody(a,start,assistedQuote(a,customer),UUID.randomUUID());String path="/api/v1/admin/customers/"+customer+"/bookings";
  var created=mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();var session=created.getResponse().getCookie("SESSION");var result=json.readTree(created.getResponse().getContentAsString());String code=result.get("accessCode").asText();
  mvc.perform(post(path).cookie(session).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(jsonPath("$.accessCode").value(code));
  mvc.perform(get("/api/v1/admin/customers").with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].email").value(""));
  mvc.perform(post("/api/v1/public/"+a.slug+"/client/code").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("firstName","Maria","lastName","Silva","phone","11999990000","code",code)))).andExpect(status().isOk());
 }
 String customerBody(String name,String email,int version)throws Exception{return json.writeValueAsString(Map.of("name",name,"email",email,"phone","11999990000","version",version));}
 UUID manualCustomer(Account a)throws Exception{return UUID.fromString(json.readTree(mvc.perform(post("/api/v1/admin/customers").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(customerBody("Cliente manual",UUID.randomUUID()+"@example.test",0))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText());}
 String assistedQuote(Account a,UUID customer)throws Exception{return json.readTree(mvc.perform(get("/api/v1/admin/customers/"+customer+"/booking-quote").param("serviceId",serviceId(a).toString()).with(user(a.id.toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("quote").asText();}
 String assistedBody(Account a,String start,String quote,UUID key)throws Exception{return json.writeValueAsString(Map.of("serviceId",serviceId(a),"start",start,"quote",quote,"requestKey",key,"confirmed",true));}
 @Test void customerDirectoryDeduplicatesVersionsAndIsolatesTenants()throws Exception{
  var a=account();var b=account();String path="/api/v1/admin/customers",email=UUID.randomUUID()+"@example.test";String body=customerBody("Cliente teste",email.toUpperCase(Locale.ROOT),0);
  mvc.perform(post(path).with(user(a.id.toString())).contentType("application/json").content(body)).andExpect(status().isForbidden());
  UUID id=UUID.fromString(json.readTree(mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText());
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(customerBody("Outro nome",email,0))).andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.name").value("Cliente teste"));
  mvc.perform(get(path+"/"+id).with(user(b.id.toString()))).andExpect(status().isNotFound());
  mvc.perform(put(path+"/"+id).with(user(b.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
  mvc.perform(put(path+"/"+id).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(customerBody("Nome atualizado",email,0))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(put(path+"/"+id).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
  mvc.perform(get(path).param("search","1199999").with(user(a.id.toString()))).andExpect(jsonPath("$.items.length()").value(1));
  assertThat(owner().queryForObject("SELECT count(*) FROM customer_changes WHERE customer_id=?",Long.class,id)).isEqualTo(2);assertThat(runtime.queryForObject("SELECT count(*) FROM customer_changes",Long.class)).isZero();
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(get(path+"/"+id).with(user(a.id.toString()))).andExpect(status().isOk());
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
 }
 @Test void assistedBookingWorksPrivatelyAndPreservesIdentityAndIdempotency()throws Exception{
  var a=account();String start=publishAndFindStart(a);owner().update("UPDATE public_profiles SET published=false WHERE tenant_id=?",a.tenant);UUID customer=manualCustomer(a);String quote=assistedQuote(a,customer),path="/api/v1/admin/customers/"+customer+"/bookings",body=assistedBody(a,start,quote,UUID.randomUUID());
  mvc.perform(post(path).with(user(a.id.toString())).contentType("application/json").content(body)).andExpect(status().isForbidden());
  var b=account();mvc.perform(post(path).with(user(b.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
  String first=mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT")).andReturn().getResponse().getContentAsString();
  UUID booking=UUID.fromString(json.readTree(first).get("id").asText());
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(jsonPath("$.id").value(booking.toString()));
  for(String table:List.of("bookings","customers","calendar_allocations","payment_intents","assisted_bookings","booking_usage"))assertThat(owner().queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
  assertThat(owner().queryForObject("SELECT count(*) FROM mail_outbox WHERE booking_id=?",Long.class,booking)).isZero();assertThat(runtime.queryForObject("SELECT count(*) FROM assisted_bookings",Long.class)).isZero();
  mvc.perform(get("/api/v1/admin/customers/"+customer).with(user(a.id.toString()))).andExpect(jsonPath("$.customer.emailLocked").value(true)).andExpect(jsonPath("$.bookings[0].id").value(booking.toString()));
  mvc.perform(put("/api/v1/admin/customers/"+customer).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(customerBody("Cliente manual","changed@example.test",0))).andExpect(status().isConflict());
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(assistedBody(a,start,quote,UUID.randomUUID()))).andExpect(status().isConflict());
 }
 @Test void assistedBookingRejectsStaleQuotesAndRollsBackAuditFailure()throws Exception{
  var a=account();String start=publishAndFindStart(a);UUID customer=manualCustomer(a);String quote=assistedQuote(a,customer),path="/api/v1/admin/customers/"+customer+"/bookings";
  owner().update("UPDATE services SET price_cents=12000 WHERE tenant_id=?",a.tenant);
  mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(assistedBody(a,start,quote,UUID.randomUUID()))).andExpect(status().isConflict());
  String body=assistedBody(a,start,assistedQuote(a,customer),UUID.randomUUID());
  owner().execute("ALTER TABLE booking_events ADD CONSTRAINT test_reject_assisted CHECK(event_type<>'ASSISTED_REQUESTED') NOT VALID");
  try{mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
   for(String table:List.of("bookings","calendar_allocations","payment_intents","assisted_bookings","booking_usage","booking_requests"))assertThat(owner().queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id=?",Long.class,a.tenant)).isZero();
  }finally{owner().execute("ALTER TABLE booking_events DROP CONSTRAINT test_reject_assisted");}
 }
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17").withUsername("agendou_app").withPassword("agendou_app");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.flyway.url",postgres::getJdbcUrl);}
 @Autowired MockMvc mvc;@Autowired AuthService auth;@Autowired JdbcTemplate runtime;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @org.springframework.boot.test.mock.mockito.MockBean org.springframework.mail.javamail.JavaMailSender sender;
 @Autowired br.com.agendou.identity.MailDeliveryService delivery;
 @Autowired BookingService bookings;
 // Each scenario has its own synthetic users; do not share peer-rate history across tests.
 @org.junit.jupiter.api.BeforeEach void resetRateCounters(){owner().update("DELETE FROM auth_rate_limits");}
 @org.springframework.boot.test.mock.mockito.MockBean EvidenceStorage evidenceStorage;
 void moveAppointmentToPast(UUID id){owner().update("UPDATE bookings SET starts_at=starts_at-interval '3 days',ends_at=ends_at-interval '3 days' WHERE id=?",id);owner().update("UPDATE calendar_allocations SET starts_at=starts_at-interval '3 days',ends_at=ends_at-interval '3 days',protected_start=protected_start-interval '3 days',protected_end=protected_end-interval '3 days' WHERE id=(SELECT allocation_id FROM bookings WHERE id=?)",id);}
 String outcomeBody(String status,int version)throws Exception{return json.writeValueAsString(Map.of("status",status,"expectedVersion",version,"reason","Resultado conferido pelo profissional","confirmed",true));}
 @Test void outcomesRequireConfirmedElapsedAppointmentAndPreserveMoney()throws Exception{
  for(String outcome:List.of("COMPLETED","NO_SHOW")){
   var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/bookings/"+id+"/outcome";String body=outcomeBody(outcome,0);
   mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
   mvc.perform(post("/api/v1/admin/payment-reviews/"+id+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"outcome-receipt"))).andExpect(status().isOk());
   mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());moveAppointmentToPast(id);
   mvc.perform(post(path).with(user(a.id.toString())).contentType("application/json").content(body)).andExpect(status().isForbidden());var b=account();mvc.perform(post(path).with(user(b.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
   mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(outcomeBody(outcome,1))).andExpect(status().isConflict());
   for(int i=0;i<2;i++)mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(outcome));
   assertThat(owner().queryForObject("SELECT count(*) FROM booking_outcomes WHERE booking_id=?",Long.class,id)).isEqualTo(1);assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,id)).isEqualTo("COMMITTED");assertThat(owner().queryForObject("SELECT status FROM payment_intents WHERE booking_id=?",String.class,id)).isEqualTo("CONFIRMED");
   mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings/"+id+"/payment").cookie(c.cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(outcome)).andExpect(jsonPath("$.receivedCents").value(5000)).andExpect(jsonPath("$.pixKey").doesNotExist());
   mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings/"+id).cookie(c.cookie)).andExpect(jsonPath("$.outcome.status").value(outcome));
   assertThat(runtime.queryForObject("SELECT count(*) FROM booking_outcomes",Long.class)).isZero();
  }
 }
 @Test void concurrentOutcomesDoNotOverwriteEachOther()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());mvc.perform(post("/api/v1/admin/payment-reviews/"+id+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"outcome-race"))).andExpect(status().isOk());moveAppointmentToPast(id);
  var gate=new java.util.concurrent.CountDownLatch(1);try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){var tasks=new ArrayList<java.util.concurrent.Future<Integer>>();for(String status:List.of("COMPLETED","NO_SHOW")){String body=outcomeBody(status,0);tasks.add(executor.submit(()->{gate.await();return mvc.perform(post("/api/v1/admin/bookings/"+id+"/outcome").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus();}));}gate.countDown();assertThat(List.of(tasks.get(0).get(),tasks.get(1).get())).containsExactlyInAnyOrder(200,409);}
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_events WHERE booking_id=? AND event_type IN ('COMPLETED','NO_SHOW')",Long.class,id)).isEqualTo(1);
 }
 @Test void outcomeAuditFailureRollsBackAndBlockedTenantCannotClose()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());mvc.perform(post("/api/v1/admin/payment-reviews/"+id+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"outcome-rollback"))).andExpect(status().isOk());moveAppointmentToPast(id);String path="/api/v1/admin/bookings/"+id+"/outcome",body=outcomeBody("COMPLETED",0);
  owner().execute("ALTER TABLE booking_events ADD CONSTRAINT test_outcome_rollback CHECK(event_type<>'COMPLETED') NOT VALID");try{mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());}finally{owner().execute("ALTER TABLE booking_events DROP CONSTRAINT test_outcome_rollback");}
  assertThat(owner().queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("CONFIRMED");assertThat(owner().queryForObject("SELECT count(*) FROM booking_outcomes WHERE booking_id=?",Long.class,id)).isZero();
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
 }
 @Test void rescheduleCoordinatesConcurrentChangesAndMonthlyQuota()throws Exception{
  var a=account();String start=publishAndFindStart(a);var c=client(a,start);UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/bookings/"+id;
  var gate=new java.util.concurrent.CountDownLatch(1);try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){var tasks=new ArrayList<java.util.concurrent.Future<Integer>>();for(int minutes:List.of(15,30)){String body=json.writeValueAsString(Map.of("requestKey",UUID.randomUUID(),"expectedVersion",0,"start",java.time.Instant.parse(start).plusSeconds(minutes*60),"reason","Alteração concorrente de teste"));tasks.add(executor.submit(()->{gate.await();return mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus();}));}gate.countDown();assertThat(List.of(tasks.get(0).get(),tasks.get(1).get())).containsExactlyInAnyOrder(200,409);}
  var nextMonth=java.time.Instant.parse(start).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().withDayOfMonth(1).plusMonths(1);String target=nextMonth.atTime(9,0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant().toString();
  owner().update("UPDATE availability_settings SET schedule=?::jsonb WHERE tenant_id=?",json.writeValueAsString(Map.of("weekly",List.of(),"exceptions",List.of(Map.of("date",nextMonth.toString(),"reason","Teste","periods",List.of(Map.of("start","09:00","end","10:00")))))),a.tenant);
  owner().update("UPDATE services SET duration_minutes=60,price_cents=20000 WHERE tenant_id=?",a.tenant);
  mvc.perform(get(path+"/reschedule-options").with(user(a.id.toString())).param("date",nextMonth.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(3));
  String body=json.writeValueAsString(Map.of("requestKey",UUID.randomUUID(),"expectedVersion",1,"start",target,"reason","Alteração para o próximo mês"));
  owner().update("UPDATE plans SET monthly_booking_limit=0 WHERE code='PREMIUM_TOP'");try{mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());}finally{owner().update("UPDATE plans SET monthly_booking_limit=NULL WHERE code='PREMIUM_TOP'");}
  mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
  assertThat(owner().queryForObject("SELECT month FROM booking_usage WHERE booking_id=?",java.sql.Date.class,id).toLocalDate()).isEqualTo(nextMonth);assertThat(owner().queryForObject("SELECT price_cents FROM bookings WHERE id=?",Long.class,id)).isEqualTo(10000);
 }
 @Test void reschedulePreservesPaymentAndDeadlineAndRejectsStaleChanges()throws Exception{
  var a=account();String start=publishAndFindStart(a);var c=client(a,start);UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/bookings/"+id;
  var original=owner().queryForMap("SELECT expires_at,price_cents,pix_snapshot::text AS pix FROM bookings WHERE id=?",id);String next=java.time.Instant.parse(start).plusSeconds(1800).toString();String body=json.writeValueAsString(Map.of("requestKey",UUID.randomUUID(),"expectedVersion",0,"start",next,"reason","Cliente solicitou outro horário"));
  mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).contentType("application/json").content(body)).andExpect(status().isForbidden());var b=account();mvc.perform(post(path+"/reschedule").with(user(b.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
  for(int i=0;i<2;i++)mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.start").value(next));
  assertThat(owner().queryForMap("SELECT expires_at,price_cents,pix_snapshot::text AS pix FROM bookings WHERE id=?",id)).isEqualTo(original);assertThat(owner().queryForObject("SELECT count(*) FROM booking_reschedules WHERE booking_id=?",Long.class,id)).isEqualTo(1);
  String stale=json.writeValueAsString(Map.of("requestKey",UUID.randomUUID(),"expectedVersion",0,"start",start,"reason","Outro reagendamento solicitado"));mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(stale)).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings/"+id).cookie(c.cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.reschedules.length()").value(1));
  reserve(a,client(a,start),UUID.randomUUID()).andExpect(status().isCreated());assertThat(runtime.queryForObject("SELECT count(*) FROM booking_reschedules",Long.class)).isZero();
 }
 @Test void rescheduleConflictAndAuditFailureKeepOriginalSlot()throws Exception{
  var a=account();String start=publishAndFindStart(a);var c=client(a,start);UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String next=java.time.Instant.parse(start).plusSeconds(1800).toString();var other=client(a,next);reserve(a,other,UUID.randomUUID()).andExpect(status().isCreated());String path="/api/v1/admin/bookings/"+id;String body=json.writeValueAsString(Map.of("requestKey",UUID.randomUUID(),"expectedVersion",0,"start",next,"reason","Troca solicitada pelo cliente"));
  mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE tenant_id=? AND id<>?",a.tenant,id);
  owner().execute("ALTER TABLE booking_events ADD CONSTRAINT test_reschedule_rollback CHECK(event_type<>'RESCHEDULED') NOT VALID");try{mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());}finally{owner().execute("ALTER TABLE booking_events DROP CONSTRAINT test_reschedule_rollback");}
  assertThat(owner().queryForObject("SELECT starts_at FROM bookings WHERE id=?",java.sql.Timestamp.class,id).toInstant()).isEqualTo(java.time.Instant.parse(start));assertThat(owner().queryForObject("SELECT count(*) FROM booking_reschedules WHERE booking_id=?",Long.class,id)).isZero();
  mvc.perform(post("/api/v1/admin/payment-reviews/"+id+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"reschedule-paid"))).andExpect(status().isOk());
  mvc.perform(post(path+"/reschedule").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));assertThat(owner().queryForObject("SELECT amount_cents FROM payment_transactions WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(5000);
 }
 @Test void cancellationReleasesSlotPreservesAuditAndAllowsOnlyPlannedRefund()throws Exception{
  var a=account();String start=publishAndFindStart(a);var c=client(a,start);UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/payment-reviews/"+id;
  var credit=mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"cancel-credit"))).andExpect(status().isOk()).andReturn();String transaction=json.readTree(credit.getResponse().getContentAsString()).get("transactions").get(0).get("id").asText();
  String body=json.writeValueAsString(Map.of("reason","Cliente solicitou cancelamento","expectedNetCents",5000,"refundCents",4000,"policyReviewed",true));
  mvc.perform(post(path+"/cancel").with(user(a.id.toString())).contentType("application/json").content(body)).andExpect(status().isForbidden());var b=account();mvc.perform(post(path+"/cancel").with(user(b.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
  for(int i=0;i<2;i++)mvc.perform(post(path+"/cancel").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED")).andExpect(jsonPath("$.refundDueCents").value(4000)).andExpect(jsonPath("$.cancellation.retainedCents").value(1000));
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_events WHERE booking_id=? AND event_type='CANCELED'",Long.class,id)).isEqualTo(1);assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,id)).isEqualTo("COMMITTED");
  mvc.perform(post(path+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(transaction,5000,"over-cancel"))).andExpect(status().isConflict());
  mvc.perform(post(path+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(transaction,4000,"cancel-refund"))).andExpect(status().isOk()).andExpect(jsonPath("$.financialStatus").value("CANCELED_SETTLED"));
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 day' WHERE id=?",id);bookings.expireTenant(a.tenant);
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings/"+id+"/payment").cookie(c.cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED")).andExpect(jsonPath("$.pixKey").doesNotExist()).andExpect(jsonPath("$.cancellation.reason").value("Cliente solicitou cancelamento"));
  reserve(a,client(a,start),UUID.randomUUID()).andExpect(status().isCreated());
  mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(1000,"credit-after-cancel"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED")).andExpect(jsonPath("$.refundDueCents").value(1000));
  assertThat(runtime.queryForObject("SELECT count(*) FROM booking_cancellations",Long.class)).isZero();
 }
 @Test void cancellationRejectsStaleDecisionAndRollsBackAllChanges()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/payment-reviews/"+id;
  String body=json.writeValueAsString(Map.of("reason","Cancelamento solicitado pelo cliente","expectedNetCents",0,"refundCents",0,"policyReviewed",true));
  owner().execute("ALTER TABLE booking_events ADD CONSTRAINT test_cancel_rollback CHECK(event_type<>'CANCELED') NOT VALID");try{mvc.perform(post(path+"/cancel").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());}finally{owner().execute("ALTER TABLE booking_events DROP CONSTRAINT test_cancel_rollback");}
  assertThat(owner().queryForObject("SELECT count(*) FROM booking_cancellations WHERE booking_id=?",Long.class,id)).isZero();assertThat(owner().queryForObject("SELECT active FROM calendar_allocations WHERE id=(SELECT allocation_id FROM bookings WHERE id=?)",Boolean.class,id)).isTrue();
  mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(1000,"stale-cancel"))).andExpect(status().isOk());
  mvc.perform(post(path+"/cancel").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
  body=json.writeValueAsString(Map.of("reason","Cancelamento solicitado pelo cliente","expectedNetCents",1000,"refundCents",1000,"policyReviewed",true));
  mvc.perform(post(path+"/cancel").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,id)).isEqualTo("RELEASED");
 }
 @Test void cancellationRejectsExpiredAndBlockedReservations()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/payment-reviews/"+id+"/cancel";String body=json.writeValueAsString(Map.of("reason","Cancelar reserva de teste","expectedNetCents",0,"refundCents",0,"policyReviewed",true));
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE id=?",id);mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);mvc.perform(post(path).with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
 }
 @Test void manualReceiptConfirmsOnlyWithEnoughFundsAndIsIdempotent()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));var made=reserve(a,c,UUID.randomUUID()).andReturn();UUID id=UUID.fromString(json.readTree(made.getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/payment-reviews/"+id;
  mvc.perform(get(path)).andExpect(status().isUnauthorized());var b=account();mvc.perform(get(path).with(user(b.id.toString()))).andExpect(status().isNotFound());
  String time=java.time.Instant.now().toString();String partial=json.writeValueAsString(Map.of("amountCents",2000,"bankReference"," bank-1 ","receivingAccount","Conta teste","receivedAt",time,"verified",true));
  mvc.perform(post(path+"/receipts").with(user(a.id.toString())).contentType("application/json").content(partial)).andExpect(status().isForbidden());
  mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(partial)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT")).andExpect(jsonPath("$.receivedCents").value(2000));
  String rest=json.writeValueAsString(Map.of("amountCents",3000,"bankReference","bank-2","receivingAccount","Conta teste","receivedAt",java.time.Instant.now().toString(),"verified",true));
  for(int i=0;i<2;i++)mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(rest)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.receivedCents").value(5000));
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_transactions WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(2);
  assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,id)).isEqualTo("COMMITTED");
  assertThat(owner().queryForObject("SELECT kind FROM calendar_allocations WHERE id=(SELECT allocation_id FROM bookings WHERE id=?)",String.class,id)).isEqualTo("BOOKING");
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 day' WHERE id=?",id);bookings.expireTenant(a.tenant);
  mvc.perform(get("/api/v1/public/"+a.slug+"/client/bookings/"+id+"/payment").cookie(c.cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.receivedCents").value(5000)).andExpect(jsonPath("$.pixKey").doesNotExist());
  mvc.perform(post("/api/v1/public/"+a.slug+"/client/bookings/"+id+"/evidence").cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(EvidenceValidatorTest.png(3))).andExpect(status().isConflict());
  assertThat(runtime.queryForObject("SELECT count(*) FROM payment_transactions",Long.class)).isZero();
 }
 @Test void excessRefundAndLatePaymentDoNotReopenExpiredBooking()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/payment-reviews/"+id;
  String input=receiptBody(12000," bank-excess ");var b=account();mvc.perform(post(path+"/receipts").with(user(b.id.toString())).with(csrf()).contentType("application/json").content(input)).andExpect(status().isNotFound());
  var made=mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.financialStatus").value("EXCESS")).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT")).andReturn();String transaction=json.readTree(made.getResponse().getContentAsString()).get("transactions").get(0).get("id").asText();
  mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(1000,"BANK-EXCESS"))).andExpect(status().isConflict());
  String refund=refundBody(transaction,2000,"refund-excess");
  mvc.perform(post(path+"/refunds").with(user(a.id.toString())).contentType("application/json").content(refund)).andExpect(status().isForbidden());mvc.perform(post(path+"/refunds").with(user(b.id.toString())).with(csrf()).contentType("application/json").content(refund)).andExpect(status().isNotFound());
  for(int i=0;i<2;i++)mvc.perform(post(path+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refund)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.receivedCents").value(10000));
  mvc.perform(post(path+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(transaction,10001,"refund-over"))).andExpect(status().isConflict());
  mvc.perform(post(path+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(transaction,6000,"refund-deposit"))).andExpect(status().isConflict());
  var other=client(a,owner().queryForObject("SELECT starts_at FROM bookings WHERE id=?",java.sql.Timestamp.class,id).toInstant().plusSeconds(1800).toString());UUID late=UUID.fromString(json.readTree(reserve(a,other,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String latePath="/api/v1/admin/payment-reviews/"+late;
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE id=?",late);
  var result=mvc.perform(post(latePath+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"late-ref"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.financialStatus").value("LATE")).andReturn();String lateTx=json.readTree(result.getResponse().getContentAsString()).get("transactions").get(0).get("id").asText();
  mvc.perform(post(latePath+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(lateTx,5000,"late-refund"))).andExpect(status().isOk()).andExpect(jsonPath("$.financialStatus").value("REFUNDED")).andExpect(jsonPath("$.status").value("EXPIRED"));
  assertThat(owner().queryForObject("SELECT active FROM calendar_allocations WHERE id=(SELECT allocation_id FROM bookings WHERE id=?)",Boolean.class,late)).isFalse();assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,late)).isEqualTo("RELEASED");
 }
 String receiptBody(long cents,String reference)throws Exception{return json.writeValueAsString(Map.of("amountCents",cents,"bankReference",reference,"receivingAccount","Conta teste","receivedAt",java.time.Instant.now().toString(),"verified",true));}
 String refundBody(String transaction,long cents,String reference)throws Exception{return json.writeValueAsString(Map.of("transactionId",transaction,"amountCents",cents,"bankReference",reference,"reason","Devolução conferida no banco","refundedAt",java.time.Instant.now().toString(),"verified",true));}
 @Test void concurrentRefundsCannotExceedReceiptAndBlockedTenantCannotWrite()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());String path="/api/v1/admin/payment-reviews/"+id;
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE id=?",id);
  var result=mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(receiptBody(5000,"concurrent"))).andExpect(status().isOk()).andReturn();String transaction=json.readTree(result.getResponse().getContentAsString()).get("transactions").get(0).get("id").asText();
  var gate=new java.util.concurrent.CountDownLatch(1);try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var tasks=new ArrayList<java.util.concurrent.Future<Integer>>();for(int i=0;i<2;i++){String body=refundBody(transaction,4000,"parallel-"+i);tasks.add(executor.submit(()->{gate.await();return mvc.perform(post(path+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus();}));}gate.countDown();assertThat(List.of(tasks.get(0).get(),tasks.get(1).get())).containsExactlyInAnyOrder(200,409);
  }
  assertThat(owner().queryForObject("SELECT sum(amount_cents) FROM payment_refunds WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(4000);
  owner().update("UPDATE subscriptions SET trial_started_at=now()-interval '8 days',trial_ends_at=now()-interval '1 day' WHERE tenant_id=?",a.tenant);
  mvc.perform(get(path).with(user(a.id.toString()))).andExpect(status().isOk());
  mvc.perform(post(path+"/refunds").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(refundBody(transaction,1000,"blocked"))).andExpect(status().isForbidden());
 }
 @Test void reviewDownloadIsPrivateAndConfirmationRollsBackTogether()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));UUID id=UUID.fromString(json.readTree(reserve(a,c,UUID.randomUUID()).andReturn().getResponse().getContentAsString()).get("id").asText());
  var upload=mvc.perform(post("/api/v1/public/"+a.slug+"/client/bookings/"+id+"/evidence").cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(EvidenceValidatorTest.png(1))).andExpect(status().isCreated()).andReturn();String evidence=json.readTree(upload.getResponse().getContentAsString()).get("id").asText();
  org.mockito.Mockito.when(evidenceStorage.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(EvidenceValidatorTest.png(1));String path="/api/v1/admin/payment-reviews/"+id;
  mvc.perform(get(path+"/evidence/"+evidence).with(user(a.id.toString()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));var b=account();mvc.perform(get(path+"/evidence/"+evidence).with(user(b.id.toString()))).andExpect(status().isNotFound());
  String body=json.writeValueAsString(Map.of("amountCents",5000,"bankReference","rollback-ref","receivingAccount","Conta teste","receivedAt",java.time.Instant.now().toString(),"verified",true));
  owner().execute("ALTER TABLE booking_events ADD CONSTRAINT test_confirmation_rollback CHECK(event_type<>'CONFIRMED') NOT VALID");try{mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());}finally{owner().execute("ALTER TABLE booking_events DROP CONSTRAINT test_confirmation_rollback");}
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_transactions WHERE tenant_id=?",Long.class,a.tenant)).isZero();assertThat(owner().queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("IN_REVIEW");assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,id)).isEqualTo("HELD");
  mvc.perform(post(path+"/receipts").with(user(a.id.toString())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
 }
 @Test void evidenceIsPrivateIdempotentAndReviewHasFixedDeadline()throws Exception{
  var a=account();var start=publishAndFindStart(a);var c=client(a,start);var made=reserve(a,c,UUID.randomUUID()).andReturn();UUID booking=UUID.fromString(json.readTree(made.getResponse().getContentAsString()).get("id").asText());String path="/api/v1/public/"+a.slug+"/client/bookings/"+booking+"/evidence";
  byte[] file=EvidenceValidatorTest.png(1);var stored=new java.util.concurrent.ConcurrentHashMap<String,byte[]>();
  org.mockito.Mockito.doAnswer(call->{stored.put(call.getArgument(0),call.getArgument(1));return null;}).when(evidenceStorage).put(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
  org.mockito.Mockito.when(evidenceStorage.get(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call->stored.get(call.getArgument(0)));
  mvc.perform(post(path).cookie(c.cookie).contentType("application/octet-stream").content(file)).andExpect(status().isForbidden());
  var result=mvc.perform(post(path).cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(file)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("IN_REVIEW")).andReturn();var first=json.readTree(result.getResponse().getContentAsString());
  mvc.perform(post(path).cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(file)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(first.get("id").asText()));
  mvc.perform(post(path).cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(EvidenceValidatorTest.png(2))).andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").value(first.get("expiresAt").asText()));
  assertThat(stored).hasSize(2);assertThat(owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,booking)).isEqualTo("HELD");
  mvc.perform(get(path+"/"+first.get("id").asText()).cookie(c.cookie)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
  var other=client(a,java.time.Instant.parse(start).plusSeconds(1800).toString());reserve(a,other,UUID.randomUUID()).andExpect(status().isCreated());
  mvc.perform(get(path+"/"+first.get("id").asText()).cookie(other.cookie)).andExpect(status().isNotFound());
  mvc.perform(post(path).cookie(other.cookie).with(csrf()).contentType("application/octet-stream").content(file)).andExpect(status().isNotFound());
  owner().update("UPDATE bookings SET expires_at=now()-interval '1 minute' WHERE id=?",booking);bookings.expireTenant(a.tenant);
  assertThat(owner().queryForObject("SELECT status FROM bookings WHERE id=?",String.class,booking)).isEqualTo("IN_REVIEW");
  owner().update("UPDATE bookings SET review_expires_at=now()-interval '1 minute' WHERE id=?",booking);bookings.expireTenant(a.tenant);
  assertThat(owner().queryForObject("SELECT status FROM payment_intents WHERE booking_id=?",String.class,booking)).isEqualTo("EXPIRED");
  mvc.perform(post(path).cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(EvidenceValidatorTest.png(3))).andExpect(status().isConflict());
 }
 @Test void failedEvidencePersistenceRemovesObjectAndRollsBackReview()throws Exception{
  var a=account();var c=client(a,publishAndFindStart(a));var made=reserve(a,c,UUID.randomUUID()).andReturn();String booking=json.readTree(made.getResponse().getContentAsString()).get("id").asText();String path="/api/v1/public/"+a.slug+"/client/bookings/"+booking+"/evidence";
  mvc.perform(post(path).cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content("not an image".getBytes())).andExpect(status().isUnprocessableEntity());
  org.mockito.Mockito.verifyNoInteractions(evidenceStorage);
  org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Armazenamento indisponível.")).when(evidenceStorage).put(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
  mvc.perform(post(path).cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(EvidenceValidatorTest.png(1))).andExpect(status().isServiceUnavailable());
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_evidence WHERE tenant_id=?",Long.class,a.tenant)).isZero();
  org.mockito.Mockito.reset(evidenceStorage);
  owner().execute("ALTER TABLE booking_events ADD CONSTRAINT test_reject_review CHECK(event_type<>'IN_REVIEW') NOT VALID");
  try{mvc.perform(post(path).cookie(c.cookie).with(csrf()).contentType("application/octet-stream").content(EvidenceValidatorTest.png(1))).andExpect(status().isConflict());}finally{owner().execute("ALTER TABLE booking_events DROP CONSTRAINT test_reject_review");}
  org.mockito.Mockito.verify(evidenceStorage).remove(org.mockito.ArgumentMatchers.anyString());
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_evidence WHERE tenant_id=?",Long.class,a.tenant)).isZero();
  assertThat(owner().queryForObject("SELECT status FROM bookings WHERE id=?",String.class,UUID.fromString(booking))).isEqualTo("AWAITING_PAYMENT");
 }
 @Test void manualPixUsesBookingSnapshotAndExpiresWithoutExposingKey()throws Exception{
  var a=account();var start=publishAndFindStart(a);var c=client(a,start);var key=UUID.randomUUID();
  var response=reserve(a,c,key).andExpect(status().isCreated()).andReturn();String id=json.readTree(response.getResponse().getContentAsString()).get("id").asText();
  reserve(a,c,key).andExpect(status().isCreated());
  assertThat(owner().queryForObject("SELECT count(*) FROM payment_intents WHERE tenant_id=?",Long.class,a.tenant)).isEqualTo(1);
  owner().update("UPDATE payment_settings_versions SET pix_key='changed@example.test',recipient_name='Alterado' WHERE tenant_id=?",a.tenant);
  owner().update("UPDATE services SET price_cents=20000 WHERE tenant_id=?",a.tenant);
  String path="/api/v1/public/"+a.slug+"/client/bookings/"+id+"/payment";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).cookie(c.cookie)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.pixKey").value("private@example.test")).andExpect(jsonPath("$.recipientName").value("Recebedor teste")).andExpect(jsonPath("$.totalCents").value(10000)).andExpect(jsonPath("$.depositCents").value(5000)).andExpect(jsonPath("$.remainingAfterDepositCents").value(5000)).andExpect(jsonPath("$.paymentAvailable").value(true));
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
