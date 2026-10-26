# Agendou - Testes, aceite e evidencias

## 1. Definition of Done por tarefa

- Requisito e criterio de aceite identificados.
- Backend valida entrada, tenant, papel e propriedade.
- Migracao e contrato acompanham mudancas de dados/API.
- UI cobre carregamento, vazio, erro e sucesso no mobile.
- Teste cobre regra real, autorizacao, integridade, concorrencia ou jornada.
- Logs nao expoem dados sensiveis.
- Documentacao ou runbook atualizado quando necessario.

## 2. Testes criticos

### Multi-tenant

- Admin A tentando acessar IDs do Tenant B recebe 404 ou 403 seguro.
- RLS nega acesso sem contexto.
- Role real da aplicacao e usada nos testes.
- Arquivo privado de um tenant nao e baixado por outro.

### Trial e assinatura

- Cadastro cria trial Premium/Top de 7 dias.
- Basico e Intermediario nao aceitam trial gratis.
- Worker expira trial vencido e bloqueia tenant.
- Tenant bloqueado nao recebe novas reservas.
- Tenant bloqueado preserva configuracoes.
- Pagamento confirmado reativa e mantem pagina, servicos e agenda.
- Tentativa de segundo trial automatico e recusada ou exige override auditado.

### Agenda

- Pausas, bloqueios e excecoes prevalecem.
- Buffers cabem integralmente no periodo.
- DST/fuso e limites do dia sao tratados.
- 50 tentativas concorrentes para o mesmo slot geram uma alocacao ativa.
- Mudanca de expediente nao invalida reserva confirmada silenciosamente.

### Reserva

- Idempotencia retorna mesma resposta para mesma chave/corpo.
- Mesma chave com corpo diferente retorna conflito.
- Token de cliente reutilizado falha.
- Cliente nao consulta reserva alheia.
- Expiracao libera alocacao e quota provisoria sem apagar historico.

### PIX manual

- R$ 99,99 com 50% exige R$ 50,00.
- Upload de comprovante nao confirma.
- Parcial nao confirma.
- Referencia bancaria duplicada nao contabiliza duas vezes.
- Corrida entre expiracao e validacao produz estado unico.
- Pagamento tardio vira pendencia financeira.
- Falha de email nao desfaz confirmacao.

## 3. E2E obrigatorios

1. Profissional cria conta, verifica email, entra em trial e configura pagina.
2. Profissional publica agenda.
3. Cliente reserva, verifica contato, envia comprovante e consulta status.
4. Admin confere PIX e confirma reserva.
5. Trial vence sem pagamento e bloqueia novas reservas.
6. Pagamento de assinatura reativa tenant com configuracoes preservadas.
7. Reagendamento falho preserva reserva original.
8. Super admin suspende e reativa tenant com auditoria.

## 4. Gates antes do piloto

- Duas empresas e dois clientes isolados em endpoints, arquivos e exportacoes.
- Trial Premium/Top validado do cadastro ao bloqueio e reativacao.
- Corrida de 50 solicitacoes nao gera dupla reserva.
- Reserva so confirma com entrada validada suficiente.
- Expiracao, pagamento tardio, devolucao e reagendamento possuem caminho operacional.
- Emails de acesso chegam; falhas sao visiveis e recuperaveis.
- Super admin protegido por MFA.
- Segredos fora do repositorio.
- Backup restaurado e RPO/RTO registrados.
- Alertas e canal de suporte definidos.

## 5. Evidencias esperadas

- Logs de CI.
- Relatorio Playwright.
- Resultado dos testes Testcontainers.
- Evidencia de restore.
- Prints ou videos curtos das jornadas criticas.
- Relatorio de acessibilidade.
- Registro de carga com massa usada.
- ADRs aprovadas para decisoes comerciais e tecnicas.

## Evidência de 22/10/2026 — revisão HTTP e suíte completa

- Backend: 36 unitários + 106 integrações, zero falhas/erros/ignorados, `apps/api/hardening-full-verify.log`. Abrange MinIO real e PostgreSQL com papel runtime/RLS, identidade/MFA, assinatura, agenda e jornada de reserva até pagamento/gestão/recuperação.
- Build de produção/TypeScript aprovado em `apps/web/hardening-final-build.log`.
- `infra/local/verify-http-security.ps1`: sete verificações aprovadas, páginas com política de privacidade e acesso anônimo privado rejeitado diretamente e via proxy. Evidência `.local/hardening-http-check.log`.
- O runner registrou timeout de fechamento da JVM após os testes concluídos; Maven passou. Revisar encerramento dos contextos Spring/Hikari nos testes. Não repetir a suíte completa sem mudança/falha adicional apenas para esconder esse aviso.
- Esta rodada NÃO valida navegabilidade autenticada, acessibilidade, carga, restore, exportação/exclusão, TLS/ingress ou entrega externa de mensagens. Gates de piloto permanecem abertos.


## Ensaio de recuperação — 22/10/2026

`BackupRestoreIT` aprovado: esquema completo até V026, dois tenants, um cliente, dump PostgreSQL e um PNG privado restaurados em contêineres isolados. Verifica Flyway, RLS, permissões runtime, hashes e recusa de acesso anônimo; simula perda de uma gravação após o snapshot. Mesmo cenário executado por Surefire e Failsafe, sem falhas (um teste distinto). Log `apps/api/backup-restore-verify.log`; medição no `target/backup-restore-report.txt`: backup 341 ms, recuperação de infraestrutura/verificações 4.003 ms. Não inclui aplicação restaurada, vínculo reserva/comprovante, segredos ou volume realista. MVP-073 parcial; procedimento e próximos critérios em [backup e restauração](runbooks/backup-e-restauracao.md).


## Regressão integrada e recuperação de reserva — 22/10/2026

Resultado final: **144 testes distintos aprovados**, 36 unitários e 108 integrações, sem falhas/erros/ignorados; `apps/api/integration-regression-final.log`, BUILD SUCCESS às 19:45. `BookingRecoveryIT` restaura banco e inventário de comprovantes, inicia outra API e verifica código/sessão/download por HTTP e confirmação administrativa por MockMvc, com PostgreSQL/MinIO reais. Dados sintéticos: dois tenants, uma reserva e um comprovante vinculado. Backup 370 ms; recuperação, inicialização e jornada 9.074 ms. Não é medição de produção.

`@DirtiesContext(AFTER_CLASS)` nas jornadas fecha recursos pelo ciclo do Spring. A regressão final não repetiu os erros de tarefas em bancos removidos nem o término forçado da JVM. Uma tentativa intermediária de fechar o contexto manualmente foi interrompida por conflito com callbacks e corrigida; não conta como execução aprovada.

TypeScript e sete verificações HTTP locais também passaram (`apps/web/integration-typecheck.log`, `.local/integration-http-check.log`). Build frontend não reexecutado; E2E visual/teclado/leitor/carga continuam pendentes. Consultar a [matriz de testes](matriz-testes-automatizados.md) para cobertura e limites. Próximo incremento: automação de navegador para as jornadas principais.


## Evidência de navegador em 26/10/2026

`npm run test:e2e` aprovado: dez cenários Chromium (desktop + Pixel 7 emulado), build de produção/TypeScript e conferência final no banco. Jornadas abrangem reserva pública, consulta por código, comprovante, confirmação, cancelamento/devolução, login/teclado/temas, navegação, clientes, recuperação genérica e acesso negado à plataforma. Axe passou nas telas selecionadas; corrigidos contraste no tema claro e rolagem do menu. Todos os 40 pedidos de leitura pública do ensaio limitado retornaram 200.

Runbook: `runbooks/testes-navegador.md`. Relatórios em `apps/web/playwright-report` e `test-results`; resultado final em `apps/web/browser-validation.log` (08:55). A preparação usa contas sintéticas e infraestrutura descartável. Não valida transferências/mensagens reais, carga de produção, leitor de tela humano, aparelhos físicos ou todos os fluxos administrativos no navegador. Gates de privacidade, segurança/backup operacionais e piloto continuam abertos.

Regressão completa backend de 26/10 também aprovada: 144 testes (36 unitários + 108 integrações), zero falhas/erros/ignorados; `apps/api/regression-20261026.log`, BUILD SUCCESS às 09:00.
