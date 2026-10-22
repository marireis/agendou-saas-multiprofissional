package br.com.agendou.catalog;

import static br.com.agendou.catalog.BackupRestoreIT.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.*;
import org.testcontainers.utility.MountableFile;

class BookingRecoveryIT {
 @TempDir Path backup;
 record FileEntry(String key,String type,String digest,long size,String localFile){}

 @Test void restoredApiAuthenticatesCodeAndReadsLinkedProofThenConfirmsPayment() throws Exception {
  try(var source=postgres();var destination=postgres();var sourceFiles=minio();var destinationFiles=minio()) {
   source.start();sourceFiles.start();
   RecoveryJourneyFixture.Account account,other;
   String bookingId,code,evidenceId;
   byte[] image=EvidenceValidatorTest.png(2);
   List<FileEntry> manifest=new ArrayList<>();
   // Close the entire source application before taking the database/object snapshot.
   try(var app=start(source,sourceFiles)) {
    var fixture=new RecoveryJourneyFixture(app,source);
    account=fixture.account();other=fixture.account();
    String start=fixture.publishAndFindStart(account);
    var session=fixture.phoneSession(account,start,"11999990000");
    var booking=fixture.phoneReserve(account,session,UUID.randomUUID());
    bookingId=booking.get("id").asText();code=booking.get("accessCode").asText();
    var client=client();String base=base(app),portal="/api/v1/public/"+account.slug()+"/client";
    assertThat(postJson(client,base,portal+"/code",fixture.json.writeValueAsString(Map.of("firstName","Maria","lastName","Silva","phone","11999990000","code",code))).statusCode()).isEqualTo(200);
    var csrf=csrf(client,base);
    var upload=client.send(HttpRequest.newBuilder(URI.create(base+portal+"/bookings/"+bookingId+"/evidence"))
      .header(csrf.get("headerName").asText(),csrf.get("token").asText()).header("Content-Type","application/octet-stream")
      .POST(HttpRequest.BodyPublishers.ofByteArray(image)).build(),HttpResponse.BodyHandlers.ofString());
    assertThat(upload.statusCode()).as(upload.body()).isEqualTo(201);
    evidenceId=fixture.json.readTree(upload.body()).get("id").asText();
    manifest=fixture.owner().query("SELECT object_key,media_type,sha256,size_bytes FROM payment_evidence ORDER BY id",(row,n)->new FileEntry(row.getString(1),row.getString(2),row.getString(3),row.getLong(4),"object-"+n));
    assertThat(manifest).hasSize(1);
    assertThat(fixture.owner().queryForObject("SELECT count(*) FROM payment_evidence e JOIN payment_intents i ON (i.tenant_id,i.id)=(e.tenant_id,e.intent_id) JOIN bookings b ON (b.tenant_id,b.id)=(i.tenant_id,i.booking_id) WHERE b.id=?",Long.class,UUID.fromString(bookingId))).isEqualTo(1);
   }
   long backupStarted=System.nanoTime();
   checked(source,"pg_dump","-U",source.getUsername(),"-d",source.getDatabaseName(),"-Fc","-f","/tmp/booking.dump");
   Path dump=backup.resolve("booking.dump");source.copyFileFromContainer("/tmp/booking.dump",dump.toString());
   String dumpHash=sha256(Files.readAllBytes(dump));
   for(var entry:manifest){byte[] bytes=storage(sourceFiles).get(entry.key());verify(entry,bytes);Files.write(backup.resolve(entry.localFile()),bytes);}
   var json=new com.fasterxml.jackson.databind.ObjectMapper();
   Path manifestPath=backup.resolve("manifest.json");json.writeValue(manifestPath.toFile(),manifest);
   long backupMs=elapsed(backupStarted);
   // Recovery reads the saved inventory, not a list of hard-coded object keys.
   manifest=Arrays.asList(json.readValue(manifestPath.toFile(),FileEntry[].class));
   long recoveryStarted=System.nanoTime();destination.start();destinationFiles.start();
   execute(destination,"CREATE ROLE agendou_runtime LOGIN PASSWORD 'agendou_runtime_local_only' NOSUPERUSER NOBYPASSRLS");
   assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(dumpHash);
   destination.copyFileToContainer(MountableFile.forHostPath(dump),"/tmp/booking.dump");
   checked(destination,"pg_restore","-U",destination.getUsername(),"-d",destination.getDatabaseName(),"--exit-on-error","/tmp/booking.dump");
   for(var entry:manifest){
    // Missing or corrupt bytes cannot silently pass the acceptance gate.
    assertThatThrownBy(()->storage(destinationFiles).get(entry.key())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    byte[] bytes=Files.readAllBytes(backup.resolve(entry.localFile()));verify(entry,bytes);
    byte[] corrupt=bytes.clone();corrupt[0]^=1;
    assertThatThrownBy(()->verify(entry,corrupt)).isInstanceOf(AssertionError.class);
    storage(destinationFiles).put(entry.key(),bytes,entry.type());verify(entry,storage(destinationFiles).get(entry.key()));
   }
   try(var app=start(destination,destinationFiles)) {
    var fixture=new RecoveryJourneyFixture(app,destination);
    String base=base(app),portal="/api/v1/public/"+account.slug()+"/client",payment="/api/v1/admin/payment-reviews/"+bookingId;
    var client=client();
    assertThat(get(client,base,"/api/v1/health").statusCode()).isEqualTo(200);
    assertThat(postJson(client,base,portal+"/code",json.writeValueAsString(Map.of("firstName","Maria","lastName","Silva","phone","11999990000","code","A".repeat(43)))).statusCode()).isEqualTo(400);
    assertThat(postJson(client,base,portal+"/code",json.writeValueAsString(Map.of("firstName","Maria","lastName","Silva","phone","11999990000","code",code))).statusCode()).isEqualTo(200);
    var detail=get(client,base,portal+"/bookings/"+bookingId);assertThat(detail.statusCode()).isEqualTo(200);
    assertThat(json.readTree(detail.body()).get("status").asText()).isEqualTo("IN_REVIEW");
    var download=client.send(HttpRequest.newBuilder(URI.create(base+portal+"/bookings/"+bookingId+"/evidence/"+evidenceId)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    assertThat(download.statusCode()).isEqualTo(200);assertThat(download.body()).isEqualTo(image);
    assertThat(download.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(get(client(),base,portal+"/bookings/"+bookingId+"/evidence/"+evidenceId).statusCode()).isNotEqualTo(200);
    var entry=manifest.getFirst();
    assertThat(get(client(),endpoint(destinationFiles),"/recovery-private/"+entry.key()).statusCode()).isIn(401,403);
    fixture.mvc.perform(get(payment).with(user(other.id().toString()))).andExpect(status().isNotFound());
    fixture.mvc.perform(post(payment+"/receipts").with(user(account.id().toString())).with(csrf()).contentType("application/json").content(fixture.receiptBody(5000,"recovery-receipt")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    var paid=get(client,base,portal+"/bookings/"+bookingId+"/payment");assertThat(paid.statusCode()).isEqualTo(200);
    assertThat(json.readTree(paid.body()).get("receivedCents").asLong()).isEqualTo(5000);
    assertThat(fixture.owner().queryForObject("SELECT state FROM booking_usage WHERE booking_id=?",String.class,UUID.fromString(bookingId))).isEqualTo("COMMITTED");
    Files.writeString(Path.of("target/booking-recovery-report.txt"),"PASS at "+java.time.Instant.now()+"\nBackup ms: "+backupMs+"\nRestore, application startup and journey ms: "+elapsed(recoveryStarted)
      +"\nSynthetic tenants: 2; bookings: 1; linked evidence objects: 1\nHTTP: code authentication, invalid code rejection, private download, payment read. MockMvc: other tenant denied, manual receipt confirms restored booking.\nNo production RPO/RTO, browser, external delivery or MFA key recovery claimed.\n");
   }
  }
 }
 private static void verify(FileEntry entry,byte[] bytes)throws Exception{assertThat(bytes.length).isEqualTo(entry.size());assertThat(sha256(bytes)).isEqualTo(entry.digest());}
 private static HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
 private static String base(ServletWebServerApplicationContext app){return "http://localhost:"+app.getWebServer().getPort();}
 private static HttpResponse<String> get(HttpClient client,String base,String path)throws Exception{return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
 private static com.fasterxml.jackson.databind.JsonNode csrf(HttpClient client,String base)throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(get(client,base,"/api/v1/auth/csrf").body());}
 private static HttpResponse<String> postJson(HttpClient client,String base,String path,String body)throws Exception{
  var csrf=csrf(client,base);return client.send(HttpRequest.newBuilder(URI.create(base+path)).header(csrf.get("headerName").asText(),csrf.get("token").asText()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 private static ServletWebServerApplicationContext start(PostgreSQLContainer<?> db,GenericContainer<?> files){
  return (ServletWebServerApplicationContext)new SpringApplicationBuilder(br.com.agendou.ApiApplication.class).run(
   "--server.port=0","--server.servlet.session.cookie.secure=false","--spring.datasource.url="+db.getJdbcUrl(),"--spring.datasource.username=agendou_runtime","--spring.datasource.password=agendou_runtime_local_only",
   "--spring.flyway.url="+db.getJdbcUrl(),"--spring.flyway.user="+db.getUsername(),"--spring.flyway.password="+db.getPassword(),
   "--agendou.storage.endpoint="+endpoint(files),"--agendou.storage.access-key=testaccess","--agendou.storage.secret-key=testsecret123","--agendou.storage.bucket=recovery-private",
   "--agendou.mail-worker-initial-delay=3600000","--agendou.trial-worker-delay=3600000","--agendou.booking-expiration-delay=3600000",
   "--spring.mail.host=127.0.0.1","--spring.mail.port=1");
 }
}
