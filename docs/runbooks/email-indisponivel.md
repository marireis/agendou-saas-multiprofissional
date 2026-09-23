# Email de acesso indisponivel - operacao local

## Uso normal

Em `/verificar`, abrir "Nao recebeu o email ou o link expirou?", informar o email do cadastro e solicitar reenvio. O retorno nao revela se a conta existe. Aguardar pelo menos 60 segundos; o link novo revoga o anterior sem reiniciar o trial. A recuperacao de senha continua em `/recuperar`.

Muitos pedidos retornam 429 com Retry-After. Os limites nao desaparecem ao reiniciar a API. O cooldown da interface apenas orienta; o controle efetivo fica no servidor/banco.

## Diagnostico sem expor conteudo

Executar com acesso autorizado ao banco local, nunca por endpoint publico:

```sql
SELECT status, count(*) AS quantidade, max(attempts) AS maior_numero_tentativas,
       min(created_at) AS mais_antigo
FROM mail_outbox
GROUP BY status;

SELECT id, status, purpose, attempts, created_at, expires_at,
       last_attempt_at, next_attempt_at, last_error_code
FROM mail_outbox
WHERE status IN ('PENDING', 'FAILED')
ORDER BY created_at
LIMIT 50;
```

Nao selecionar/copiar body, recipient ou tokens para logs ou tickets. O `X-Correlation-ID` da resposta pode acompanhar o relato de erro; o worker usa o ID da mensagem, sem contato pessoal.

- **PENDING:** aguarda a proxima tentativa. Verificar processo da API e conexao SMTP.
- **SENT:** SMTP aceitou a mensagem; isso nao comprova entrega na caixa final de um provedor externo.
- **FAILED:** seis tentativas falharam. Corrigir SMTP e pedir novo link pela tela correspondente.
- **EXPIRED:** o prazo do link terminou. Solicitar novo link.
- **CANCELED:** link foi consumido ou substituido; nao tentar reenviar esse registro.

Para ambiente local, verificar `docker compose -f infra/local/compose.yaml ps` e abrir Mailpit em http://localhost:8025. A API usa localhost:1025 por padrao. Reiniciar o servico SMTP recupera pedidos pendentes enquanto seus links ainda sao validos. Nao alterar FAILED/EXPIRED para PENDING via SQL: o token antigo pode estar revogado e o conteudo ja foi apagado.

## Parametros

- AGENDOU_AUTH_LOGIN_LIMIT: 10 por email a cada janela fixa de 15 min.
- AGENDOU_AUTH_MAIL_LIMIT: 3 por email a cada janela fixa de 15 min, somando cadastro/reenvio/recuperacao.
- AGENDOU_AUTH_PEER_LIMIT: 120 chamadas `/auth/*` por conexao a cada minuto; Next pode compartilhar o endereco entre clientes.
- `agendou.mail-worker-delay`: 5000 ms. `agendou.mail-worker-initial-delay`: 5000 ms.
- `agendou.auth.cleanup-delay`: 3600000 ms para contadores e tokens expirados.

Em homologacao, ajustar ingress confiavel, limites e alertas. O endpoint de health nao divulga fila ou contatos. O console administrativo de suporte depende de PlatformRole/MFA e permanece no backlog.

## Verificacao automatizada

`mvnw.cmd verify` cobre SMTP falho, retries, exaustao, links vencidos, cancelamento, disputa entre workers e entrega por SMTP em Mailpit descartavel. Todos os destinatarios dos testes sao sinteticos em example.test.
