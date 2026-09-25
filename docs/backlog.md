# Agendou - Backlog de desenvolvimento

Este backlog substitui a sequencia do roadmap original apenas onde a nova regra comercial altera a ordem: trial e billing entram cedo para que o cadastro, bloqueio e reativacao sejam parte estrutural do produto.

## F0 - Preparacao e contrato tecnico

**Objetivo:** iniciar o produto com decisoes rastreaveis, setup reproduzivel e fluxo claro.

- [ ] **MVP-001:** registrar ADRs de stack, monolito modular, PIX manual, trial Premium/Top, bloqueio pos-trial e preservacao de dados.
- [ ] **MVP-002:** definir jornadas mobile-first: cadastro, trial, onboarding, servicos, agenda, reserva, pagamento, consulta, bloqueio e reativacao.
- [ ] **MVP-003:** criar OpenAPI inicial com padrao de erro, datas UTC, valores em centavos e `Idempotency-Key`.
- [ ] **MVP-004:** inicializar repositorio com `apps/api`, `apps/web`, `contracts`, `infra`, `docs` e `tests/e2e`.
- [ ] **MVP-005:** configurar Maven Wrapper, Node lockfile, lint, formatter, typecheck, Docker Compose, PostgreSQL, Mailpit e S3 compativel local.
- [ ] **MVP-006:** criar fixtures de dois tenants, dois admins e dois clientes sem dados reais.
- [ ] **MVP-007:** solicitar dominio, email transacional, ambiente AWS e responsavel por conferencia PIX.

**Saida:** build vazio executavel, Compose funcional, ADRs iniciais e prototipo de fluxo.

## F1 - Fundacao, identidade e isolamento

**Objetivo:** garantir seguranca e multi-tenancy antes de qualquer regra de agenda.

- [x] **MVP-010:** criar Tenant, User, Membership, PlatformRole, AuthToken e sessao JDBC. MFA TOTP implementado; homologacao operacional pendente.
- [x] **MVP-011:** implementar cadastro admin, verificacao de email, login, logout, recuperacao e revogacao de sessoes.
- [x] **MVP-012:** aplicar cookies `HttpOnly`, `Secure`, `SameSite` e CSRF para mutacoes autenticadas por cookie.
- [x] **MVP-013:** implementar contexto de tenant por transacao e filtro de membership.
- [ ] **MVP-014:** criar RLS por tabela de negocio, com roles separadas de migracao, aplicacao e operacao.
- [ ] **MVP-015:** implementar outbox e envio base de email com Mailpit/SES.
- [ ] **MVP-016:** criar shell responsivo do painel com layout autenticado, tratamento central de erros e correlation ID.
- [ ] **MVP-017:** publicar primeiro deploy em homologacao com migrations como tarefa unica.

**Saida:** admin acessa painel, tenants ficam isolados e email base funciona.

## F2 - Trial Premium/Top, assinatura e bloqueio

**Objetivo:** tornar o teste gratis uma regra central, nao um ajuste tardio.

- [x] **MVP-020:** criar Plan, Subscription, SubscriptionEvent, BillingDecision e AuditLog de assinatura.
- [ ] **MVP-021:** cadastrar planos Basico, Intermediario e Premium/Top, com trial permitido somente no Premium/Top.
- [ ] **MVP-022:** iniciar `TRIAL_ACTIVE` por 7 dias na criacao do tenant profissional.
- [ ] **MVP-023:** exibir banners de trial ativo, ultimos 2 dias e trial vencido.
- [ ] **MVP-024:** implementar worker de expiracao de trial para mudar tenant para `TRIAL_EXPIRED_BLOCKED`.
- [ ] **MVP-025:** bloquear novas reservas, publicacao e operacao administrativa ativa em trial vencido.
- [ ] **MVP-026:** manter acesso limitado para visualizar aviso, escolher plano e informar/registrar pagamento.
- [x] **MVP-027:** implementar reativacao administrativa ou confirmacao de pagamento para `PAID_ACTIVE`, preservando configuracao.
- [ ] **MVP-028:** impedir novo trial automatico para mesmo tenant/email/documento/telefone sem override auditado.
- [x] **MVP-029:** criar painel super admin minimo de assinatura, vigencia, status, bloqueio, desbloqueio e auditoria em `/plataforma`, com MFA obrigatorio.

**Saida:** usuario testa Premium/Top por 7 dias; vencido sem pagamento fica bloqueado; pagamento reativa com dados intactos.

## F3 - Perfil, marca, servicos e publicacao

**Objetivo:** permitir que o profissional prepare uma pagina publica completa.

- [x] **MVP-030:** onboarding com nome, slug reservado, contato, descricao, modalidade/local, fuso e progresso do perfil.
- [x] **MVP-031:** upload de logo ate 2 MB com validacao por conteudo e variante PNG sanitizada; consulta autenticada nesta etapa, exposicao publica depende de MVP-034/035. Persistencia compacta no banco conforme ADR-0006.
- [x] **MVP-032:** cadastro, edicao, inativacao/reativacao de servicos com duracao 5-480 min, preco positivo, buffers 0-240 min e entrada 50-100%; RLS, bloqueio por assinatura e versao contra sobrescrita concorrente.
- [x] **MVP-033:** formulario PIX, recebedor, instrucoes/politica, validacao local e auditoria com versoes imutaveis (V008). Referencia/copia por reserva depende do MVP-052; nao existe confirmacao bancaria automatica.
- [ ] **MVP-034 (parcial):** página `/a/{slug}`, perfil/logo, serviços ativos, preços/entrada, estado indisponível e prévia privada implementados. CTA permanece desabilitado até disponibilidade/reserva reais.
- [ ] **MVP-035 (parcial):** requisitos calculados, rascunho privado, retirada e bloqueio de publicação implementados. Liberação efetiva depende de disponibilidade MVP-040 a 045; nenhuma conta publicada automaticamente.
- [ ] **MVP-036:** aba Compartilhar com copiar link publico e mensagem editavel para abrir no WhatsApp; agenda precisa estar publicada, sem envio automatico ou integracao simulada.

**Saida:** catalogo e pagina publica prontos para publicar apos disponibilidade.

## F4 - Disponibilidade e calendario

**Objetivo:** gerar horarios confiaveis e impedir dupla reserva.

- [ ] **MVP-040:** configurar dias, periodos, pausas, excecoes, feriados manuais e bloqueios.
- [ ] **MVP-041:** implementar gerador de candidatos considerando duracao, buffers, antecedencia, horizonte e fuso IANA.
- [ ] **MVP-042:** criar `CalendarAllocation` com `tstzrange`, `active` e constraint GiST por tenant/recurso.
- [ ] **MVP-043:** implementar alocacao transacional com conflito HTTP 409.
- [ ] **MVP-044:** criar calendario semanal desktop e lista diaria mobile sem drag-and-drop.
- [ ] **MVP-045:** coordenar mudanca de expediente, bloqueio e reserva por ordem de locks.

**Saida:** horarios publicos confiaveis e calendario administrativo funcional.

## F5 - Reserva publica, quota e consulta segura

**Objetivo:** cliente cria uma solicitacao privada sem conta permanente.

- [ ] **MVP-050:** fluxo publico servico -> data/hora -> contato -> verificacao -> revisao.
- [ ] **MVP-051:** token de acesso unico com hash, validade de 15 min e respostas genericas.
- [ ] **MVP-052:** criar reserva temporaria, snapshots, alocacao, quota provisoria e outbox na mesma transacao.
- [ ] **MVP-053:** implementar idempotencia por tenant/ator/operacao e hash do corpo.
- [ ] **MVP-054:** implementar quota mensal e provisoria conforme plano vigente, sem descartar recebimento por limite.
- [ ] **MVP-055:** criar area `Meus agendamentos` com detalhe, estado e prazo de expiracao.
- [ ] **MVP-056:** worker de expiracao de reservas com leases, retries e limpeza sincronica antes de nova reserva.

**Saida:** cliente recebe reserva `AguardandoPagamento`, horario bloqueado temporariamente e consulta segura.

## F6 - PIX manual e operacao diaria

**Objetivo:** completar reserva, pagamento, conferencia e atendimento.

- [ ] **MVP-060:** criar PaymentIntent, PaymentTransaction, PaymentEvidence, Refund e interface `PaymentProvider` manual.
- [ ] **MVP-061:** tela de pagamento com total, entrada, saldo, recebedor, chave PIX, copiar chave e prazo.
- [ ] **MVP-062:** receber JPEG, PNG ou PDF ate 5 MB, validar conteudo e armazenar em area privada.
- [ ] **MVP-063:** mover para `EmConferencia` apos comprovante no prazo, sem estender prazo por reenvio.
- [ ] **MVP-064:** painel de conferencia com valor recebido, conta, referencia bancaria unica e data.
- [ ] **MVP-065:** confirmar somente com entrada liquida suficiente e reserva valida, sob lock.
- [ ] **MVP-066:** tratar pagamento insuficiente, duplicado, excedente, tardio e devolucao manual.
- [ ] **MVP-067:** implementar cancelar, reagendar mesmo servico, concluir, marcar falta e reserva assistida.
- [ ] **MVP-068:** enviar emails de solicitacao, confirmacao, alteracao, expiracao e cancelamento.
- [ ] **MVP-069:** aba Financeiro do profissional com recebimentos, entradas/saldos pendentes, devolucoes e filtros por periodo, derivados dos pagamentos reais; independente da assinatura do SaaS.

**Saida:** jornada ponta a ponta real com PIX manual e operacao diaria.

## F7 - Homologacao, privacidade e operacao

**Objetivo:** garantir que o produto pode ser operado e recuperado.

- [ ] **MVP-070:** aba Clientes com cadastro manual, edicao, busca, contato e historico por tenant; coordenar duplicidades com reservas. Antecipar junto da reserva assistida (MVP-067), antes da homologacao.
- [ ] **MVP-071:** procedimentos de exportacao, correcao e exclusao verificadas.
- [ ] **MVP-072:** revisar CSP, CSRF, uploads, rate limits, logs, arquivos privados e tokens.
- [ ] **MVP-073:** testar restore de banco e arquivos, registrando RPO/RTO medidos.
- [ ] **MVP-074:** executar E2E, carga, acessibilidade por teclado/leitor e revisao de mensagens.
- [ ] **MVP-075:** completar runbooks de deploy, rollback, comprovante suspeito, pagamento tardio, email indisponivel, isolamento e trial vencido.

**Saida:** release candidata com evidencias dos testes criticos.

## F8 - Producao e piloto

**Objetivo:** validar uso real sem ampliar escopo.

- [ ] **MVP-080:** publicar release versionada com digest, migration unica e smoke test pos-deploy.
- [ ] **MVP-081:** cadastrar primeiro profissional, validar PIX e acompanhar primeira reserva real autorizada.
- [ ] **MVP-082:** acompanhar primeiro vencimento real de trial e primeiro pagamento de reativacao.
- [ ] **MVP-083:** ampliar para 5-10 profissionais, registrando duvidas e tempo de onboarding.
- [ ] **MVP-084:** monitorar expiracoes, pagamentos, conflitos, falhas de email, bloqueios e tickets.
- [ ] **MVP-085:** decidir continuar, corrigir ou ampliar com base nos criterios de validacao.

**Saida:** piloto acompanhado e decisao de continuidade registrada.

## Evidencias de execucao

O checklist acima representa o escopo completo. Consulte [implementation-status.md](implementation-status.md) para entregas efetivamente implementadas, parciais e validadas em 23/09/2026; itens amplos nao devem ser marcados completos apenas pela existencia de scaffold.
