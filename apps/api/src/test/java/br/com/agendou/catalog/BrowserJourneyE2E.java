package br.com.agendou.catalog;
import static br.com.agendou.catalog.BackupRestoreIT.*;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Explicitly selected by -Dit.test=BrowserJourneyE2E; excluded from normal backend regression. */
class BrowserJourneyE2E {
 @Test void browserJourneysOnIsolatedServices() throws Exception {
  try(var db=postgres();var files=minio()) {
   db.start();files.start();
   try(var app=BookingRecoveryIT.start(db,files)) {
    var fixture=new RecoveryJourneyFixture(app,db);
    Map<String,Object> data=new LinkedHashMap<>();
    for(String project:List.of("desktop","mobile")) {
     var account=fixture.account();String start=fixture.publishAndFindStart(account);
     fixture.owner().update("UPDATE app_users SET verified=true WHERE id=?",account.id());
     String email=fixture.owner().queryForObject("SELECT email FROM app_users WHERE id=?",String.class,account.id());
     data.put(project,Map.of("email",email,"password","synthetic-password-123","slug",account.slug(),"start",start,"date",start.substring(0,10)));
    }
    Path target=Path.of("target").toAbsolutePath();Path fixtureFile=target.resolve("browser-fixture.json");
    fixture.json.writeValue(fixtureFile.toFile(),data);
    Files.write(target.resolve("browser-proof.png"),EvidenceValidatorTest.png(2));
    int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
    Path web=Path.of("../web").toAbsolutePath().normalize();
    var builder=new ProcessBuilder("C:\\Program Files\\nodejs\\node.exe","node_modules/@playwright/test/cli.js","test").directory(web.toFile()).redirectErrorStream(true).redirectOutput(target.resolve("browser-e2e.log").toFile());
    builder.environment().put("AGENDOU_E2E_DATA",fixtureFile.toString());
    builder.environment().put("AGENDOU_E2E_PORT",Integer.toString(port));
    builder.environment().put("AGENDOU_API_URL","http://localhost:"+app.getWebServer().getPort());
    builder.environment().put("AGENDOU_E2E_PROOF",target.resolve("browser-proof.png").toString());
    var process=builder.start();
    try {
     assertThat(process.waitFor(20,TimeUnit.MINUTES)).as("Browser run timeout; see target/browser-e2e.log").isTrue();
     assertThat(process.exitValue()).as("Browser tests; see target/browser-e2e.log and ../web/playwright-report").isZero();
     assertThat(fixture.owner().queryForObject("SELECT count(*) FROM bookings WHERE status='CANCELED'",Long.class)).isEqualTo(2);
     assertThat(fixture.owner().queryForObject("SELECT count(*) FROM payment_evidence",Long.class)).isEqualTo(2);
     assertThat(fixture.owner().queryForObject("SELECT count(*) FROM payment_transactions",Long.class)).isEqualTo(2);
     assertThat(fixture.owner().queryForObject("SELECT count(*) FROM payment_refunds",Long.class)).isEqualTo(2);
    } finally {
     process.descendants().forEach(child->child.destroy());process.destroy();
    }
   }
  }
 }
}
