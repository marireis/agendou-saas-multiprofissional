package br.com.agendou.web;

/**
 * Formato de erro minimo alinhado ao contrato descrito em
 * {@code docs/api-contract.md} (code, message, correlation_id).
 *
 * <p>O {@code correlationId} aqui e gerado por erro, nao propagado de um
 * cabecalho de requisicao/tracing real — a infraestrutura de correlation ID
 * por requisicao (arquitetura, secao 7) ainda nao existe e fica fora do
 * escopo desta mudanca.</p>
 */
public record ApiErrorResponse(String code, String message, String correlationId) {
}
