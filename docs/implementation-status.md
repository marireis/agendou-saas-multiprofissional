# Agendou - Status de implementação

**Atualizado em:** 23/09/2026. Desenvolvimento na própria pasta do projeto.

## Entrega mais recente em 23/09: superadmin, MFA e decisões de assinatura

- Área `/plataforma` com login existente e papel SUPER_ADMIN provisionado por operador; conta comum não pode se promover nem acessar dados da plataforma.
- MFA por aplicativo autenticador: configuração com senha atual, segredo cifrado, códigos de uso único, proteção contra concorrência/replay, limite 5/5 min e sessão elevada por 15 minutos. Novo login remove a elevação.
- Consulta paginada de profissionais, detalhe da assinatura, histórico de decisões e auditoria de acessos/ações. Sem impersonação ou acesso a clientes privados.
- Confirmação manual de pagamento com plano, centavos, referência bancária única, motivo e UUID idempotente. Acrescenta 30 dias uma única vez; trial/perfil preservados.
- Suspensão e retirada de suspensão auditadas. Retirar suspensão respeita vigência original; não cria novo trial nem libera período gratuito.
- Assinatura paga expirada passa a PAST_DUE e bloqueia operação, sem carência. Reativação por pagamento restaura operação sem recriar dados.
- V005, OpenAPI 0.4.0, ADR-0005 e runbook de superadmin. Runtime não concede roles nem apaga/altera auditoria e decisões; RLS preservada.
- Script Windows `infra/local/start-api.ps1`: Java/Maven e chave MFA protegida por DPAPI no arquivo local ignorado pelo Git. Sem chave padrão compartilhada.
- Banco local existente atualizado até V005, sem apagar volumes. Conta indicada pela responsável habilitada como superadmin; senha preservada. Configuração do autenticador deve ser feita pela titular no primeiro acesso.

## Entrega anterior em 23/09: identidade e operação de emails

A primeira prioridade da lista anterior foi implementada: reenvio de verificação, limites de tentativas e tratamento operacional da outbox. A identidade visual aprovada foi preservada.

| Entrega | Comportamento implementado |
|---|---|
| Reenvio de verificação | `POST /auth/verification-email`; tela `/verificar` permite pedir novo email, inclusive após link vencido |
| Resposta segura | Retorno 202 genérico para email inexistente, conta verificada ou aguardando verificação; CSRF e validação obrigatórios |
| Tokens e concorrência | Reemissão revoga links anteriores; consumo único; lock do usuário evita links concorrentes; pedidos em menos de 60 s são agrupados |
| Trial preservado | Reenvio não recria tenant/perfil nem altera início/fim do trial |
| Limites persistidos | PostgreSQL: 10 logins/email/15 min, 3 solicitações de email/email/15 min, 120 chamadas auth por endereço de conexão/minuto; contadores independentes de falhas do login |
| Espera na interface | HTTP 429 com Retry-After; login/recuperação/reenvio mostram contagem e desabilitam nova tentativa durante a espera |
| Outbox com ciclo explícito | PENDING, SENT, FAILED, EXPIRED, CANCELED; prazo do link e vínculo ao token/usuário; falhas históricas preservadas |
| Retentativas SMTP | Até 6 tentativas com esperas crescentes; transação por mensagem e SKIP LOCKED; mensagens revogadas/expiradas não são enviadas |
| Privacidade operacional | Corpo e destinatário apagados ao concluir; logs de falha só com ID interno/tentativa; limpeza de tokens e contadores expirados |
| Diagnóstico de requisição | X-Correlation-ID gerado pelo servidor, mesmo ID nos erros tratados e MDC dos logs; CSRF/autorização/429 seguem JSON padronizado |
| Contrato e operação | OpenAPI 0.3.0, migration V004, ADR-0004 e runbook de email indisponível |

## Base já implementada

- Maven 3.9.9/Wrapper oficial e Java 21; PostgreSQL com Flyway e role de runtime separada da role de migração.
- Cadastro transacional de usuário, tenant, membership, perfil, assinatura e outbox.
- Verificação de email, login/logout e recuperação de senha com revogação de sessões JDBC.
- Cookies HttpOnly/Secure/SameSite=Lax, CSRF e tenant derivado de membership autenticada.
- RLS nas tabelas de negócio existentes, contexto local obrigatório em transação e negação de acesso a outro tenant.
- Planos cadastrados; trial gratuito somente Premium/Top por 7 dias; expiração síncrona e agendada, eventos de assinatura e bloqueio de alteração de perfil.
- Reativação auditada pela plataforma com MFA preservando perfil e tenant. Nenhuma confirmação pública de pagamento está habilitada.
- Perfil com nome, descrição, slug reservado e fuso IANA; frontend conectado à assinatura e ao perfil reais.
- Home escura/verde-água com a logomarca, slogan aprovado, FAQ e ilustração de agenda; login/cadastro/painel compartilham a identidade.
- O usuário relatou em 23/09 ter testado login, sair e recuperação de senha com sucesso.

## Validação desta entrega

| Check | Evidência |
|---|---|
| `mvnw.cmd verify` | PASS: 12 testes unitários + 26 de integração (38 no total), sem falhas ou testes ignorados; PostgreSQL 17 e Mailpit descartáveis |
| Migrations V001 a V005 | Aplicadas em bancos descartáveis, usando role real de runtime |
| Atualização de banco existente | V003 → V005 preserva usuário, token e todos os dados da assinatura/trial; email legado pendente expira e tem conteúdo limpo |
| Plataforma/MFA | Conta comum negada, CSRF, papel revogado, MFA vencido, novo login, replay/concorrência, limite de tentativas, configuração expirada e vetores RFC 6238 |
| Decisões e billing | Confirmação idempotente, concorrência sem dupla vigência, referência duplicada com rollback, auditoria protegida, suspensão, retirada sem novo trial e expiração paga |
| Reenvio/consumo concorrentes | Uma nova emissão por janela de cooldown e apenas um vencedor no consumo do token |
| Limites concorrentes | 20 tentativas simultâneas admitem exatamente 10; renovação na próxima janela; falhas de login não apagam contadores |
| Segurança HTTP | Respostas genéricas, CSRF, validação, 429/Retry-After, correlation ID e X-Forwarded-For sem influência no endereço confiável |
| Outbox | Falha/retry/sucesso, exaustão, expiração, revogação, preservação de cadastro e exclusão mútua entre workers |
| SMTP real | Mensagem sintética aceita por Mailpit via SMTP; registro passa a SENT e conteúdo é limpo |
| Regressão de negócio | Cadastro, sessão, RLS, bloqueio do trial, reativação, recuperação e logout continuam cobertos |
| `npm run typecheck` e `npm run build` | PASS |

Relatórios: `apps/api/target/surefire-reports`, `apps/api/target/failsafe-reports`. Log da suíte atual: `apps/api/platform-verify.log`. PASS técnico não equivale à homologação de negócio. Frontend validado por tipagem/build; E2E de navegador da plataforma e homologação do autenticador pela titular ainda pendentes. API local iniciou na porta 8080 e provisionamento da role foi confirmado por transação no banco.

## Backlog: concluído e parcial

- **MVP-005:** Maven/Wrapper/lockfile/typecheck disponíveis. Lint/formatter e pipeline completo pendentes.
- **MVP-010:** usuário, membership, token, sessão, PlatformRole e MFA TOTP implementados.
- **MVP-011/012/013:** identidade administrativa, reenvio, recuperação/revogação, cookies, CSRF e autorização por membership implementados/testados.
- **MVP-014:** RLS nas tabelas de negócio atuais; ampliar nas próximas migrations. Identidade, sessões, fila e limites são infraestrutura global sem CRUD público.
- **MVP-015:** outbox com estados, retries, limpeza e SMTP local validado. SES, alertas e console operacional autenticado pendentes.
- **MVP-016:** shell/erros/correlation ID implementados; telemetria e observabilidade completas ainda pendentes.
- **MVP-020/021/022/023/024:** planos, trial, banners, eventos, BillingDecision, auditoria administrativa e expiração disponíveis. Retenção/exportação operacional para homologação.
- **MVP-025/026:** bloqueio real sobre perfil e consultas essenciais preservadas. Fluxos de pagamento, reserva e publicação ainda não implementados.
- **MVP-027/029:** reativação por pagamento, suspensão/retirada, consulta e auditoria no painel de plataforma com MFA concluídas. Sem cancelamento ou impersonação neste painel mínimo.
- **MVP-028:** email único impede novo trial na mesma conta. Documento/telefone ainda não são coletados.
- **MVP-030:** perfil inicial pronto; contato, modalidade/local e progresso de onboarding pendentes.

## Próximas implementações

1. **Próxima entrega — perfil e marca:** completar onboarding com contato, modalidade/local e progresso; upload seguro da logomarca (MVP-030/031).
2. **Catálogo e página:** serviços, PIX/política, perfil público e critérios reais de publicação (MVP-032 a 035). Compartilhar link/mensagem WhatsApp (MVP-036) após publicação funcional.
3. **Calendário e horários:** dias, períodos, pausas, exceções, bloqueios, agenda semanal/diária e alocação GiST com concorrência (MVP-040 a 045).
4. **Reserva:** acesso do cliente, idempotência, quota, snapshots e expiração (MVP-050 a 056).
5. **Operação do profissional:** PIX manual, comprovantes, conferência, atendimento, reserva assistida (MVP-060 a 068), aba Financeiro (MVP-069), cadastro/edição/lista/histórico de Clientes (MVP-070, antecipado junto da reserva assistida).
6. **Homologação:** E2E de navegador e MFA real, acessibilidade, SES, ingress confiável/limites por IP real, métricas/alertas, retenção de histórico, backup/restore e deploy.

### Onde entram as funções solicitadas pela responsável

| Função | Situação / etapa |
|---|---|
| Acesso superadmin | Implementado nesta entrega; ativar autenticador no primeiro acesso |
| Página com logomarca do profissional | Próxima etapa: MVP-030/031; página pública em MVP-034/035 |
| Calendário e disponibilidade | MVP-040 a 045, depois do catálogo |
| Aba de cadastrar clientes | MVP-070, junto da operação/reserva assistida MVP-067 |
| Financeiro do profissional | MVP-060 a 069; depende de reservas e pagamentos reais. Separado do billing da plataforma |
| Compartilhar link e mensagem no WhatsApp | MVP-036 explicitado no backlog; copiar/abrir mensagem, sem envio automático |

## Limitações e execução

- O limite por conexão fica compartilhado quando a API está atrás do proxy Next. Antes de publicar, configurar ingress confiável; não aceitar X-Forwarded-For público como autoridade.
- SMTP aceita a mensagem, mas não garante entrega na caixa final de um provedor externo. Uma interrupção entre envio e commit pode duplicar email; token continua de uso único.
- V004 expira emails antigos ainda pendentes, que não tinham vínculo confiável ao token. Contas/perfis/trials são preservados; pedir novo link em `/verificar`.
- V004/V005 já aplicadas localmente. Para iniciar novamente, usar `powershell -NoProfile -File infra/local/start-api.ps1` na raiz; não iniciar outra cópia se a porta 8080 estiver ocupada. Nenhum banco do usuário foi apagado; testes usam containers descartáveis.
- Agenda da home é ilustrativa. Reservas, uploads, financeiro do profissional, clientes, conferência PIX e deploy ainda não estão concluídos.
- Recuperação de MFA é operacional/auditada; não há backup codes ou reset público. A chave DPAPI depende deste usuário Windows; produção exige gestão de segredos própria.
- Credenciais padrão de banco/SMTP são locais. Nenhum email externo real ou cobrança foi realizado.

Execução: [desenvolvimento local](runbooks/desenvolvimento-local.md). Plataforma: [superadmin](runbooks/superadmin.md). Diagnóstico: [email indisponível](runbooks/email-indisponivel.md).
