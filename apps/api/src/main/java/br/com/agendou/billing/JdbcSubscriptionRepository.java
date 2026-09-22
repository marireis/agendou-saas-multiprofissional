package br.com.agendou.billing;

import br.com.agendou.tenancy.TenantSessionConfigurer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia real de {@link Subscription} em PostgreSQL, substituindo o
 * antigo {@code InMemorySubscriptionRepository} (removido).
 *
 * <p>Toda operacao aplica primeiro o tenant atual na sessao do banco via
 * {@link TenantSessionConfigurer#applyCurrentTenant()}, dentro da mesma
 * transacao, para que a RLS criada em V001 (e a role sem BYPASSRLS criada
 * em V002) realmente isolem os dados por tenant.</p>
 */
@Repository
public class JdbcSubscriptionRepository implements SubscriptionRepository {
    private static final RowMapper<Subscription> ROW_MAPPER = JdbcSubscriptionRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final TenantSessionConfigurer tenantSessionConfigurer;

    public JdbcSubscriptionRepository(JdbcTemplate jdbcTemplate, TenantSessionConfigurer tenantSessionConfigurer) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantSessionConfigurer = tenantSessionConfigurer;
    }

    @Override
    @Transactional
    public Subscription save(Subscription subscription) {
        tenantSessionConfigurer.assertMatchesCurrentTenant(subscription.tenantId());
        tenantSessionConfigurer.applyCurrentTenant();

        jdbcTemplate.update(
            """
            INSERT INTO subscriptions (
                id, tenant_id, plan_code, status,
                trial_started_at, trial_ends_at, paid_until, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (tenant_id) DO UPDATE SET
                plan_code = EXCLUDED.plan_code,
                status = EXCLUDED.status,
                trial_started_at = EXCLUDED.trial_started_at,
                trial_ends_at = EXCLUDED.trial_ends_at,
                paid_until = EXCLUDED.paid_until,
                updated_at = now()
            """,
            UUID.randomUUID(),
            subscription.tenantId(),
            subscription.planCode().name(),
            subscription.status().name(),
            toTimestamp(subscription.trialStartedAt()),
            toTimestamp(subscription.trialEndsAt()),
            toTimestamp(subscription.paidUntil())
        );

        return subscription;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findByTenantId(UUID tenantId) {
        tenantSessionConfigurer.assertMatchesCurrentTenant(tenantId);
        tenantSessionConfigurer.applyCurrentTenant();

        try {
            Subscription subscription = jdbcTemplate.queryForObject(
                """
                SELECT tenant_id, plan_code, status, trial_started_at, trial_ends_at, paid_until
                FROM subscriptions
                WHERE tenant_id = ?
                """,
                ROW_MAPPER,
                tenantId
            );
            return Optional.ofNullable(subscription);
        } catch (EmptyResultDataAccessException notFound) {
            return Optional.empty();
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Subscription(
            UUID.fromString(rs.getString("tenant_id")),
            PlanCode.valueOf(rs.getString("plan_code")),
            SubscriptionStatus.valueOf(rs.getString("status")),
            toInstant(rs, "trial_started_at"),
            toInstant(rs, "trial_ends_at"),
            toInstant(rs, "paid_until")
        );
    }
}
