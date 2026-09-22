# ADR-0003 - Identidade, sessoes e autoridade do tenant

Data: 22/09/2026. Estado: implementada para desenvolvimento local.

O scaffold recebia tenant pela URL sem conferir identidade e permitia confirmar pagamento publicamente. O contexto SQL isolava consultas, mas nao autorizava o chamador.

## Decisao

- Usuario global identificado por UUID, email normalizado unico e senha PBKDF2; membership ADMIN associa um unico tenant no MVP.
- Cadastro cria usuario, tenant, perfil, trial e email em outbox na mesma transacao. O trial inicia no cadastro.
- Tokens aleatorios de 256 bits, hash SHA-256 na tabela de autenticacao, validade de 15 minutos e consumo atomico unico. A outbox guarda temporariamente o corpo necessario ao envio, apagado apos sucesso.
- Spring Security com sessao JDBC, cookies HttpOnly/Secure/SameSite=Lax e CSRF em todas as mutacoes, inclusive login. HTTP local exige desabilitar Secure explicitamente.
- Tenant vem da membership do usuario autenticado; URL divergente retorna 404. SET LOCAL exige transacao ativa e runtime sem BYPASSRLS.
- Roles globais de identidade e fila nao sao expostas por CRUD generico. Tabelas de negocio sao protegidas por RLS.
- Trial expira em worker e na consulta operacional. Lock do tenant serializa mudancas de assinatura e operacoes protegidas. Eventos append-only registram transicoes.
- Confirmacao de pagamento permanece interna; nao existe endpoint publico de autoativacao. Endpoint de plataforma depende de MFA e auditoria administrativa futura.

## Limites

Antes de piloto: MFA e operacao de plataforma, rate limits, reenvio de verificacao expirada, observabilidade/reprocessamento da outbox, isolamento de privilegios das tabelas globais e endurecimento de deploy. Defaults de banco e SMTP sao somente locais. Nao existe provedor de pagamento ou envio externo configurado.
