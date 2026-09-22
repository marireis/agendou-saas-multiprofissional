package br.com.agendou.tenancy;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Aplica o tenant atual (vindo do {@link TenantContext}) na sessao do
 * PostgreSQL via {@code set_config('app.tenant_id', ..., true)}.
 *
 * <p>O terceiro argumento {@code true} faz o `set_config` valer apenas para
 * a transacao corrente (equivalente a {@code SET LOCAL}), entao repositorios
 * DEVEM chamar {@link #applyCurrentTenant()} como a primeira coisa dentro de
 * um metodo {@code @Transactional} — se chamado fora de uma transacao, o
 * valor persistiria na conexao entre requisicoes (risco de vazamento entre
 * tenants em um pool de conexoes), entao preferimos usar o escopo local.</p>
 *
 * <p>Usamos {@code set_config(...)} com bind parameter em vez de montar
 * {@code SET LOCAL app.tenant_id = '<valor>'} via concatenacao de string,
 * exatamente para nao depender de nenhuma sanitizacao manual do UUID.</p>
 */
@Component
public class TenantSessionConfigurer {
    private static final String SET_TENANT_SQL = "SELECT set_config('app.tenant_id', ?, true)";

    private final JdbcTemplate jdbcTemplate;

    public TenantSessionConfigurer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Deve ser chamado no inicio de todo metodo {@code @Transactional} de um
     * repositorio que acesse tabela protegida por RLS por tenant.
     */
    public void applyCurrentTenant() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Contexto de tenant exige transacao ativa.");
        }
        UUID tenantId = TenantContext.require();
        jdbcTemplate.queryForObject(SET_TENANT_SQL, String.class, tenantId.toString());
    }

    /**
     * Confere que o tenantId recebido como parametro de metodo (por
     * exemplo, vindo do Service) e exatamente o mesmo que esta no contexto
     * da requisicao. Isso torna o contexto a unica fonte de verdade real: um
     * tenantId de metodo divergente do contexto e tratado como bug/violacao
     * de isolamento, nunca silenciosamente aceito.
     */
    public void assertMatchesCurrentTenant(UUID tenantId) {
        UUID current = TenantContext.require();
        if (!current.equals(tenantId)) {
            throw new TenantMismatchException(
                "tenantId do metodo diverge do tenant no contexto da requisicao.");
        }
    }
}
