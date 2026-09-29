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
- [x] **MVP-034:** página `/a/{slug}`, perfil/logo, serviços ativos, preços/entrada e prévia privada. Publicação explícita disponível; CTA de reserva integrado futuramente no MVP-050.
- [x] **MVP-035:** requisitos reais de perfil/assinatura/serviço/PIX/disponibilidade, publicação explícita com revalidação sob lock e retirada sem excluir dados. Nenhuma conta publicada automaticamente. ADR-0014.
- [x] **MVP-036:** aba Compartilhar com copiar link público, convite editável, copiar mensagem e abrir WhatsApp. Exige página publicada, avisa sobre endereço local e oferece cópia manual se clipboard falhar. Sem envio automático.

**Saida:** catalogo e pagina publica prontos para publicar apos disponibilidade.

## F4 - Disponibilidade e calendario

**Objetivo:** gerar horarios confiaveis e impedir dupla reserva.

- [x] **MVP-040:** aba Horários com dias/períodos semanais, pausas por intervalos separados e exceções por data; feriado/bloqueio total sem períodos, bloqueio parcial por expediente excepcional. RLS, assinatura, CSRF e versão concorrente. Geração/aplicação em slots depende de MVP-041 a 045.
- [x] **MVP-041:** gerador e prévia privada por serviço/data; duração/buffers, grade 15min, antecedência 24h, horizonte 60 dias, fuso e exceções. Sequência ilustrativa respeita intervalo global. Períodos que cruzam transição de offset são omitidos (ADR-0011). Integrado a ocupações persistidas desde V011; reservas de clientes ainda dependem de MVP-050+.
- [x] **MVP-042:** calendar_allocations com faixa tstzrange semiaberta, active e GiST por tenant/recurso único, RLS e FK composta. V011.
- [x] **MVP-043:** alocação transacional interna HOLD e bloqueio pessoal BLOCK, lock do tenant, conflito 409, expiração preguiçosa e prévia com ocupações reais. Endpoint público de reserva depende de MVP-050+.
- [x] **MVP-044:** aba Calendário com grade mensal/semanal e lista diária, pontos de disponibilidade real, navegação/Hoje, expediente/exceções e ocupações reais; consulta por período com RLS, limites civis no fuso, faixas atravessando dias e expiração. Criação/liberação de bloqueios integrada; sem drag-and-drop ou reservas fictícias. ADR-0013.
- [ ] **MVP-045 (parcial):** expediente/fuso, bloqueios e HOLDs coordenados pelo lock do tenant; ocupação temporária vigente impede alteração de expediente/fuso. Corridas testadas. Ampliar coordenação para estados de reservas confirmadas/reagendamento quando MVP-050+ existir.

**Saida:** horarios publicos confiaveis e calendario administrativo funcional.

## F5 - Reserva publica, quota e consulta segura

**Objetivo:** cliente cria uma solicitacao privada sem conta permanente.

- [x] **MVP-050:** fluxo público serviço → data → horário → nome/email → verificação → revisão, com disponibilidade real. Não cria reserva até MVP-052/053/054.
- [x] **MVP-051:** token de uso único com hash, validade de 15 min, respostas genéricas, reenvio/revogação, outbox e sessão de revisão restrita ao slug. V012/ADR-0015.
- [x] **MVP-052:** reserva temporária, snapshots, alocação, quota provisória e outbox na mesma transação (V013, ADR-0016).
- [x] **MVP-053:** idempotência por tenant/ator/operação e hash da seleção/revisão; repetição devolve recibo original, conteúdo divergente retorna 409.
- [ ] **MVP-054 (mecanismo implementado):** quota mensal/provisória pelo plano vigente, liberada na expiração. Falta definir números comerciais (atualmente null = ilimitado); futura conferência não deve bloquear recebimento por limite.
- [x] **MVP-055:** Meus agendamentos com lista paginada, detalhe, histórico e prazo; link único de email/sessão restrita. Consulta preservada após retirada da página/bloqueio de assinatura (V014/ADR-0017).
- [ ] **Ajuste posterior solicitado em 29/09:** usar nome, sobrenome e telefone, sem exigir email, para agendar e acessar reservas. Definir verificação pelo telefone, migração e recuperação. Adiado explicitamente pela responsável; fluxo atual mantido.
- [x] **MVP-056:** expiração síncrona e worker em lotes com lease/token, recuperação e retries; liberação transacional de alocação/quota e evento único. V015/ADR-0018. Futuros estados de pagamento serão integrados em MVP-060+.

**Saida:** cliente recebe reserva `AguardandoPagamento`, horario bloqueado temporariamente e consulta segura.

## F6 - PIX manual e operacao diaria

**Objetivo:** completar reserva, pagamento, conferencia e atendimento.

- [x] **MVP-060 (base):** PaymentIntent transacional, estruturas PaymentTransaction/PaymentEvidence/Refund e PaymentProvider manual. V016/ADR-0019; operações de recebimento/comprovante/devolução dependem de MVP-062+.
- [x] **MVP-061 (interface):** prévia privada com total, entrada, saldo projetado, recebedor, chave PIX, copiar chave e prazo. Pagamento operacional permanece desabilitado até integração de comprovantes/conferência.
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
