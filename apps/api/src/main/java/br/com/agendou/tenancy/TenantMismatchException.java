package br.com.agendou.tenancy;

/**
 * Lancada quando um componente pede dados de um tenantId diferente do
 * tenant presente no {@link TenantContext} da requisicao atual.
 *
 * <p>Isto nunca deveria acontecer em uso normal (o tenantId do path e o
 * mesmo colocado no contexto pelo {@link TenantContextInterceptor}), entao
 * quando ocorre tratamos como violacao de isolamento, nao como bug comum:
 * ver {@link br.com.agendou.web.ApiExceptionHandler}.</p>
 */
public class TenantMismatchException extends RuntimeException {
    public TenantMismatchException(String message) {
        super(message);
    }
}
