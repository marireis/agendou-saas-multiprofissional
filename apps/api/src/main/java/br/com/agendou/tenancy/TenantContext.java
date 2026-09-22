package br.com.agendou.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Guarda o tenant da requisicao atual em um ThreadLocal.
 *
 * <p>Nao e uma fonte de autorizacao: quem popula este contexto (hoje,
 * {@link TenantContextInterceptor}) e responsavel por garantir que o valor
 * veio de um lugar confiavel. Repositorios usam o valor guardado aqui,
 * nunca um tenantId recebido solto em parametro de metodo, para configurar
 * a sessao do banco antes de qualquer consulta (ver {@link TenantSessionConfigurer}).</p>
 */
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId nao pode ser nulo.");
        }
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static UUID require() {
        return current().orElseThrow(() ->
            new IllegalStateException("Nenhum tenant presente no contexto da requisicao atual."));
    }

    /**
     * Deve ser chamado ao final de toda requisicao (ver
     * {@link TenantContextInterceptor#afterCompletion}) para evitar que o
     * valor vaze para a proxima requisicao processada pela mesma thread,
     * já que threads de servidor web sao reaproveitadas por um pool.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
