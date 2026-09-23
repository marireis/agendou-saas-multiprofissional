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
- Na entrega original, confirmacao de pagamento permaneceu interna. A ADR-0005 adiciona endpoint de plataforma protegido por MFA e auditoria; autoativacao publica continua negada.

## Limites

Reenvio, limites e ciclo da outbox foram implementados na ADR-0004 (23/09); MFA e painel minimo de plataforma na ADR-0005. Antes de piloto: homologacao do MFA, observabilidade/alertas, revisao de privilegios globais e endurecimento de deploy. Defaults de banco e SMTP sao somente locais. Nao existe provedor de pagamento ou envio externo configurado.
