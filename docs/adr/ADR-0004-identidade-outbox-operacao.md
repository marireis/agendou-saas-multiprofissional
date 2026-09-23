# ADR-0004 - Reenvio de verificacao, limites e ciclo da outbox

Data: 23/09/2026. Estado: implementada.

## Contexto

Um link de verificacao expirado nao tinha recuperacao pela interface. Solicitacoes de autenticacao nao tinham limites. A outbox interrompia envios apos varias falhas, mas nao distinguia falha definitiva de espera e podia guardar/enviar links vencidos.

## Decisoes

- `POST /auth/verification-email` responde 202 generico para contas inexistentes, verificadas e nao verificadas. Exige CSRF e entrada valida.
- Reenvio nao altera tenant, perfil, assinatura ou data de trial. Lock do usuario serializa emissao e consumo; ha somente um token vigente por usuario/finalidade. Reemissao cancela emails pendentes anteriores. Pedidos com menos de 60 segundos reutilizam a solicitacao vigente.
- Tokens continuam aleatorios, de uso unico e validos por 15 minutos; somente seus hashes ficam em auth_tokens.
- Limites em PostgreSQL, em transacoes independentes: 10 logins por email/15 minutos; 3 solicitacoes de email por email/15 minutos (cadastro, reenvio e recuperacao compartilham contador); 120 chamadas auth por endereco da conexao/minuto. Janelas fixas UTC. Contadores sobrevivem a reinicio e sao atomicos entre replicas.
- Chaves dos contadores usam SHA-256 do escopo/identificador; nao se persistem emails/IPs em texto nesses contadores. Isso e pseudonimizacao, nao anonimato; identificadores de baixa entropia podem ser enumerados. Limpeza horaria remove janelas vencidas.
- Headers Forwarded/X-Forwarded-For nao sao usados como autoridade. Atras do proxy Next, o limite por conexao e compartilhado. Antes de deploy, configurar ingress confiavel e avaliar limites por usuario/IP real. Nao confiar diretamente em headers publicos.
- HTTP 429 inclui Retry-After. A interface respeita a espera e mantem mensagens genericas.
- A outbox passa por PENDING, SENT, FAILED, EXPIRED ou CANCELED. Cada tentativa usa sua propria transacao com FOR UPDATE SKIP LOCKED; no maximo 10 mensagens por ciclo.
- Falhas SMTP sao repetidas ate 6 tentativas, com esperas de 30, 60, 120, 240 e 300 segundos. Mensagens expiradas ou revogadas nao sao enviadas. Um FAILED historico e mantido para diagnostico; recuperacao ocorre pela geracao de novo link, nunca restaurando um token antigo.
- Corpo/destinatario sao limpos apos envio ou estado terminal. Logs de falha registram apenas ID interno, tentativa e estado; nao incluem excecao SMTP, token ou destinatario.
- O SMTP entrega pelo menos uma vez: falha depois da aceitacao SMTP e antes do commit pode produzir duplicidade. Consumo unico do token preserva a seguranca; nao se promete exatamente uma entrega.
- X-Correlation-ID gerado no servidor liga respostas, erros tratados e MDC dos logs. IDs enviados pelo cliente nao substituem esse valor.

## Migracao

V004 preserva usuarios, sessoes, assinaturas e perfis. Emails pendentes anteriores nao possuem ligacao confiavel ao token: ficam EXPIRED, com conteudo apagado, em vez de arriscar envio de link vencido. Links ja recebidos continuam sujeitos a validade original. A nova tela permite solicitar outro email.

## Pendencias

MFA/plataforma, alertas e console de suporte, SES autenticado, limites ajustados por carga, limpeza de historico operacional com politica de retencao e validacao do ingress em homologacao.
