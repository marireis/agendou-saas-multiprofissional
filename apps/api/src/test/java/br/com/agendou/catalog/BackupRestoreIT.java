package br.com.agendou.catalog;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.net.URI;
import java.net.http.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/** Synthetic recovery rehearsal. Never connects to the application's local database. */
class BackupRestoreIT {
    @TempDir Path backup;
    private static final String TENANT = "00000000-0000-0000-0000-000000000001";
    private static final String BUCKET = "recovery-private";

    @Test void restoresSchemaDataPermissionsAndPrivateObjects() throws Exception {
        try (var source = postgres(); var destination = postgres();
             var sourceFiles = minio(); var destinationFiles = minio()) {
            source.start(); sourceFiles.start();
            Flyway.configure().dataSource(source.getJdbcUrl(), source.getUsername(), source.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            execute(source, "INSERT INTO tenants(id,slug,display_name,status) VALUES ('" + TENANT + "','recovery-one','Synthetic One','ACTIVE'),"
                    + "('00000000-0000-0000-0000-000000000002','recovery-two','Synthetic Two','ACTIVE')");
            execute(source, "INSERT INTO customers(id,tenant_id,name,phone,contact_name_key) VALUES "
                    + "('00000000-0000-0000-0000-000000000003','" + TENANT + "','Synthetic Client','11999990000','synthetic client')");
            var original = storage(sourceFiles);
            String key = TENANT + "/synthetic-proof.png";
            byte[] bytes = EvidenceValidatorTest.png(1);
            original.put(key, bytes, "image/png");
            Instant recoveryPoint = Instant.now();
            long backupStart = System.nanoTime();
            checked(source, "pg_dump", "-U", source.getUsername(), "-d", source.getDatabaseName(), "-Fc", "-f", "/tmp/recovery.dump");
            Path dump = backup.resolve("database.dump");
            source.copyFileFromContainer("/tmp/recovery.dump", dump.toString());
            Path object = backup.resolve("proof.png");
            Files.write(object, original.get(key));
            String digest = sha256(Files.readAllBytes(object));
            String dumpDigest = sha256(Files.readAllBytes(dump));
            Files.writeString(backup.resolve("manifest.txt"), key + "\timage/png\t" + bytes.length + "\t" + digest + "\n");
            long backupMs = elapsed(backupStart);
            // One write after the backup boundary demonstrates that a snapshot is not continuous recovery.
            execute(source, "INSERT INTO tenants(id,slug,display_name,status) VALUES "
                    + "('00000000-0000-0000-0000-000000000004','after-backup','After backup','ACTIVE')");
            Instant incident = Instant.now();
            long restoreStart = System.nanoTime();
            destination.start(); destinationFiles.start();
            // pg_dump does not contain global roles. Preserve ownership/ACL; never use --no-owner.
            execute(destination, "CREATE ROLE agendou_runtime LOGIN PASSWORD 'agendou_runtime_local_only' NOSUPERUSER NOBYPASSRLS");
            assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(dumpDigest);
            destination.copyFileToContainer(MountableFile.forHostPath(dump), "/tmp/recovery.dump");
            checked(destination, "pg_restore", "-U", destination.getUsername(), "-d", destination.getDatabaseName(), "--exit-on-error", "/tmp/recovery.dump");
            var restored = storage(destinationFiles);
            assertThat(sha256(Files.readAllBytes(object))).isEqualTo(digest);
            restored.put(key, Files.readAllBytes(object), "image/png");
            assertThat(sha256(restored.get(key))).isEqualTo(digest);
            assertThat(count(destination, "SELECT count(*) FROM tenants")).isEqualTo(2);
            assertThat(count(source, "SELECT count(*) FROM tenants")).isEqualTo(3);
            assertThat(count(destination, "SELECT count(*) FROM customers")).isEqualTo(1);
            for (String query : new String[] {
                    "SELECT count(*) FROM flyway_schema_history WHERE success",
                    "SELECT count(*) FROM pg_policies WHERE schemaname='public'",
                    "SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relrowsecurity AND relforcerowsecurity",
                    "SELECT count(*) FROM pg_proc WHERE pronamespace='public'::regnamespace AND prosecdef"}) {
                assertThat(count(destination, query)).isEqualTo(count(source, query)).isPositive();
            }
            Flyway.configure().dataSource(destination.getJdbcUrl(), destination.getUsername(), destination.getPassword())
                    .locations("classpath:db/migration").load().validate();
            try (var connection = DriverManager.getConnection(destination.getJdbcUrl(), "agendou_runtime", "agendou_runtime_local_only");
                 var statement = connection.createStatement()) {
                assertThat(scalar(statement, "SELECT count(*) FROM tenants")).isZero();
                statement.execute("SET app.tenant_id='" + TENANT + "'");
                assertThat(scalar(statement, "SELECT count(*) FROM tenants")).isEqualTo(1);
                assertThat(scalar(statement, "SELECT count(*) FROM customers")).isEqualTo(1);
                statement.execute("SET app.tenant_id='00000000-0000-0000-0000-000000000002'");
                assertThat(scalar(statement, "SELECT count(*) FROM customers")).isZero();
            }
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(endpoint(destinationFiles) + "/" + BUCKET + "/" + key)).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isIn(401, 403);
            long restoreMs = elapsed(restoreStart);
            Path report = Path.of("target/backup-restore-report.txt");
            Files.writeString(report, "Synthetic recovery drill PASS\nExecuted UTC: " + Instant.now()
                    + "\nBackup ms: " + backupMs + "\nInfrastructure restore and checks ms: " + restoreMs
                    + "\nSnapshot age at simulated incident ms: " + java.time.Duration.between(recoveryPoint, incident).toMillis()
                    + "\nPost-snapshot rows intentionally lost: 1\nRestored tenants: 2; customers: 1; objects: 1"
                    + "\nDatabase bytes: " + Files.size(dump) + "; object bytes: " + bytes.length
                    + "\nChecks: Flyway validation, RLS, ACL, SHA-256, anonymous object denied."
                    + "\nNot production RPO/RTO. No application restart, real booking/evidence linkage, offsite backup or key recovery tested.\n");
        }
    }
    static PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17").withDatabaseName("recovery").withUsername("agendou_app").withPassword("synthetic_only");
    }
    static GenericContainer<?> minio() {
        return new GenericContainer<>("agendou-minio-local:7aac2a2").withEnv("MINIO_ROOT_USER", "testaccess")
                .withEnv("MINIO_ROOT_PASSWORD", "testsecret123").withCommand("server /data").withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    }
    static String endpoint(GenericContainer<?> container) { return "http://" + container.getHost() + ":" + container.getMappedPort(9000); }
    static S3EvidenceStorage storage(GenericContainer<?> container) { return new S3EvidenceStorage(endpoint(container), "testaccess", "testsecret123", BUCKET); }
    static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
    static void checked(GenericContainer<?> container, String... command) throws Exception {
        var result = container.execInContainer(command);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }
    static void execute(PostgreSQLContainer<?> container, String sql) throws Exception {
        try (var connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword()); var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static long count(PostgreSQLContainer<?> container, String sql) throws Exception {
        try (var connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword()); var statement = connection.createStatement()) { return scalar(statement, sql); }
    }
    private static long scalar(Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }
}
