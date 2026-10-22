package br.com.agendou.catalog;
import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
@Testcontainers
class S3EvidenceStorageIT {
 @Container static GenericContainer<?> minio=new GenericContainer<>("agendou-minio-local:7aac2a2").withEnv("MINIO_ROOT_USER","testaccess").withEnv("MINIO_ROOT_PASSWORD","testsecret123").withCommand("server /data").withExposedPorts(9000).waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/minio/health/ready").forPort(9000));
 @Test void privateObjectRoundTripAndAnonymousAccessDenied()throws Exception{
  String endpoint="http://"+minio.getHost()+":"+minio.getMappedPort(9000);
  var storage=new S3EvidenceStorage(endpoint,"testaccess","testsecret123","evidence-private-test");
  var bytes=EvidenceValidatorTest.png(1);storage.put("tenant/booking/file",bytes,"image/png");assertThat(storage.get("tenant/booking/file")).isEqualTo(bytes);
  var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(endpoint+"/evidence-private-test/tenant/booking/file")).GET().build(),HttpResponse.BodyHandlers.discarding());assertThat(response.statusCode()).isIn(401,403);
  storage.remove("tenant/booking/file");assertThatThrownBy(()->storage.get("tenant/booking/file")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
}
