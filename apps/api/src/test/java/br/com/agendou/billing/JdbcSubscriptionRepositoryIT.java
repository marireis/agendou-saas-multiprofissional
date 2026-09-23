package br.com.agendou.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.agendou.tenancy.TenantContext;
import br.com.agendou.tenancy.TenantMismatchException;
import br.com.agendou.tenancy.TenantSessionConfigurer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prova, com um Postgres real (Testcontainers), que:
 *
 * <ol>
 *   <li>{@link JdbcSubscriptionRepository} grava e le assinaturas de verdade
 *   (nao mais o repositorio em memoria);</li>
 *   <li>a role de runtime (agendou_runtime, criada em V002, sem BYPASSRLS)
 *   nunca enxerga dado de outro tenant, mesmo ignorando por completo o
 *   guard de aplicacao ({@link TenantSessionConfigurer#assertMatchesCurrentTenant});</li>
 *   <li>sem nenhum tenant configurado na sessao, a RLS nega acesso a
 *   qualquer linha (falha fechada), como exigido em
 *   docs/testing-and-acceptance.md ("RLS nega acesso sem contexto").</li>
 * </ol>
 *
 * <p>Requer Docker disponivel na maquina que roda os testes.</p>
 */
@Testcontainers
class JdbcSubscriptionRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
        .withDatabaseName("agendou")
        .withUsername("agendou_app")
        .withPassword("agendou_app");

    private static AnnotationConfigApplicationContext runtimeContext;
    private static JdbcTemplate ownerJdbcTemplate;
    private static SubscriptionRepository repository;

    @BeforeAll
    static void migrateSchemaAndWireRuntimeRole() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        ownerJdbcTemplate = new JdbcTemplate(
            dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        runtimeContext = new AnnotationConfigApplicationContext(RuntimeTestConfig.class);
        repository = runtimeContext.getBean(SubscriptionRepository.class);
    }

    @AfterAll
    static void closeContext() {
        if (runtimeContext != null) {
            runtimeContext.close();
        }
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void savesAndReadsBackSubscriptionForOwningTenant() {
        UUID tenantId = createTenant("tenant-a");

        TenantContext.set(tenantId);
        repository.save(premiumTrial(tenantId));
        Optional<Subscription> found = repository.findByTenantId(tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().tenantId()).isEqualTo(tenantId);
        assertThat(found.get().status()).isEqualTo(SubscriptionStatus.TRIAL_ACTIVE);
        assertThat(found.get().planCode()).isEqualTo(PlanCode.PREMIUM_TOP);
    }

    @Test
    void repositoryRejectsTenantIdThatDoesNotMatchTheRequestContext() {
        UUID tenantA = createTenant("tenant-b");
        UUID tenantB = createTenant("tenant-c");

        TenantContext.set(tenantA);
        repository.save(premiumTrial(tenantA));
        TenantContext.clear();

        // Simula uma segunda requisicao (de tenantB) tentando ler o id de tenantA.
        TenantContext.set(tenantB);
        assertThrows(TenantMismatchException.class, () -> repository.findByTenantId(tenantA));
    }

    @Test
    void rowLevelSecurityBlocksDirectCrossTenantReadEvenBypassingTheApplicationGuard() {
        UUID tenantA = createTenant("tenant-d");
        UUID tenantB = createTenant("tenant-e");

        TenantContext.set(tenantA);
        repository.save(premiumTrial(tenantA));
        TenantContext.clear();

        // Consulta direta com a role de runtime, contornando de proposito o
        // guard de aplicacao, para provar que quem realmente impede o
        // vazamento e a RLS do Postgres, nao so a checagem em Java.
        JdbcTemplate runtimeJdbcTemplate = runtimeContext.getBean(JdbcTemplate.class);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
            runtimeContext.getBean(DataSourceTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            runtimeJdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantB.toString());
            assertThat(runtimeJdbcTemplate.queryForObject(
                "SELECT current_setting('app.tenant_id')", String.class)).isEqualTo(tenantB.toString());
            assertThat(runtimeJdbcTemplate.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE tenant_id = ?", Long.class, tenantA)).isZero();
        });
    }

    @Test
    void rowLevelSecurityDeniesAllAccessWhenNoTenantContextIsSet() {
        UUID tenantId = createTenant("tenant-f");
        TenantContext.set(tenantId);
        repository.save(premiumTrial(tenantId));
        TenantContext.clear();

        // Conexao nova, sem nenhum set_config de app.tenant_id: a policy
        // compara tenant_id com NULL, que nunca da match, entao nenhuma
        // linha de nenhum tenant deveria aparecer (falha fechada).
        JdbcTemplate runtimeJdbcTemplate = runtimeContext.getBean(JdbcTemplate.class);
        Long visibleWithoutContext = runtimeJdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions", Long.class);

        assertThat(visibleWithoutContext).isZero();
    }

    private static Subscription premiumTrial(UUID tenantId) {
        Instant start = Instant.parse("2026-09-22T10:00:00Z");
        return new Subscription(
            tenantId,
            PlanCode.PREMIUM_TOP,
            SubscriptionStatus.TRIAL_ACTIVE,
            start,
            start.plus(TrialPolicy.TRIAL_DURATION),
            null
        );
    }

    @Test
    void upgradingLegacyOutboxPreservesIdentityAndTrial() {
        String database = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        ownerJdbcTemplate.execute("CREATE DATABASE " + database);
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target("3").load().migrate();
        var legacy = new JdbcTemplate(dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID user = UUID.randomUUID(), tenant = UUID.randomUUID(), message = UUID.randomUUID();
        legacy.update("INSERT INTO app_users(id,email,password_hash) VALUES (?, 'legacy@example.test', 'synthetic')", user);
        legacy.update("INSERT INTO tenants(id,slug,display_name,status) VALUES (?, 'legacy', 'Legacy', 'ACTIVE')", tenant);
        legacy.update("INSERT INTO subscriptions(id,tenant_id,plan_code,status,trial_started_at,trial_ends_at) VALUES (?,?,'PREMIUM_TOP','TRIAL_ACTIVE',now(),now()+interval '7 days')", UUID.randomUUID(), tenant);
        legacy.update("INSERT INTO auth_tokens(token_hash,user_id,purpose,expires_at) VALUES ('synthetic-hash',?,'VERIFY',now()+interval '15 minutes')", user);
        legacy.update("INSERT INTO mail_outbox(id,recipient,subject,body) VALUES (?, 'legacy@example.test','Test','Synthetic link')", message);
        var trial = legacy.queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?", tenant);
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").load().migrate();
        assertThat(legacy.queryForMap("SELECT * FROM subscriptions WHERE tenant_id=?", tenant)).isEqualTo(trial);
        assertThat(legacy.queryForObject("SELECT count(*) FROM app_users WHERE id=?", Long.class, user)).isEqualTo(1);
        assertThat(legacy.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id=?", Long.class, user)).isEqualTo(1);
        var mail = legacy.queryForMap("SELECT status,recipient,body FROM mail_outbox WHERE id=?", message);
        assertThat(mail).containsEntry("status", "EXPIRED").containsEntry("recipient", "").containsEntry("body", "");
    }

    private static UUID createTenant(String slug) {
        UUID id = UUID.randomUUID();
        ownerJdbcTemplate.update(
            "INSERT INTO tenants (id, slug, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
            id, slug, slug
        );
        return id;
    }

    private static DataSource dataSource(String url, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    /**
     * Contexto Spring minimo, so com o que e preciso para exercitar o
     * {@code @Transactional} real de {@link JdbcSubscriptionRepository}
     * (sem isso, um teste que so faz `new JdbcSubscriptionRepository(...)`
     * chamaria os metodos sem proxy nenhum de transacao, e o
     * `set_config(..., true)` feito em {@link TenantSessionConfigurer}
     * expiraria antes do INSERT/SELECT seguinte rodar).
     */
    @Configuration
    @EnableTransactionManagement
    static class RuntimeTestConfig {
        @Bean
        DataSource dataSource() {
            return JdbcSubscriptionRepositoryIT.dataSource(
                POSTGRES.getJdbcUrl(), "agendou_runtime", "agendou_runtime_local_only");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TenantSessionConfigurer tenantSessionConfigurer(JdbcTemplate jdbcTemplate) {
            return new TenantSessionConfigurer(jdbcTemplate);
        }

        @Bean
        SubscriptionRepository subscriptionRepository(
            JdbcTemplate jdbcTemplate, TenantSessionConfigurer tenantSessionConfigurer
        ) {
            return new JdbcSubscriptionRepository(jdbcTemplate, tenantSessionConfigurer);
        }
    }
}
