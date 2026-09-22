# Agendou - MVP para SaaS de agendamento

**Versao:** 1.0  
**Data:** 22/09/2026  
**Origem:** `Roadmap_MVP_Agendou.md`, versao 1.0, com ajuste comercial solicitado para teste gratis.  
**Objetivo:** construir um SaaS responsivo e moderno para profissionais publicarem agenda, receberem reservas, conferirem pagamento via PIX manual e operarem clientes com isolamento multi-tenant.

## 1. Resultado esperado

Um profissional cria conta, entra em teste gratis de 7 dias do plano Premium/Top, configura sua pagina, servicos, PIX, disponibilidade e politica. Um cliente acessa a pagina publica, escolhe servico e horario, valida contato, envia comprovante PIX de pelo menos 50% e acompanha a reserva. O profissional confere manualmente o PIX e confirma, cancela, reagenda, conclui ou marca falta com historico e auditoria.

Ao final do teste gratis, se o profissional nao pagar, o tenant fica bloqueado para novas reservas e administracao ativa. A pagina, configuracoes, servicos, reservas, comprovantes e historico permanecem preservados. Quando pagar, o tenant e reativado e continua exatamente com a pagina e os dados ja configurados.

## 2. Decisoes do MVP

1. **Stack:** Java 21 LTS, Spring Boot, PostgreSQL, Next.js, React, TypeScript e Tailwind CSS.
2. **Arquitetura:** monolito modular no backend, API e worker no mesmo projeto Java, frontend separado em Next.js.
3. **Multi-tenant:** `tenant_id`, FKs compostas, RLS, roles separadas e contexto transacional obrigatorio.
4. **Pagamento da reserva:** PIX manual no MVP. Upload de comprovante nao confirma pagamento.
5. **Pagamento da assinatura:** o MVP registra status, vigencia, bloqueio e reativacao. A cobranca pode ser administrativa no inicio, sem gateway recorrente obrigatorio.
6. **Teste gratis:** apenas o plano Premium/Top possui teste gratis de 7 dias.
7. **Fim do teste gratis:** sem pagamento confirmado, tenant vai para `TRIAL_EXPIRED_BLOCKED`.
8. **Preservacao:** tenant bloqueado nao perde pagina, servicos, disponibilidade, clientes, reservas ou arquivos.
9. **Plano pos-pagamento:** ao pagar, o usuario continua com tudo configurado e o sistema muda para plano pago ativo.
10. **Escopo de validacao:** piloto com 5 a 10 profissionais antes de ampliar aquisicao.

## 3. Planos e trial

| Plano | Trial gratis | Uso no MVP | Observacao |
|---|---:|---|---|
| Basico | Nao | Pode existir como plano pago inferior | Nao recebe trial. |
| Intermediario | Nao | Pode ficar cadastrado para evolucao comercial | Nao recebe trial. |
| Premium/Top | Sim, 7 dias | Plano de entrada do teste gratis | Unico plano liberado para teste. |

### 3.1 Estados de assinatura

| Estado | Significado | Permissoes |
|---|---|---|
| `TRIAL_ACTIVE` | Teste gratis Premium/Top em andamento | Configurar pagina, publicar, receber reservas e operar normalmente. |
| `TRIAL_EXPIRING` | Ultimos 2 dias do trial | Operacao normal com aviso persistente de vencimento. |
| `TRIAL_EXPIRED_BLOCKED` | Trial venceu sem pagamento | Bloqueia novas reservas, publicacao e acoes administrativas de operacao. Mantem login limitado para pagar e consultar aviso. |
| `PAID_ACTIVE` | Assinatura paga ativa | Operacao normal no plano contratado. |
| `PAST_DUE` | Pagamento vencido em plano pago | Politica de carencia definida pelo produto; nao confundir com trial. |
| `SUSPENDED` | Suspensao administrativa | Consulta preservada, novas reservas bloqueadas. |
| `CANCELED` | Cancelado pelo cliente ou plataforma | Dados preservados conforme politica de retencao. |

### 3.2 Regras do trial

- O trial inicia na criacao do tenant profissional ou na primeira ativacao do workspace, conforme decisao de produto registrada em ADR.
- O trial dura 7 dias corridos, calculados por relogio confiavel do servidor.
- Trial nao pode ser reiniciado para o mesmo email, documento, tenant, telefone ou dominio sem decisao administrativa auditada.
- Durante o trial, o profissional usa recursos do Premium/Top liberados no MVP.
- Ao vencer sem pagamento, o tenant nao e excluido e a pagina nao e apagada.
- A pagina publica deve mostrar indisponibilidade para novas reservas quando bloqueada.
- Reservas ja confirmadas antes do bloqueio permanecem consultaveis e operaveis conforme politica minima.
- Comprovantes e pendencias financeiras permanecem preservados.
- Ao confirmar pagamento, o sistema reativa o tenant e preserva todo o conteudo configurado.

## 4. Escopo do MVP

### Dentro do MVP

- Cadastro, login, verificacao de email, recuperacao e sessao segura.
- Trial Premium/Top de 7 dias, bloqueio por vencimento e reativacao por pagamento.
- Perfil publico com slug, logo, descricao, local/modalidade, fuso e contato.
- Catalogo de servicos, preco, duracao, buffers e entrada minima de 50%.
- Configuracao PIX manual com snapshot por reserva.
- Disponibilidade semanal, pausas, excecoes, bloqueios e calendario semanal.
- Reserva publica com contato verificado, idempotencia, quota, alocacao temporaria e expiracao.
- Upload e conferencia manual de comprovante PIX.
- Cancelamento, reagendamento, conclusao, falta, devolucao manual e historico.
- Super admin para tenants, assinatura, bloqueio, reativacao, auditoria e suporte operacional.
- Emails transacionais essenciais.
- Runbooks de deploy, restore, incidentes, pagamento tardio e privacidade.

### Fora do MVP

- PIX por API, PSP, boleto ou cartao recorrente automatizado.
- WhatsApp, SMS, lembretes programados e calendario externo.
- Multiplos profissionais por tenant.
- Dashboards avancados, financeiro consolidado, campanhas, recorrencia e lista de espera.
- Aplicativo nativo.
- Impersonacao de super admin.

## 5. Estrutura de arquivos proposta

| Arquivo | Uso |
|---|---|
| `mvp.md` | Visao executiva e contrato do MVP. |
| `docs/backlog.md` | Backlog faseado de desenvolvimento. |
| `docs/sdd-rules.md` | Regras obrigatorias herdadas do SDD e decisoes do MVP. |
| `docs/architecture.md` | Arquitetura, modulos, deploy e estrutura de repositorio. |
| `docs/data-model.md` | Entidades, estados e migracoes. |
| `docs/api-contract.md` | Endpoints, contratos e erros. |
| `docs/ui-ux-design.md` | Experiencia responsiva, telas e design system. |
| `docs/testing-and-acceptance.md` | Testes, DoD, gates e evidencias. |
| `docs/ai-development-playbook.md` | Passo a passo para desenvolver com IA. |
| `docs/brand.md` | Marca, cores, tipografia e uso do logo. |
| `assets/agendou-logo.svg` | Logomarca vetorial do Agendou. |

## 6. Sequencia de implementacao

1. **F0 - Preparacao:** ADRs, setup, OpenAPI inicial, prototipo responsivo e ambiente local.
2. **F1 - Fundacao:** identidade, tenant, RLS, sessoes, email base, deploy homologacao.
3. **F2 - Trial e billing:** plano Premium/Top, trial de 7 dias, bloqueio, reativacao e super admin de assinatura.
4. **F3 - Perfil e catalogo:** pagina publica, servicos, logo, PIX e publicacao controlada.
5. **F4 - Disponibilidade:** regras de agenda, slots, calendario, constraint de sobreposicao e locks.
6. **F5 - Reserva publica:** contato verificado, idempotencia, reserva temporaria, quota e consulta segura.
7. **F6 - PIX manual e operacao:** comprovante, conferencia, confirmacao, cancelamento, reagendamento e historico.
8. **F7 - Homologacao:** privacidade, seguranca, carga, restore, acessibilidade, runbooks e piloto.
9. **F8 - Producao e piloto:** deploy versionado, primeiros profissionais, monitoramento e decisao de continuidade.

## 7. Criterios de aceite principais

- Tenant A nao acessa dados, arquivos ou reservas do Tenant B.
- Trial gratis so pode ser criado no plano Premium/Top.
- Trial vencido sem pagamento bloqueia novas reservas e preserva configuracoes.
- Pagamento de assinatura reativa tenant preservando pagina, servicos e historico.
- Reserva so confirma com entrada PIX validada manualmente e suficiente.
- Upload de comprovante nunca confirma sozinho.
- Corrida de 50 tentativas para o mesmo horario gera no maximo uma alocacao ativa.
- Cliente nao consulta reserva alheia por ID.
- Falha de email nao desfaz confirmacao financeira.
- Restore de banco e arquivos e testado antes do piloto.

## 8. Proxima acao

Executar o playbook em `docs/ai-development-playbook.md`, comecando pela criacao do repositorio, ADRs, wrappers, Compose, primeira migration e teste de isolamento entre dois tenants.
