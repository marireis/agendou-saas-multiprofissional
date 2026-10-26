# Agendou - Status de implementação

**Atualizado em:** 26/10/2026. Desenvolvimento na própria pasta do projeto.

## Entrega em 26/10: jornadas automatizadas no navegador (MVP-074 parcial)

- **10 cenários Playwright aprovados**, cinco em Chromium desktop e cinco em emulação Pixel 7, sem falhas/ignorados na execução final. `npm run test:e2e`, BUILD SUCCESS às 08:55 em `apps/web/browser-validation.log`; build de produção Next.js/TypeScript e resultados em `apps/api/target/browser-e2e.log`. Harness Java também valida no banco duas reservas canceladas, dois comprovantes, dois recebimentos e duas devoluções. Seus testes unitários repetidos não são contados como testes novos.
- Jornadas cobrem login válido/inválido, teclado, logout, proteção de área privada, persistência de tema, reserva pública sem email, código inválido/válido em nova sessão, upload/download de comprovante, recebimento/confirmação, cancelamento/devolução e consulta atualizada. Também navegam nas 12 abas administrativas, cadastram/editam cliente sem email, verificam resposta genérica de recuperação e negam plataforma a profissional comum.
- API, PostgreSQL e MinIO reais em portas/contêineres temporários; frontend de produção separado em `.next-e2e`. Dados/volumes locais e chave MFA preservados. Execução reproduzível documentada em `runbooks/testes-navegador.md`.
- Axe sem violações WCAG A/AA nas telas selecionadas: entrada nos dois temas, painel, recibo e cadastro. Corrigidos contraste do nome do negócio no tema claro e menu lateral sem rolagem que tornava abas inacessíveis em telas baixas. Falhas intermediárias de seletores/sincronização da automação foram corrigidas; anúncio de navegação do Next.js não é erro funcional.
- Next.js atualizado para 16.4.3, dependências compatíveis corrigidas e auditoria npm de 25/10 com **zero vulnerabilidades reportadas**, evidência `.local/20261025-dependency-audit-final.json`. Não equivale a uma auditoria completa de segurança.
- Dois ensaios limitados de leitura pública (20 requisições, concorrência 4 por projeto), todos HTTP 200, medições anexadas ao relatório Playwright. Não é teste de capacidade/SLO de produção. Mobile é emulado; Safari/aparelho físico, leitor de tela humano, MFA positivo e recuperação completa pelo navegador ainda não cobertos por esta suíte (os dois últimos têm integrações backend).
- **Regressão completa do backend aprovada novamente em 26/10: 144 testes (36 unitários + 108 integrações), zero falhas/erros/ignorados**, BUILD SUCCESS às 09:00, 3min59s, em `apps/api/regression-20261026.log`. Inclui identidade/MFA, reservas, pagamentos, RLS, concorrência e recuperação.
- Ambiente local iniciado em 26/10: Docker/PostgreSQL/MinIO/Mailpit, API 8080 e frontend dev 3000. `/entrar` e CSRF pelo proxy HTTP 200; `/api/v1/health` UP direto e pelo proxy. Sete verificações HTTP de cabeçalhos/recusa de financeiro anônimo aprovadas (`.local/http-check-20261026.log`). Logs: `apps/api/local-preview-api.log`, `apps/web/local-preview-web.log`. Dados e chave MFA existentes preservados.
- **Próximos passos:** teste manual da responsável no ambiente local; depois privacidade operacional (MVP-071: exportação/exclusão/retenção), completar CSP/ingress, backups/segredos/cópia externa, runbooks de operação e homologação representativa. Produção/piloto, hospedagem/domínio, provedor WhatsApp/SMS e números comerciais dos planos permanecem pendentes por decisão da responsável. Não considerar todas as tarefas do projeto concluídas.

## Entrega em 22/10: reserva restaurada e regressão automatizada completa

- `BookingRecoveryIT` cria uma reserva sem email, autentica o cliente por código e envia comprovante PNG por HTTP para MinIO real. Encerra a aplicação de origem, salva dump e inventário de `payment_evidence` (chave/tipo/tamanho/SHA-256), restaura em outros contêineres e inicia uma nova API. Dados/volumes locais preservados.
- API restaurada valida o código original e recusa código incorreto; consulta a reserva e baixa os mesmos bytes privados. Confere vínculo comprovante → intenção de pagamento → reserva, detecta arquivo ausente/corrompido e nega download anônimo/acesso de outro profissional. Recebimento administrativo confirma a reserva e compromete a quota. Cliente usa HTTP real com CSRF/cookies; preparação e conferência administrativas usam MockMvc com identidade sintética, serviços/banco/armazenamento reais.
- **Regressão final: 144 testes distintos aprovados (36 unitários + 108 integrações), zero falhas, erros ou ignorados**, `mvnw.cmd verify`, BUILD SUCCESS em `apps/api/integration-regression-final.log` (22/10 às 19:45, 4min41s). Cobre identidade, recuperação de senha, MFA/superadmin, assinatura/trial, perfil, catálogo, agenda, reservas, clientes, pagamentos, cancelamento/reagendamento/conclusão/falta, financeiro, notificações, códigos, RLS, concorrência e armazenamento/restauração. Matriz detalhada em `docs/matriz-testes-automatizados.md`.
- Primeira regressão também passou em `integration-full-verify.log`, mas revelou tarefas Spring tentando usar bancos temporários encerrados. Jornadas agora herdam `IsolatedSpringIntegrationTest` com `@DirtiesContext(AFTER_CLASS)`, fechando pools/agendadores via Spring e removendo contextos do cache. Tentativa intermediária de fechamento manual conflitava com callbacks e foi interrompida (`integration-final-verify.log`); substituída pelo fechamento gerenciado, validado isoladamente e na regressão final. Não reapareceram erros de tarefas contra bancos removidos nem término forçado da JVM na execução final. Avisos esperados dos testes de falha SMTP e de dependências permanecem distintos de falhas funcionais.
- TypeScript aprovado (`apps/web/integration-typecheck.log`) e sete verificações HTTP do ambiente local aprovadas (`.local/integration-http-check.log`), incluindo recusa de financeiro anônimo pela API/proxy. O build do frontend não foi reexecutado nesta etapa; a entrega altera testes/documentação, e o frontend local continua servindo o build existente.
- Medição final sintética: backup **370 ms**, restauração/inicialização da API/jornada **9.074 ms**. Relatório `apps/api/target/booking-recovery-report.txt`. Não equivale a RPO/RTO de produção, nem valida recuperação da chave MFA, cópia externa ou carga realista. MVP-073 permanece parcial quanto à operação de produção.
- **Próxima etapa: automatizar as jornadas no navegador, em desktop e celular (MVP-074)**, começando por login, reserva pública, consulta por código e conferência administrativa. A suíte atual confirma os cenários de backend cobertos; não certifica todas as interações visuais, acessibilidade ou carga. Depois concluir privacidade/exportação/exclusão (MVP-071), backups/segredos operacionais, configuração de produção e piloto. Provedor de mensagens e limites comerciais continuam pendentes.

## Entrega em 22/10: ensaio isolado de backup e restauração (MVP-073 parcial)

- Novo `BackupRestoreIT` cria quatro contêineres temporários, aplica as 26 migrations e restaura dump PostgreSQL e PNG privado em destinos vazios. Não acessa os dados/volumes do ambiente local. Dois tenants, um cliente e um arquivo sintéticos; nenhuma alteração de interface nesta etapa.
- Verificados: validação Flyway, contagem de políticas/FORCE RLS/funções privilegiadas, permissões runtime e isolamento entre tenants, SHA-256 do dump/arquivo, bytes restaurados e recusa de acesso anônimo. Roles globais são provisionadas separadamente; proprietários/ACL do dump preservados.
- Ensaio aprovado sem falhas/erros/ignorados, BUILD SUCCESS em `apps/api/backup-restore-verify.log`. O mesmo cenário executou duas vezes (Surefire e Failsafe por seleção explícita); não são dois testes distintos nem nova execução da suíte completa. Relatório da execução Failsafe: `apps/api/target/backup-restore-report.txt`.
- Medição de 22/10: backup **341 ms**, inicialização/restauração dos destinos e verificações **4.003 ms**; dump 154.051 bytes e PNG 71 bytes. Snapshot tinha 359 ms no incidente simulado; uma gravação posterior ficou intencionalmente fora da recuperação. São medidas de infraestrutura sintética, não RPO/RTO de produção ou da aplicação completa.
- Runbook `docs/runbooks/backup-e-restauracao.md` documenta execução, consistência banco/objetos, roles, chaves MFA, limites e critérios antes de liberar tráfego. MVP-073 continua parcial: faltam inventário completo de comprovantes, vínculo reserva/pagamento/arquivo e jornada na API restaurada, recuperação de segredos, cópia externa e volume representativo.
- **Próxima etapa: ampliar a recuperação para uma reserva com comprovante vinculado e validar a API restaurada**, antes de considerar MVP-073 concluído. Depois retomar privacidade operacional (MVP-071), homologação autenticada/acessibilidade/carga, configuração de produção e piloto. Limites comerciais e provedor de mensagens seguem pendentes.

## Entrega em 22/10: primeira rodada de homologação e proteção HTTP

- Suíte completa `mvnw.cmd verify` aprovada: **142 testes** (36 unitários + 106 integrações), incluindo identidade, recuperação, MFA/superadmin, assinatura/trial, RLS, catálogo, agenda, reservas, pagamentos, financeiro, notificações, reemissão e MinIO real. Log `apps/api/hardening-full-verify.log`. Nenhum teste falhou ou foi ignorado. Isso não substitui E2E autenticado de navegador.
- Páginas recebem Referrer-Policy no-referrer, X-Frame-Options DENY, nosniff, Permissions-Policy e CSP com base-uri/object-src/frame-ancestors/form-action. Cabeçalho X-Powered-By desabilitado. CSP **parcial**, sem restrição de scripts/styles nesta etapa; nonce/hash e validação de hidratação permanecem pendentes. API recebe no-referrer e DENY, preservando sandbox dos comprovantes.
- Novo teste cobre cabeçalhos em sucesso, 401, CSRF negado e rate limit. Novo script `infra/local/verify-http-security.ps1` verifica páginas e acesso privado sem sessão na API e pelo proxy. Sete verificações HTTP aprovadas contra build de produção local; evidência `.local/hardening-http-check.log`.
- Build/TypeScript aprovados em `apps/web/hardening-final-build.log`. Primeira execução detectou tipos gerados corrompidos em `.next/dev`; pasta preservada em `.local/next-before-hardening-20261022` e regenerada. API reiniciada e frontend executando o build de produção com `npm run start`, portas 8080/3000, dados/MFA preservados. Para editar com atualização automática, parar esse frontend e iniciar `npm run dev`.
- Observação de infraestrutura de testes: após os testes aprovados, a JVM de testes excedeu 30 segundos no encerramento dos contextos Spring/Hikari e foi terminada pelo runner. Maven retornou BUILD SUCCESS. Dump em `apps/api/target/failsafe-reports/2026-10-22T08-47-07_067-jvmRun1.dump`; ciclo de fechamento dos recursos de teste ainda deve ser ajustado, sem tratar isso como falha funcional ou omitir o aviso.
- Runbook `docs/runbooks/privacidade-e-seguranca.md` descreve atendimento verificado, correção cadastral e requisitos para exportação/exclusão. **MVP-071 não concluído:** exportação/exclusão operacional e política de retenção não implementadas. MVP-072/074 parciais: revisão técnica/testes API feitos, ainda faltam CSP completa, ingress/IP real, E2E navegador, acessibilidade e carga representativa.
- **Próxima etapa: backup/restauração em ambiente isolado (MVP-073)**, medindo recuperação do banco e comprovantes sem sobrescrever o ambiente local. Depois concluir privacidade operacional, homologação de navegador, configuração de produção e piloto. Limites comerciais e canal/provedor de mensagens continuam pendentes.

## Entrega em 22/10: reemissão profissional do código de acesso

- Conferência → atendimento → Acesso do cliente permite consultar dados originais, confirmar a verificação de identidade, informar motivo e emitir outro código. Conhecer nome/telefone não basta: a verificação é responsabilidade do profissional por contato confiável ou presencialmente. A aplicação registra sua declaração, sem verificação externa automática.
- ADMIN/tenant da sessão, CSRF e assinatura operacional exigidos para emitir. Consulta preservada após bloqueio. Versão impede duas emissões simultâneas/repetidas; intervalo mínimo de 60 segundos. V026 grava rotação/auditoria na mesma transação, com FKs compostas, FORCE RLS e operador/correlation ID. Falha de auditoria preserva o código anterior.
- Novo segredo aleatório entregue uma vez na resposta no-store; somente hash persistido no domínio. Tela mantém o novo código em memória por até cinco minutos, permite copiar/ocultar e orienta entrega manual ao cliente verificado. Resposta perdida não pode ser recuperada: consultar versão e emitir novamente com nova confirmação/intervalo. Sem mensagens externas.
- Código anterior deixa de autorizar consultas, inclusive de sessões já abertas, recibo original, PIX e comprovantes. Repetição da criação pública não devolve código revogado. Não apaga dados já baixados. Nome/telefone originais, reserva, horário e pagamentos permanecem iguais. Legados por email usam recuperação por link.
- ADR-0028, contrato OpenAPI 0.28.0, modelo de dados e runbook atualizados. Retomada corrigiu a expectativa de um teste (400 → 422, padrão de validação da API), sem reduzir a exigência de confirmação. Build atual aprovado em `code-reissue-final-build.log`; OpenAPI validado como YAML. **87 testes aprovados** (36 unitários + 51 integrações da jornada pública) em `apps/api/code-reissue-final-verify.log`, incluindo três testes de reemissão: revogação de sessões/recibo/PIX/comprovantes e repetição da criação, isolamento, CSRF, confirmação, intervalo, versão, concorrência e rollback. V026 aplicada no banco local com dados/chave MFA preservados; API e frontend iniciados (`code-reissue-local-api.log`, `code-reissue-local-web.log`). Homologação visual autenticada pendente.
- **Próxima etapa: homologação integrada das jornadas e revisão de privacidade/segurança (MVP-071/072/074)**. Limites comerciais dos planos ainda precisam de números definidos; WhatsApp/SMS externo depende de canal/provedor. Backup/restore e publicação/piloto continuam pendentes.

## Entrega em 13/10: Financeiro do profissional (MVP-069)

- Aba `/painel/financeiro` com período inclusivo no fuso atual do perfil, até 366 dias, visões Movimentações/Pendências, páginas de 50 itens e acesso ao atendimento na Conferência. Mês inicial do dispositivo; período/fuso aplicados explícitos. Layout responsivo usando o tema existente.
- Recebimentos e devoluções pela data efetiva informada de cada lançamento; líquido pode ser negativo quando a devolução pertence a recebimento anterior. Totais abrangem todo o período, independentemente da página, sem duplicar créditos ao juntar devoluções.
- Pendências selecionadas pela data atual do atendimento, considerando todo seu histórico financeiro. Entrada pendente é parte do restante previsto. Canceladas/expiradas não geram saldo a receber. Cancelamentos mostram devolução conforme valor retido; créditos em expiradas e excedentes pedem avaliação. Conclusão/falta não quitam o saldo. Expiração vencida é reconhecida mesmo antes do worker.
- API de consulta exige ADMIN/tenant, usa RLS, no-store e REPEATABLE READ para resumo/página consistentes; disponível com assinatura bloqueada. Sem novas migrations, lançamentos, transferências ou alterações na assinatura. Valores derivados dos registros existentes; não é saldo bancário/lucro nem controle de despesas. ADR-0027/OpenAPI 0.27.0 atualizados.
- Validação: **84 testes distintos aprovados** (36 unitários + 47 integrações da jornada em `finance-verify.log`, mais um cenário adicional de paginação em `finance-pagination-verify.log`). Cobrem somas, devoluções parciais, limites de data/fuso, filtro, isolamento, assinatura bloqueada, vencimento, pendências após conclusão/falta e paginação. Build final com TypeScript aprovado (`finance-final-build.log`); primeira tentativa encontrou arquivo de build bloqueado, resolvido ao parar/reiniciar o frontend. OpenAPI validado como YAML. Homologação visual autenticada pendente.
- API/frontend reiniciados para teste local, dados/MFA preservados (`finance-local-api.log`, `finance-local-web.log`). Não houve mudança de esquema nesta etapa.
- **Próxima etapa: recuperação/reemissão segura do código de acesso**, com procedimento profissional, invalidação do código anterior e auditoria. Depois definir limites comerciais dos planos e concluir homologação, privacidade, backup/restore e publicação. Mensagens externas MVP-068 continuam dependendo de canal/provedor.

## Entrega em 13/10: central de notificações internas (MVP-068, primeira etapa)

- Aba `/painel/notificacoes`: eventos de solicitação, comprovante recebido, confirmação, reagendamento, expiração, cancelamento, conclusão/falta, recebimento e devolução. Inclui histórico existente e reservas antigas por email; reserva assistida não duplica o aviso de solicitação.
- Filtro de não lidas, contagem individual, marcação idempotente e paginação de 50 eventos por cursor data/id. Botão Ver atendimento abre a reserva na Conferência. Atualiza ao abrir, filtrar ou clicar em Atualizar; sem push/polling. Horário exibido é o atual da reserva, não um snapshot do horário no instante do evento.
- V025 registra leitura por administrador com FKs compostas e FORCE RLS. ADMIN/tenant da sessão, CSRF e no-store; leitura não altera reserva, pagamento ou leitura de outro administrador. Consulta preservada com assinatura bloqueada. Sem exposição de telefone, email, código privado ou PIX na resposta dos avisos.
- Cliente continua consultando o portal privado. **MVP-068 permanece parcial quanto a mensagens externas:** não houve integração/envio WhatsApp/SMS nem ampliação dos emails legados; depende de canal/provedor e regras de entrega. Recuperação do código também permanece pendente. ADR-0026, OpenAPI 0.26.0 e modelo de dados atualizados.
- Durante a regressão, três testes existentes rejeitaram recebimento imediato por diferença entre relógios do banco e da aplicação. Criação da reserva passou a usar o mesmo Clock da aplicação utilizado na validação do recebimento, com precisão de microssegundos. Regressão reexecutada e aprovada; não flexibilizada a validação de datas.
- Validação desta entrega: build final com TypeScript aprovado (`notifications-final-build.log`); typecheck isolado aprovado (`notifications-typecheck.log`); OpenAPI carregado como YAML. **81 testes aprovados** (36 unitários + 45 de integração da jornada pública, incluindo dois novos de notificações) em `apps/api/notifications-final-verify.log`. V025 aplicada no banco local com dados/MFA preservados; API health UP e página `/painel/notificacoes` HTTP 200. API e frontend iniciados para teste (`notifications-local-api.log` / `notifications-local-web.log`). Homologação visual autenticada desta nova aba pendente.
- **Próxima implementação: aba Financeiro (MVP-069)**, consolidando recebimentos, saldos pendentes e devoluções por período. Retomar mensagens externas de MVP-068 quando canal e provedor forem definidos.

## Entrega em 09/10: acabamento e validação visual do agendamento público

- Último trabalho: melhoria visual do agendamento e de Meus agendamentos, mantendo o fluxo sem login/email e consulta por código. Cabeçalho com marca, cartões de serviços, indicador de etapas, resumo lateral e adaptação para celular implementados. Build com TypeScript aprovado em `apps/web/booking-design-build.log`.
- Validação pelo navegador concluída em 09/10 com conta e contatos sintéticos locais: serviço → calendário → horário → nome/sobrenome/telefone → revisão → reserva → recibo → consulta privada. Conferidos desktop escuro, calendário/contato no celular escuro e recibo/portal no celular claro. Código incorreto recusado e código correto liberando somente a reserva correspondente. A assinatura da conta real, que havia bloqueado a conferência em 06/10, não foi alterada.
- Melhorias desta etapa: foco e rolagem para revisão/recibo, respeitando redução de movimento; confirmação visível e anunciada ao copiar o código; resumo de data/hora usando o fuso preservado na revisão/reserva. Não houve pagamento nem mensagem externa real.
- Docker/PostgreSQL/Mailpit/MinIO, API 8080 e frontend 3000 iniciados para teste local. Build de produção e typecheck aprovados em `apps/web/visual-qa-build.log` e `apps/web/visual-qa-typecheck-final.log`; typecheck isolado não concluiu e foi reexecutado fora do isolamento. Captura local do portal em `.local/visual-qa-portal.jpg` (dados sintéticos, arquivo não versionado). Esta verificação não substitui homologação administrativa, auditoria de acessibilidade ou E2E automatizado completo.
- Os 88 testes aprovados da entrega funcional anterior continuam registrados abaixo; não foram reexecutados para o ajuste visual. Não houve nova mudança de backend nesta etapa.
- Próxima etapa: revisar/implementar notificações MVP-068 para o modelo sem email, definindo o escopo de avisos internos e comunicação manual enquanto não houver provedor de mensagens; depois implementar Financeiro MVP-069; concluir privacidade, segurança, backup/restore, testes completos e publicação/piloto.
- Pendências de produto: recuperação/reemissão de código ainda não implementada; limites mensais comerciais ainda não definidos. WhatsApp/SMS automático exige decisão de canal e integração; nenhum provedor está configurado.

## Entrega em 06/10: agendamento público sem email, código privado e Clientes

- Página e reserva públicas, sem conta/login: serviço → data → horário → nome, sobrenome e telefone com DDD → revisão. O pedido de substituir email deixou de estar adiado e foi implementado conforme escolha da responsável. Contato brasileiro validado/normalizado; aceite explícito de valores/política.
- Recibo entrega código privado aleatório por reserva. Meus agendamentos pede os mesmos dados e esse código, com sessão de uma hora, CSRF, no-store e limites de tentativas. Código só libera a reserva correspondente, inclusive PIX/comprovantes; conhecer o telefone não libera histórico de terceiros. Banco de domínio guarda hash; segredo em claro fica temporariamente na sessão para recuperar o recibo. É preciso copiar/guardar; não há reemissão automática.
- Clientes em `/painel/clientes`: cadastro, busca, edição versionada e histórico. Cadastro sem email exige telefone. Reserva assistida compartilha as regras de disponibilidade/quota/PIX e entrega código para repassar ao cliente quando não há email. Reserva continua temporária até conferência do profissional. MVP-067/MVP-070 concluídos; V023/V024 e ADR-0025.
- Reservas antigas por email preservadas, com opção separada de acesso no portal. Sem mescla automática com cadastros por telefone. Alteração administrativa de contato não muda os dados originais exigidos pelo código de uma reserva. Não há envio WhatsApp/SMS ou email para reservas sem email.
- Validação: **88 testes aprovados** (36 unitários + 43 jornada + 9 calendário), build e typecheck aprovados. `phone-booking-final-verify.log`, `phone-booking-build.log`, `phone-booking-typecheck.log`. Expectativa do teste pendente de rollback corrigida; rollback validado. Primeira execução dos novos testes ajustada para usar o cookie Spring Session real, sem mudança na autenticação de produção. OpenAPI 0.25.0 carregado como YAML.
- V024 aplicada localmente com dados/chave MFA preservados. API health UP; páginas públicas de agendamento e consulta HTTP 200 sem login. `phone-booking-local.log` e `local-test-web.log`. Homologação visual/interativa pela responsável ainda pendente; testes automatizados usam contatos sintéticos.
- **Próxima tarefa:** revisar/implementar notificações MVP-068 considerando reservas sem email e ausência de provedor WhatsApp/SMS; depois aba Financeiro MVP-069. Aguardar teste da responsável antes de seguir para novas funcionalidades.

### Histórico do ponto de retomada anterior em 06/10


- **Mudança de prioridade solicitada em 06/10:** substituir email obrigatório no agendamento público por nome, sobrenome e telefone com DDD, sem conta/login. Meus agendamentos deverá usar esses dados de contato; mecanismo adicional de confirmação de acesso em definição com a responsável (código WhatsApp/SMS ou código entregue na reserva), para não expor histórico a terceiros que conheçam o telefone. O pedido de telefone deixa de estar adiado; referências anteriores preservam o histórico. Ainda não implementado. Adequar também cadastro administrativo, vínculo das reservas existentes e plano de notificações. A página `/a/{slug}` e a disponibilidade já permitem acesso anônimo no código atual.

- Código da aba `/painel/clientes` e endpoints de cadastro, busca, edição, histórico e reserva assistida implementados na pasta do projeto. V023 adiciona telefone opcional de contato, versão do cadastro e auditoria. Email verificado continua sendo a identificação do portal; alteração para telefone permanece adiada.
- Reserva assistida segue serviço → data → horário → revisão, usa disponibilidade/quota e cria reserva temporária aguardando pagamento. Não confirma automaticamente nem envia novo email nesta etapa.
- Build frontend aprovado em `apps/web/assisted-build.log`. Última execução Maven em `apps/api/assisted-verify.log`: 32 unitários e 48 integrações aprovados; uma integração falhou. O teste de rollback esperava uma exceção propagada, mas o tratamento HTTP devolveu 409. Corrigir a expectativa e executar novamente para verificar as asserções de rollback que ainda não foram alcançadas.
- **Entrega ainda não concluída:** faltam finalizar a validação e atualizar contrato OpenAPI/ADR/backlog. Não considerar a nova aba homologada apenas pelo build.
- Ambiente iniciado para teste manual em 06/10: Docker/PostgreSQL/Mailpit/MinIO ativos, V023 aplicada com sucesso no banco local, preservando dados e chave MFA. API na porta 8080 com health UP; frontend na porta 3000 e `/painel/clientes` HTTP 200; Mailpit e saúde MinIO HTTP 200. Logs `apps/api/local-test-api.log` e `apps/web/local-test-web.log`. Verificação de disponibilidade não substitui a homologação autenticada nem a integração pendente acima.
- A responsável pediu executar e testar antes de avançar. Última entrega concluída permanece conclusão/falta (V022), descrita abaixo. Depois de finalizar e testar Clientes/reserva assistida, a próxima implementação será notificações MVP-068; em seguida Financeiro MVP-069 e homologação/deploy.

## Entrega em 05/10: concluir atendimento e marcar falta (continuação de MVP-067)

- Conferência → Gerenciar reserva permite registrar Concluído ou Não compareceu, após término previsto e somente em atendimento confirmado. Observação visível ao cliente e confirmação explícita. Versão do horário exige nova consulta após reagendamento.
- Resultado, estado e evento são atômicos, com operador/auditoria/RLS. Repetição idêntica não duplica; duas decisões diferentes não sobrescrevem uma à outra. Não há edição/reabertura nesta etapa.
- Calendário e área do cliente distinguem COMPLETED/NO_SHOW de confirmação/expiração. Pagamentos, devoluções, saldo, alocação histórica e quota COMMITTED preservados. Encerrar atendimento não quita saldo nem transfere dinheiro; intenção financeira permanece CONFIRMED.
- V022 aplicada localmente sem apagar dados; API health UP e Conferência HTTP 200. OpenAPI 0.23.0/ADR-0024 atualizados. Validação: 78 testes aprovados (32 unitários + 37 jornada + 9 calendário), build e typecheck aprovados. Logs outcomes-verify.log/outcomes-build.log/outcomes-local.log. Homologação visual autenticada pendente.
- **Próxima tarefa: reserva assistida com cadastro de clientes (MVP-067/MVP-070)**, seguida de notificações MVP-068 e aba Financeiro MVP-069. Identificação por telefone permanece adiada.

## Entrega em 05/10: reagendamento do mesmo serviço (continuação de MVP-067)

- Conferência → Gerenciar reserva → Reagendar o mesmo serviço: consulta por data e seleção de horários disponíveis no fuso do atendimento. Usa expediente, exceções/folgas, intervalos, buffers, antecedência de 24h e horizonte de 60 dias.
- Serviço, duração original, preço, entrada, pagamentos, política e protocolo preservados. Troca de horário atômica: conflito mantém o horário anterior; não renova prazo de pagamento/conferência. Versão impede sobrescrita desatualizada e chave idempotente impede execução repetida.
- Mudança de mês transfere a quota HELD/COMMITTED e valida o limite do plano no destino. Histórico anterior/novo e motivo visíveis ao profissional e ao cliente; operador/correlation ID internos. Calendário consulta o novo horário. V021/ADR-0023 e OpenAPI 0.22.0.
- Validação: 75 testes aprovados (32 unitários + 34 jornada + 9 calendário), build e typecheck aprovados. Logs reschedule-final-verify.log/reschedule-build.log. V021 aplicada no banco local sem apagar dados, API health UP e rota Conferência HTTP 200 (reschedule-local.log). Homologação visual autenticada pendente.
- **Próxima tarefa: concluir atendimento e marcar falta (MVP-067)**; depois reserva assistida/cadastro de clientes, notificações MVP-068 e Financeiro MVP-069. Identificação por telefone permanece adiada.

## Entrega em 01/10: cancelamento profissional (primeira parte de MVP-067)

- Em Conferência → Gerenciar reserva, o profissional consulta a política original, informa motivo visível ao cliente e valor a devolver, e confirma a revisão. Política textual não é interpretada automaticamente. Saldo desatualizado exige nova consulta antes de cancelar.
- Cancelamento antes do início do atendimento, sob lock e com auditoria, muda reserva/intenção para CANCELED e libera o horário na mesma transação. Não apaga comprovantes/lançamentos. Quota HELD liberada; COMMITTED de reserva confirmada preservada. Repetição idêntica não duplica decisão/evento.
- Devolução da entrada passa a ser permitida após cancelamento, limitada à decisão e ao crédito original. Pendência é recalculada com devoluções/recebimentos posteriores; nenhum lançamento reabre reserva cancelada. Não há transferência automática. Cliente visualiza motivo, valor mantido e devolução pendente, sem PIX/upload nem contagem de expiração.
- V020, OpenAPI 0.21.0 e ADR-0022. Validação: 72 testes aprovados (32 unitários + 31 jornada + 9 calendário), build e typecheck aprovados. Logs cancellation-verify.log/cancellation-build.log. V020 aplicada no banco local sem apagar dados, API health UP e rota Conferência HTTP 200 (cancellation-local.log). Homologação visual autenticada pendente.
- **Próxima tarefa: reagendamento do mesmo serviço (continuação de MVP-067)**, com checagem de disponibilidade e preservação do histórico/pagamentos. Depois conclusão/falta e reserva assistida/cadastro de clientes. Cancelamento pelo próprio cliente e emails automáticos não fazem parte desta entrega; notificações em MVP-068. Mudança de identificação para telefone segue adiada.

## Entrega em 01/10: conferência, confirmação e pendências financeiras (MVP-064/065/066)

- Aba Conferência em `/painel/conferencia`: reservas priorizadas por estado, detalhe, comprovantes privados, recebimentos e devoluções. Operador registra valor, conta, referência bancária e data, com declaração de conferência no banco.
- Confirmação transacional exige entrada líquida suficiente, sem excedente, prazo válido e HOLD ativo. Reserva/intenção CONFIRMED, alocação BOOKING sem expiração e quota COMMITTED. Portal e calendário identificam confirmação sem confundir com prazo original vencido. PIX deixa o modo prévia para reservas pendentes.
- Referências normalizadas únicas por tenant; repetição idêntica não duplica. Insuficiência/excesso/tardio/devolvido são pendências financeiras derivadas dos lançamentos, separadas da agenda. Recebimento tardio não reabre reserva nem ocupa horário de terceiro.
- Devolução manual vinculada ao crédito original, limite cumulativo sob lock, referência/data/motivo/operador e auditoria. Não executa transferência. Devolução abaixo da entrada de atendimento confirmado depende do cancelamento/política em MVP-067; não há edição de lançamentos nem email novo de confirmação.
- V018/V019 aplicadas no banco local sem apagar dados; API health UP e rota Conferência HTTP 200. OpenAPI 0.20.0 e ADR-0021 atualizados. Validação: 32 unitários + 28 integrações da jornada + 9 do calendário = 69 testes distintos aprovados. Após corrigir um horário inválido na fixture, a jornada completa passou em review-public-verify.log; calendário passou em review-final-verify.log. Build (review-final-build.log) e typecheck aprovados. Homologação visual autenticada pendente.
- **Próxima tarefa: MVP-067 — gestão dos atendimentos**, iniciando pelo cancelamento com política e devolução aplicável; depois reagendamento, conclusão/falta e reserva assistida, acompanhada do cadastro de clientes. A identificação por nome/sobrenome/telefone continua adiada conforme solicitado.
## Entrega em 30/09: comprovantes e Em conferência (MVP-062/063)

- Upload privado de JPEG/PNG/PDF até 5 MB, sessão de cliente/propriedade/CSRF e leitura limitada. Validação por conteúdo, imagens reencodadas sem metadados até 16 megapixels e PDF sem senha/conteúdo ativo até dez páginas. Até cinco comprovantes por reserva; mesmo conteúdo não duplica arquivo/registro.
- Objetos em bucket S3/MinIO dedicado, sem URL pública; metadados com RLS e download autorizado como attachment/no-store/nosniff. Nenhum comprovante gravado no disco da API. Rollback remove objeto novo; interrupção abrupta pode deixar órfão privado para reconciliação operacional (ADR-0020).
- Primeiro envio dentro do prazo muda reserva/intenção para IN_REVIEW por 24 horas, mantém horário e quota provisória. Reenvio não renova prazo. Worker/limpeza síncrona expiram pelo prazo de análise; upload nunca confirma recebimento ou atendimento.
- Recibo e Meus agendamentos permitem envio/listagem/download; calendário identifica Em conferência. Conferência profissional ainda não está disponível, portanto a interface continua orientando não transferir nesta etapa.
- V017 aplicada no banco local sem apagar dados; OpenAPI 0.19.0 e ADR-0020 atualizados. Validação: 32 testes unitários + 33 de integração da jornada/calendário + 1 com MinIO real = 66 testes distintos aprovados, incluindo acesso anônimo negado, repetição, prazo fixo e limpeza após rollback. Build e typecheck do frontend aprovados. Logs: evidence-final-verify.log, evidence-s3-verify.log e evidence-build.log. Homologação visual autenticada permanece pendente.
- Imagens prontas MinIO indisponíveis; construção local a partir de revisão fixa do repositório oficial adicionada ao Compose e validada. Serviço privado local disponível, com instruções de configuração no runbook. Não há coletor automático de objetos órfãos após queda abrupta; reconciliação operacional documentada.
- **Próxima tarefa: MVP-064/065 — painel de conferência e validação manual**, com operador autorizado, consulta do comprovante, registro de valor/referência bancária e confirmação somente com entrada suficiente. Alteração para nome/sobrenome/telefone continua adiada conforme solicitado.

## Entrega em 29/09: base PIX manual e prévia de pagamento (MVP-060/061)

- V016 cria intenções de pagamento e estruturas de recebimentos, comprovantes e devoluções com FORCE RLS/FKs compostas. Uma intenção por reserva, criada na mesma transação e preservada na repetição idempotente. Reservas anteriores recebem intenção com os valores/prazos originais; expiração atualiza reserva e intenção juntas.
- Interface PaymentProvider com implementação ManualPixProvider. Consulta privada exige sessão de cliente e propriedade da reserva, usa snapshot PIX original e responde sem cache. Expiradas omitem chave/recebedor/instruções; consulta permanece após retirada da página/bloqueio da assinatura.
- Recibo e detalhe de Meus agendamentos exibem total, entrada, saldo projetado após entrada, recebedor, chave para copiar, instruções e prazo. Dados abertos sob clique explícito, atualização periódica e ocultação na expiração. Falha de clipboard oferece seleção manual.
- **Limite operacional:** tela em modo prévia, com paymentAvailable=false e orientação para não transferir nesta etapa. Não há upload, validação bancária, confirmação de recebimento/atendimento ou devolução operacional. Estruturas futuras de recebimento/comprovante/devolução não têm permissão de escrita para o runtime ainda. Conferência será integrada nas próximas etapas.
- OpenAPI 0.18.0 e ADR-0019. V016 aplicada localmente com dados preservados e backend health UP (pix-foundation-local.log). Validação: 27 unitários e 27 integrações (PublicPageJourneyIT/PaymentSettingsJourneyIT) aprovados em pix-foundation-verify.log; build e typecheck aprovados (pix-foundation-build.log). Testes incluem preservação de chave/valores, propriedade, expiração, RLS, idempotência e rollback da intenção. Homologação visual autenticada/cópia ainda pendente; nenhuma transferência realizada.
- **Próxima tarefa: MVP-062/063 — envio privado de comprovantes e estado EmConferencia**, com validação de arquivos e armazenamento privado. Depois conferência/validação manual MVP-064/065. Upload nunca confirma pagamento automaticamente. Troca de email por nome/sobrenome/telefone permanece adiada.

## Entrega em 29/09: expiração coordenada (MVP-056)

- Worker seleciona somente tenants com reservas vencidas; até 20 lotes por ciclo e 100 reservas por transação. Coordenação persistida no PostgreSQL, com token exclusivo e lease de dois minutos; execução antiga não pode finalizar trabalho assumido por outra instância.
- Liberação de horário, devolução da quota, estado EXPIRED, evento e conclusão do lote na mesma transação. Falha desfaz tudo e agenda nova tentativa com espera progressiva de 30 segundos a 15 minutos. Se a instância parar, lease vencido permite recuperação. Nenhum histórico é apagado.
- Limpeza síncrona antes de criar/consultar continua completa e compartilha a transição com o worker. Reservas ainda vigentes são preservadas; tentativas repetidas não duplicam eventos.
- V015 cria fila por tenant, inicializa tenants existentes e novos, com RLS e funções mínimas de coordenação. Logs de conclusão/falha sem conteúdo pessoal. ADR-0018; nenhuma mudança de contrato HTTP ou interface.
- Validação: 27 unitários e 30 integrações de jornada pública/calendário aprovados (expiration-verify.log), incluindo rollback, retomada de lease, limite de lote e quota. Cenário concorrente adicional no mesmo teste aprovado em expiration-concurrency-verify.log. V015 aplicada localmente com dados preservados e backend health UP (expiration-local.log). Sem nova execução do build frontend, pois não houve mudança de interface nesta tarefa.
- **Próxima tarefa: MVP-060/061 — base de pagamento PIX manual e tela de pagamento**, com valores/prazo e dados preservados da reserva. Em seguida comprovantes e conferência (MVP-062+). Upload nunca confirma pagamento automaticamente. Ajuste para nome/sobrenome/telefone continua adiado conforme solicitado.

## Entrega em 29/09: Meus agendamentos (MVP-055)

- Nova área `/a/{slug}/meus-agendamentos`, com atalhos na página pública e jornada de reserva. Lista paginada, detalhes, protocolo, estado, prazo restante, valores, modalidade/local, política aceita e histórico. Atualização a cada 30 segundos; valores e condições vêm dos snapshots.
- Acesso atual por link de email: 15 minutos, uso único, reenvio revoga anterior, resposta genérica e limites por email/conexão. Sessão restrita ao cliente/profissional por até uma hora, com saída própria. A reserva recém-criada já abre esse acesso para o email verificado; links permitem acesso em outro dispositivo.
- Consulta preservada com página retirada ou assinatura bloqueada. RLS e propriedade por cliente; token removido da URL e consumido após clique explícito. Respostas privadas sem cache. Saída remove acessos de cliente sem encerrar eventual login administrativo.
- V014, OpenAPI 0.17.0 e ADR-0017. Não habilita pagamento, comprovante, cancelamento ou reagendamento.
- Validação: 27 testes unitários e 18 integrações de PublicPageJourneyIT passaram nesta entrega (portal-verify.log), incluindo três cenários novos do portal. Build e typecheck aprovados (portal-build.log). Tela de acesso conferida no navegador; jornada autenticada completa e email externo ainda requerem homologação. Demais suítes de integração não foram reexecutadas nesta etapa.
- **Ajuste solicitado para mais tarde:** retirar a exigência de email do agendamento e da consulta, usando nome, sobrenome e telefone. Pedido explicitamente adiado pela responsável em 29/09; o fluxo atual por email foi mantido nesta entrega. Planejar identificação/verificação pelo telefone, migração dos clientes existentes e recuperação de acesso; nome/telefone informados sem verificação não devem conceder leitura de reservas privadas. Canal de validação ainda a definir.
- **Próxima tarefa técnica: MVP-056**, completar processamento em lotes, coordenação/leases e recuperação de falhas da expiração. Depois PIX/comprovantes/conferência MVP-060+. Mudança de contato acima fica registrada para uma etapa posterior.

## Entrega em 29/09: reservas temporárias (MVP-052/053/054)

- Após verificar email e revisar serviço/valores/política, o cliente pode solicitar reserva temporária por 30 minutos. Recibo com protocolo, prazo e estado; atualização automática e recuperação da solicitação recente na mesma sessão. Ainda não confirma atendimento nem habilita transferência/comprovante PIX.
- Uma transação cria cliente, reserva, alocação, snapshots de serviço/PIX, quota provisória, evento, recibo idempotente e outbox. Lock por tenant e GiST impedem dupla reserva; alterações de valores/condições exigem nova revisão. Repetir a mesma chave/conteúdo devolve o recibo original; conteúdo diferente gera conflito.
- Quota mensal conforme o plano vigente, no mês do atendimento e fuso do profissional; reservas temporárias consomem provisoriamente e expiração libera consumo. Limites configuráveis no banco; valores comerciais ainda pendentes de decisão. Sugestão apresentada: Básico 50, Intermediário 200, Premium ilimitado. A V013 mantém os planos sem teto até essa definição.
- Expiração síncrona e worker básico liberam horário/quota e preservam histórico. MVP-056 permanece parcial: operação por lotes/leases e integração dos futuros estados de pagamento pendentes. Calendário profissional identifica Solicitação pendente e o serviço, sem confundir com atendimento confirmado.
- V013 com FORCE RLS/FKs compostas e snapshots/recibos imutáveis para o runtime. OpenAPI 0.16.0 e ADR-0016. Consulta de recibo restrita ao email verificado/slug; sessão recente não substitui Meus agendamentos ou acesso em outro dispositivo.
- Validação: 27 testes unitários e 68 de integração nos relatórios finais, sem falhas. A execução completa encontrou um erro na fixture de assinatura Básico/trial; corrigido, PublicPageJourneyIT e CalendarJourneyIT passaram juntos (24 testes). Cobertura inclui concorrência, rollback da transação quando a outbox falha, idempotência, isolamento, snapshots, quota e expiração. Build e typecheck aprovados; homologação visual/email externo ainda pendentes. Logs booking-verify.log, booking-final-verify.log e booking-build.log.
- **Próxima tarefa: MVP-055 — Meus agendamentos**, com acesso verificado, lista e detalhe das reservas do cliente. Depois completar MVP-056 e implementar PIX/comprovantes/conferência MVP-060+.

As seções datadas abaixo preservam o histórico; o estado atual e a próxima tarefa estão nesta entrega e na lista consolidada ao final.

## Ajuste em 29/09: logomarca no modo claro

- Removido o retângulo bege da versão clara: fundo transparente, lettering escuro (#17212B) e símbolo original preservado. Enquadramento igual ao da versão escura para manter o tamanho visual ao alternar o tema.
- SVG validado como XML e diff conferido; sem mudança funcional ou nova execução de testes.
- Próxima tarefa mantida: criação de reserva temporária, snapshots, idempotência e quota (MVP-052/053/054).

## Implementação em 28/09

**Atualização após a revisão:** MVP-044 implementado nesta entrega. Nova aba `/painel/calendario`, consulta privada mensal/semanal/diária e integração de bloqueios; detalhes a seguir. Os itens da revisão inicial abaixo foram levantados antes desta implementação.

### Entrega mais recente em 28/09: jornada pública e verificação de cliente (MVP-050/051)

- Rota /a/{slug}/agendar: serviço → calendário com dias disponíveis → horário → nome/email → verificação por link → revisão. Consulta usa disponibilidade real, por serviço, no fuso do profissional. Trocar serviço/data limpa escolhas posteriores; respostas antigas são descartadas.
- GET público de disponibilidade não expõe ocupações, motivos privados ou dados de outros clientes. Resolve somente página publicada, aplica RLS e revalida assinatura, pagamento ativo e serviço sob lock do tenant. Horizonte, antecedência, exceções, buffers e intervalos são os mesmos do painel.
- Link de 256 bits com hash SHA-256, validade de 15 minutos, uso único e escopo do profissional. Reenvio revoga anterior do mesmo email/tenant. CSRF e limites por email/conexão; resposta genérica. Token em fragmento, removido da URL, consumo somente após clique explícito.
- Sessão de revisão restrita ao slug por 15 minutos, sem privilégios administrativos. Preço e entrada recalculados pelo servidor; revisão avisa se horário foi ocupado. Não cria cadastro permanente Customer, Booking, HOLD, quota ou pagamento ainda.
- Outbox existente envia os links e limpa destinatário/conteúdo após entrega; tokens expirados são removidos pelo worker. V012 adiciona customer_access_tokens com FORCE RLS e FK composta, funções mínimas para resolução de slug/worker e finalidade CUSTOMER_ACCESS. OpenAPI 0.15.0 e ADR-0015.
- Limite explícito da entrega: consultar/verificar/revisar não reserva horário. reservationEnabled continua false, sem botão de confirmação fictício. A página pública oferece Consultar serviços e horários; a prévia privada não abre uma agenda pública de rascunho.
- Validação: build e typecheck aprovados; 27 unitários e 63 integrações nos relatórios finais. A suíte inicial identificou um erro no teste que reutilizava MockHttpSession em vez do cookie Spring Session; corrigido, os 10 testes de PublicPageJourneyIT passaram, incluindo a nova validação do worker com transporte SMTP simulado. Logs customer-verify.log, customer-journey-verify.log e customer-build.log. Homologação completa por navegador/email real permanece pendente.
- Próxima entrega: MVP-052/053/054 — criar reserva temporária com alocação, snapshots, idempotência e quota na mesma transação. Depois MVP-055/056 (Meus agendamentos/expiração) e PIX/conferência MVP-060+.

### Ajuste em 28/09: botões de contato

- Vamos conversar? usa botões com ícones e texto: Enviar email abre mailto; Conversar no WhatsApp abre wa.me para o telefone comercial. Disponíveis também na prévia; sem envio automático.
- Números brasileiros com DDD recebem código 55 quando ausente; internacionais exigem + e código do país. Telefone fora desses formatos mantém alternativa de ligação. Perfil orienta informar número cadastrado no WhatsApp; não há consulta de existência da conta.
- Build (contact-buttons-build.log) e typecheck aprovados. Não foram abertas conversas reais nem enviados emails. Sem alterações de backend; próxima tarefa continua sendo reservas de clientes MVP-050+.

### Entrega em 28/09: compartilhamento (MVP-036)

- Nova aba Compartilhar e atalho em Publicação, rota /painel/compartilhar. Consulta o estado real de publicação, bloqueia ações de compartilhamento para rascunhos e reconsulta ao voltar à janela; erro de consulta impede compartilhar.
- Link completo formado com a origem atual e o caminho retornado pelo servidor. Copiar link, abrir página, convite editável, restaurar sugestão e copiar mensagem. Falha da área de transferência seleciona o texto para cópia manual.
- Abrir no WhatsApp usa wa.me com texto codificado; o profissional escolhe o destinatário e confirma o envio. Nenhuma mensagem foi enviada nesta implementação. Sugestão apresenta serviços/contatos, sem prometer reservas online ainda indisponíveis. Rascunho do convite fica somente nesta tela.
- Aviso para localhost/loopback: link local não é acessível aos clientes em outros dispositivos. Compartilhamento externo depende da hospedagem pública.
- Build (sharing-build.log) e typecheck aprovados, incluindo a nova rota. Sem migration, endpoint novo ou alteração de backend. Homologação autenticada de clipboard/abertura do aplicativo WhatsApp permanece pendente; não foi executada a suíte backend nesta alteração.
- Próxima entrega: reservas de clientes MVP-050 a 056, começando pelo fluxo serviço → data → horário → contato/verificação → revisão, com consulta pública segura e integração às alocações existentes.

### Ajuste em 28/09: endereço e localização do espaço

- Minha página: campo Endereço do seu espaço para modalidade presencial/híbrida, orientação para endereço completo e link para conferir no Google Maps. Reutiliza location já persistido, sem migration ou API nova.
- Página pública e prévia: seção Onde estamos com endereço, Ver no Google Maps e Como chegar. A seção não é renderizada na modalidade exclusivamente online. Links codificam o endereço e abrem em nova aba; nenhum mapa externo é carregado automaticamente.
- Build (location-build.log) e typecheck aprovados. Sem nova execução da suíte backend; funcionamento do endereço real no provedor deve ser conferido pelo profissional antes de publicar.
- Próxima tarefa mantida: Compartilhamento (MVP-036), seguido do fluxo serviço → data → horário → contato/revisão da reserva (MVP-050+).

### Entrega em 28/09: publicação explícita da página (MVP-034/035)

- Publicação liberada com perfil completo, assinatura operacional, serviço ativo, PIX/política ativos e ao menos um horário disponível para serviço ativo no horizonte de 60 dias. Inclui antecedência, duração, buffers, intervalo, exceções e ocupações reais.
- GET calcula requisitos sob lock do tenant; POST revalida na transação antes de publicar. DELETE coordenado preserva dados e funciona após bloqueio da assinatura. Sem publicação automática.
- Aba Publicação exibe pendências, informa os dados expostos, permite Publicar página, Ver página pública e Retirar página do ar. Perfil/serviços salvos são refletidos na página já publicada.
- Apresentação comercial liberada; CTA de reserva permanece desabilitado até Booking. PIX e observações privadas não aparecem. Lotação posterior não retira automaticamente uma página já publicada.
- OpenAPI 0.14.0 e ADR-0014, sem migration. Validação: 86 testes (27 unitários + 59 de integração), build e typecheck aprovados. Testes cobrem publicação explícita/repetida, retirada, isolamento, CSRF, requisitos alterados após a prévia, duração e bloqueios reais. Homologação visual autenticada desta tela permanece pendente.
- Próxima tarefa: Compartilhamento (MVP-036), copiar link e preparar mensagem editável para abrir no WhatsApp. Depois, reservas de clientes MVP-050+.

### Ajuste em 28/09: expediente por data específica

- Seção em destaque no início de Horários: escolher uma data, copiar o expediente semanal para personalizar, editar várias faixas, marcar sem atendimento ou voltar ao padrão semanal. Edição usa as exceções já persistidas; não altera as demais datas do mesmo dia da semana. Precisa salvar para confirmar.
- Calendário diário ganhou atalho com data preenchida para essa seção. Exemplo orienta duas faixas em 30/09; quantidade de horários disponíveis depende da duração do serviço, preparação e intervalo, conferida na prévia.
- Usa API/versionamento e regras existentes, sem alteração de backend ou migration. Build e typecheck aprovados (specific-date-build.log); teste existente exceptionReplacesWeekAndEmptyExceptionClosesDay cobre substituição do expediente. Não houve nova execução da suíte backend ou homologação autenticada do editor nesta alteração.
- Próxima tarefa: critérios reais de publicação e compartilhamento (MVP-034/035); depois fluxo de reservas de clientes MVP-050+.

### Ajuste de tamanho em 28/09

- Calendário mensal/semanal com largura máxima de 840px, células mensais mais baixas (86px) e semanais de 200px. Em telas menores, a grade se adapta à largura disponível e preserva as regras de celular. Alteração apenas de CSS, conferida no diff; sem nova execução da suíte funcional.
- Próxima tarefa mantida: publicação e compartilhamento (MVP-034/035).

### Ajuste em 28/09: calendário visual e aparência

- Mês como visualização inicial, em grade de seis semanas; Semana mostra apenas sete dias. Dia mostra a lista de ocupações e bloqueios. Clicar em uma data abre a lista diária; navegação, Hoje e seletor de data mantidos.
- Pontos: verde quando há horários disponíveis sem ocupações; amarelo quando há ocupações e disponibilidade restante; vermelho quando nenhum serviço ativo cabe no dia. Vermelho tem prioridade. Cálculo real inclui expediente, folgas, duração, buffers, intervalo, bloqueios, antecedência de 24h e horizonte de 60 dias. Sem serviço ativo também significa indisponível.
- HOLDs vigentes são temporários, ainda não reservas confirmadas de clientes. Bloqueios pessoais não contam como atendimentos; expirados não afetam o indicador.
- Botão de sol/lua na barra superior, disponível em todo o sistema, alterna claro/escuro. Preferência salva neste navegador, restaurada antes da pintura e sincronizada entre abas. Logomarca usa a variante adequada ao tema.
- OpenAPI 0.13.0: consulta aceita 1, 7 ou 42 dias e retorna status/occupiedCount por dia. Sem migration.
- Validação: 83 testes (27 unitários + 56 de integração), build e typecheck aprovados. Alternância e persistência do tema verificadas no navegador na tela de login. Revisão visual do calendário autenticado permanece pendente; nenhum login foi realizado nesta verificação.
- Próxima tarefa: critérios reais de publicação e compartilhamento (MVP-034/035). Reservas de clientes e CTA de agendamento dependem de MVP-050+.

### Entrega inicial em 28/09: calendário administrativo (MVP-044)

- Aba Calendário no menu; semana (segunda a domingo) no desktop e dia no celular, alternância manual, navegação anterior/próxima, seletor de data, Hoje e atualização manual/periódica.
- Expediente atual, folgas/exceções e ocupações persistidas por dia. Bloqueios e HOLDs recebem rótulos distintos; HOLD não é exibido como reserva confirmada. Preparação e faixas que atravessam datas são identificadas.
- GET `/admin/calendar?from=YYYY-MM-DD&days=1|7`: membership/RLS, no-store, REPEATABLE READ e limites civis no fuso do perfil. Dias de 23/25 horas, faixas que começam antes do intervalo, limites semiabertos e expiração considerados.
- Criação/liberação de bloqueios reutilizada no calendário, com atualização após mutação. Consulta preservada após bloqueio da assinatura; mutações seguem restritas. Respostas antigas de navegação descartadas.
- Datas passadas mostram a configuração de expediente atual, não um histórico de versões; isso é explicado na interface. Não existem reservas ou clientes fictícios.
- OpenAPI 0.12.0, ADR-0013, sem migration. Próxima tarefa: concluir os critérios reais de publicação (MVP-034/035); CTA de reserva depende de MVP-050+.

### Revisão inicial de 28/09 — antes da implementação

Revisão do código, rotas do painel, migrations, backlog e relatórios existentes. Esta revisão inicial foi documental; a implementação e a validação posteriores estão registradas acima e na tabela de validação. As seções de entregas anteriores abaixo registram o estado na data de cada entrega; suas pendências podem ter sido resolvidas posteriormente.

| Área | Situação confirmada no código |
|---|---|
| Identidade e plataforma | Login, recuperação/verificação de email, assinatura e superadmin com MFA implementados |
| Configuração do profissional | Perfil/logo, serviços e PIX/política implementados |
| Horários | Configuração em lote ou individual, folgas/ausências, pausas e intervalo global entre atendimentos implementados |
| Disponibilidade | Prévia por serviço/data com fuso, duração, buffers, antecedência e ocupações persistidas |
| Bloqueios/alocação | Bloqueios pontuais e liberação no painel; núcleo HOLD interno, expiração e proteção GiST/locks implementados |
| Calendário visual | **MVP-044 pendente:** não há rota nem seção semanal/diária no painel |
| Publicação | Página/prévia prontas; `canPublish` permanece fixo em false e POST rejeita publicação. A mensagem de requisito ainda é genérica; atualizar junto da liberação real |
| Reservas e operação | Booking, acesso do cliente, pagamento de reservas, cadastro de clientes e financeiro ainda pendentes; HOLD interno não equivale a reserva de cliente |

Última validação completa registrada: **25/09/2026, 79 testes (27 unitários + 52 de integração), build e typecheck aprovados**. Relatórios Maven e log do build foram conferidos nesta revisão. Homologação de navegador/acessibilidade continua pendente.

### Escopo executado: MVP-044 — calendário administrativo

- Adicionar uma aba **Calendário**, mantendo **Horários** como área de configuração.
- Visão semanal no desktop e lista diária no celular, com seleção de data, anterior/próximo e retorno a hoje, no fuso do profissional.
- Exibir expediente, folgas/exceções e bloqueios reais; identificar ocupações temporárias vigentes sem apresentá-las como reservas confirmadas. Não inventar clientes ou compromissos.
- Criar consulta autenticada por período, com RLS e leitura consistente. A lista atual de bloqueios mostra apenas ativos futuros/em andamento e não substitui uma consulta de calendário por intervalo. Incluir faixas que começam antes e atravessam o período consultado; tratar limites semiabertos e expiração.
- Reutilizar criação/liberação de bloqueios e atualizar a visualização após essas ações. Consulta preservada após bloqueio de assinatura; mutações mantêm as restrições existentes.
- Validar isolamento entre profissionais, fuso, bloqueios atravessando datas, expiração e leitura após bloqueio; verificar layout semanal/diário e acessibilidade. Sem drag-and-drop nesta etapa.

Após essa entrega, seguir com publicação e compartilhamento conforme os critérios reais; CTA de reserva só será habilitado com o fluxo de Booking. MVP-045 permanece parcial até coordenar os estados de reservas confirmadas/reagendamento.

## Entrega mais recente em 25/09: alocações e bloqueios seguros (MVP-042/043, MVP-045 parcial)

- V011: calendar_allocations com recurso único por tenant, UTC, buffers, faixa semiaberta, active e exclusão GiST; RLS e FK composta de serviço. Banco rejeita sobreposição ativa, inclusive se uma gravação contornar o lock da aplicação.
- Núcleo transacional de ocupação temporária HOLD (30 min) verifica assinatura, serviço, expediente, ocupações, intervalo global e buffers sob lock do tenant. Sem endpoint público de reserva ou pagamento. Expirados são ignorados na leitura e desativados antes de novas mutações de alocação.
- Nova seção **Bloquear um período específico** em Horários: início/fim no fuso do perfil, motivo privado, lista paginada e liberação sem exclusão do histórico. CSRF, assinatura, validação de período/offset e isolamento de tenant.
- Prévia agora considera ocupações persistidas e é invalidada após criar/liberar bloqueio. Bloqueios pontuais continuam valendo mesmo após alterações do expediente.
- Alteração de expediente/intervalo ou fuso é recusada com 409 enquanto existir HOLD futuro vigente. Corridas HOLD/bloqueio e HOLD/expediente têm um único vencedor. Ainda será necessário ampliar esta coordenação aos estados de Booking quando forem implementados.
- OpenAPI 0.11.0 e ADR-0012. Próxima etapa: calendário diário/semanal MVP-044; publicação e reserva continuam pendentes.

## Entrega anterior em 25/09: gerador e prévia de horários (MVP-041)

- Na aba Horários, “Confira como fica o seu dia” calcula alternativas de início e uma sugestão de sequência por serviço e data, usando a configuração salva. Edições pendentes exigem salvar antes de consultar.
- Semana, exceções/folgas, duração e buffers, intervalo global, fuso IANA, antecedência 24h, horizonte 60 dias e grade 15min. Tempos em UTC na API, exibidos no fuso do perfil.
- Sequência respeita max(intervalo global, buffer posterior anterior + buffer anterior seguinte); candidatos próximos são alternativas, não atendimentos simultâneos.
- Períodos que cruzam transição de offset ou têm limites ambíguos/inexistentes são omitidos conservadoramente; demais períodos do dia funcionam. Regra e limitação explícitas na ADR-0011 e interface.
- Endpoint privado `/admin/availability/slots`, isolamento de tenant, serviço ativo obrigatório, sem cache e snapshot consistente. Não cria reservas, não publica a página e ainda não lê ocupações persistidas; alocação e proteção de concorrência vêm a seguir.
- OpenAPI 0.10.0, sem migration. Próxima etapa: MVP-042/043/045, alocações reais e proteção contra dupla reserva; em seguida calendário administrativo MVP-044.

## Ajuste anterior em 25/09: configuração de horários em lote

- Acrescentado **Intervalo entre atendimentos**: minutos globais (0 a 240), atalhos 0/15/30/60, persistência na configuração, validação no servidor e leitura de configurações antigas como zero. Aplica-se à semana e datas especiais; cálculo efetivo depende do gerador MVP-041. Exemplo: fim às 10h e intervalo de 30 min permitem próximo início a partir de 10h30, respeitando também as demais regras do serviço.

- Após feedback da responsável, a aba Horários passa a abrir com configuração de vários dias: selecionar dias, atalhos segunda–sexta/segunda–sábado/todos/fim de semana, definir períodos uma vez e aplicar ou marcar folga.
- Resumo compacto da semana com ajustes individuais; alternativa explícita “Configurar dia por dia”. Aplicação em lote substitui somente os dias selecionados, preservando os demais. É necessário salvar após conferir o resumo.
- Ausências, folgas, férias e feriados por data única ou intervalo inclusivo. Datas existentes não são sobrescritas; limite total de 366 datas, período invertido e colisões são informados antes de alterar o rascunho.
- Datas especiais recolhidas em uma lista com data/motivo/estado; ajustes individuais continuam disponíveis. Folga semanal recorrente fica separada de ausência em datas específicas.
- Revisão em lote inicialmente apenas de interface; complemento de intervalo atualiza API/contrato sem migration. Verificação do complemento: 20 testes unitários e 4 testes de integração de horários passaram (`interval-verify.log`), além de build e TypeScript. Suíte completa de 65 testes abaixo refere-se à entrega anterior. Revisão visual/manual pela responsável pendente.
- Próximo passo de desenvolvimento permanece MVP-041: cálculo de horários disponíveis.

## Entrega anterior em 25/09: configuração de horários (MVP-040)

- Nova aba **Horários**, em `/painel/horarios`, com períodos por dia da semana e pausas por intervalos separados. Dias sem períodos ficam sem atendimento.
- Exceções por data substituem o expediente semanal: feriado/bloqueio total sem períodos, expediente especial ou bloqueio parcial com os períodos permitidos. Observação privada; até 366 datas e oito períodos por dia.
- Validação no servidor de sobreposição, períodos invertidos, precisão de minutos e dias/datas duplicados. Regras locais usam o fuso do perfil; mudança do fuso reinterpreta os mesmos horários, conforme aviso na tela.
- V010 com RLS, versão e lock do tenant. CSRF e assinatura operacional nas mutações; leitura preservada após bloqueio; conflito 409 exige recarregar.
- Contrato OpenAPI 0.9.0 e ADR-0010. Não há geração de slots, alocação de reservas ou calendário de compromissos nesta entrega. MVP-041 a 045 continuam pendentes, e publicação permanece bloqueada.
- A responsável testou e aprovou a entrega anterior da página/prévia.

## Entrega anterior em 25/09: página e preparação da publicação (MVP-034/035 parcial)

- Página `/a/{slug}` preparada com perfil, logo saneada, contato comercial, modalidade/local, serviços ativos, duração, preço e entrada. Reserva explicitamente indisponível, sem horários ou confirmação simulados.
- Nova aba **Publicação** em `/painel/publicacao`, com prévia privada, endereço reservado e requisitos calculados: perfil, assinatura, serviço ativo, PIX/política e disponibilidade.
- V009 inicia todos os perfis como não publicados. Rascunho e slug inexistente retornam o mesmo 404, inclusive para logo. Projeção pública sem PIX, identidade do usuário ou tenant ID; consultas sem cache.
- Leitores SQL restritos expõem somente os campos públicos de páginas publicadas, preservando RLS nas consultas diretas. Testes ativam publicação somente em bancos descartáveis para verificar a projeção.
- POST de publicação permanece bloqueado enquanto não existe calendário/disponibilidade real. Retirada autenticada com CSRF preserva dados e funciona mesmo após bloqueio da assinatura.
- OpenAPI 0.8.0 e ADR-0009. **MVP-034/035 continuam parciais:** publicação funcional e CTA de reserva dependem das próximas etapas. Nenhuma página real foi publicada.

## Entrega anterior em 25/09: PIX e política (MVP-033)

- Revisados o status, as regras SDD e os módulos de perfil, catálogo, assinatura e navegação. Entregas anteriores preservadas e cobertas pela suíte de regressão.
- Nova área funcional **PIX e política**, em `/painel/pagamentos`, com tipo/chave PIX, recebedor, instruções de pagamento, política de cancelamento/reagendamento e opção de desativar a configuração sem apagar histórico.
- Validação local de CPF/CNPJ (inclusive alfanumérico e dígitos verificadores), email, telefone internacional e chave aleatória. Não há consulta ao banco/DICT nem confirmação de titularidade.
- Conferência declarada pelo profissional antes de salvar; registro de ator, instante UTC, motivo, campos alterados e correlation ID. Histórico paginado mostra apenas metadados, sem chaves anteriores ou textos privados.
- V008: versões imutáveis com RLS e FK composta de membership; runtime somente SELECT/INSERT. Cada alteração real preserva a versão anterior e prepara referência/cópia nas reservas futuras. Snapshots por reserva ainda dependem do MVP-052.
- CSRF e assinatura operacional nas mutações; leitura preservada após bloqueio. Controle de versão impede sobrescrita concorrente e retorna 409; gravações sem alteração não geram nova revisão.
- Contrato OpenAPI 0.7.0, ADR-0008 e instruções locais atualizados. Não há publicação, cobrança automática, QR Code ou devolução automática nesta entrega.

## Entrega anterior em 23/09: serviços e painel por áreas

- MVP-032: cadastro, edição, inativação e reativação de serviços; nome/descrição, duração 5–480 min, preço em centavos, intervalos antes/depois 0–240 min e entrada 50–100%.
- Entrada calculada no servidor com arredondamento para cima, sem cobrança ou confirmação de pagamento. Valores fracionários em campos inteiros são rejeitados.
- Catálogo paginado, total/ativos reais, RLS por tenant e bloqueio de mutações por assinatura. Inativação preserva registros; não existe exclusão pública.
- Versão em cada edição evita sobrescrever alteração concorrente (409); ID de outro tenant retorna 404.
- Painel com menu lateral desktop e navegação adaptada mobile: Visão geral (`/painel`), Minha página (`/painel/minha-pagina`), Serviços (`/painel/servicos`) e Assinatura (`/painel/assinatura`). Apenas áreas funcionais aparecem.
- Visão geral mostra progresso do perfil, serviços ativos e estado da assinatura, sem métricas fictícias de reservas/financeiro.
- V007, OpenAPI 0.6.0, ADR-0007, testes de ciclo de vida, concorrência, isolamento, valores e bloqueio.
- A responsável confirmou que testou perfil/logomarca e aprovou; a organização do painel segue o menu solicitado.

## Entrega anterior em 23/09: perfil profissional e logomarca

- MVP-030/031: edição de nome, descrição, contato comercial, modalidade presencial/online/híbrida, local e fuso IANA, preservando o slug reservado.
- Rascunhos salvos com progresso calculado no servidor e lista de campos faltantes. Atendimento online não exige endereço; logo é opcional. 100% do perfil não significa publicação da agenda.
- Painel com formulário responsivo, prévia da edição e envio/substituição/remoção de logo. A identidade visual aprovada foi mantida.
- PNG/JPEG até 2 MiB, validação pelo conteúdo e limites de dimensões/pixels antes da decodificação. Variante PNG até 512 pixels sem metadados, preservando proporção e transparência; original descartado.
- Variante compacta persistida no perfil com RLS, sem arquivos privados no disco. A consulta é autenticada; publicação da imagem depende da futura página pública. Decisão documentada na ADR-0006.
- Trial vencido/suspensão bloqueiam edição e mutações da logo, preservando leitura e dados. Upload inválido mantém a imagem anterior. Compatibilidade com os campos antigos do perfil preservada.
- Migration V006, OpenAPI 0.5.0 e testes de isolamento, upload, progresso e regressão.
- A responsável confirmou nesta conversa que conseguiu acessar o superadmin com autenticador. A homologação automatizada de navegador permanece pendente.

## Entrega anterior em 23/09: superadmin, MFA e decisões de assinatura

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
- Perfil com nome, descrição, slug reservado, fuso IANA, contato comercial, modalidade/local, progresso e logomarca; frontend conectado à assinatura e ao perfil reais.
- Home escura/verde-água com a logomarca, slogan aprovado, FAQ e ilustração de agenda; login/cadastro/painel compartilham a identidade.
- O usuário relatou em 23/09 ter testado login, sair e recuperação de senha com sucesso.

## Validação histórica de implementação — 28/09/2026 (entregas posteriores acima)

| Check | Evidência |
|---|---|
| `mvnw.cmd verify` | Relatórios finais: 27 unitários + 63 integrações (90 únicos), todos aprovados após correção do teste de cookie e reexecução da classe PublicPageJourneyIT; PostgreSQL descartável, transporte do novo email simulado |
| Migrations V001 a V012 | Aplicadas em bancos descartáveis, usando role real de runtime |
| Atualização de banco existente | V003 → V012 preserva usuário, token e todos os dados da assinatura/trial; email legado pendente expira e tem conteúdo limpo |
| Calendário | Grade de 42 dias, indicadores livre/ocupado/indisponível, expiração e bloqueios; consulta diária/semanal, faixa atravessando a data, exceções, RLS, leitura após bloqueio, HOLD expirado omitido, limites semiabertos e dias civis de 23/25h |
| Alocações/bloqueios | Corridas HOLD/bloqueio e HOLD/expediente, GiST direto, limites adjacentes, intervalo, expiração, fuso protegido, RLS/CSRF, bloqueio de assinatura e liberação sem excluir histórico |
| Gerador de horários | Grade/fuso, antecedência/horizonte, pausas, exceções, duração/buffers, intervalo, limites semiabertos e DST; consulta isolada, serviço inativo/externo negado |
| Horários | Semana, pausas, dia fechado, validação de períodos/dias, RLS, CSRF, isolamento, conflito concorrente e bloqueio de trial com leitura preservada |
| Página/publicação | Publicação explícita, revalidação de requisitos, duração/bloqueios reais, retirada e projeção sem PIX; rascunhos/logo privados, projeção só com serviços ativos, isolamento entre tenants, RLS direta, entrada em centavos, no-store, requisitos, CSRF, bloqueio de publicação e retirada sem perda de dados |
| PIX/política | Tipos/DV, normalização, confirmação obrigatória, versões antigas preservadas, auditoria sem chave, RLS/CSRF, permissões imutáveis, bloqueio pós-trial, noop e concorrência com um vencedor |
| Catálogo de serviços | Cadastro/edição/inativação/reativação, entrada arredondada, limites na API/banco, JSON fracionário rejeitado, RLS, CSRF, bloqueio após trial e duas edições concorrentes com um único vencedor |
| Perfil e logo | Rascunho/progresso, modalidade online/presencial, validação, compatibilidade, CSRF, isolamento entre profissionais, edição bloqueada, leitura preservada e imagem anterior mantida após erro |
| Saneamento de imagem | JPEG/PNG reencodificados, limite de bytes/dimensões, formatos inválidos rejeitados, proporção/transparência preservadas e conteúdo anexado descartado |
| Plataforma/MFA | Conta comum negada, CSRF, papel revogado, MFA vencido, novo login, replay/concorrência, limite de tentativas, configuração expirada e vetores RFC 6238 |
| Decisões e billing | Confirmação idempotente, concorrência sem dupla vigência, referência duplicada com rollback, auditoria protegida, suspensão, retirada sem novo trial e expiração paga |
| Reenvio/consumo concorrentes | Uma nova emissão por janela de cooldown e apenas um vencedor no consumo do token |
| Limites concorrentes | 20 tentativas simultâneas admitem exatamente 10; renovação na próxima janela; falhas de login não apagam contadores |
| Segurança HTTP | Respostas genéricas, CSRF, validação, 429/Retry-After, correlation ID e X-Forwarded-For sem influência no endereço confiável |
| Outbox | Falha/retry/sucesso, exaustão, expiração, revogação, preservação de cadastro e exclusão mútua entre workers |
| SMTP real | Mensagem sintética aceita por Mailpit via SMTP; registro passa a SENT e conteúdo é limpo |
| Regressão de negócio | Cadastro, sessão, RLS, bloqueio do trial, reativação, recuperação e logout continuam cobertos |
| `npm run typecheck` e `npm run build` | PASS |

Relatórios: `apps/api/target/surefire-reports`, `apps/api/target/failsafe-reports`. Log da última suíte completa: `apps/api/calendar-view-verify.log`; build em `apps/web/calendar-view-build.log`. Suíte completa reexecutada após a implementação em 28/09. A tentativa inicial encontrou Docker fechado; após iniciá-lo, todos os testes passaram. PASS técnico não equivale à homologação de negócio. Frontend validado por tipagem/build; E2E de navegador de calendário/bloqueios/prévia de horários/publicação/PIX/catálogo/menu e revisão visual/acessibilidade ainda pendentes. A titular confirmou acesso real ao superadmin com MFA e testou o perfil/logo.

## Backlog: concluído e parcial

- **MVP-005:** Maven/Wrapper/lockfile/typecheck disponíveis. Lint/formatter e pipeline completo pendentes.
- **MVP-010:** usuário, membership, token, sessão, PlatformRole e MFA TOTP implementados.
- **MVP-011/012/013:** identidade administrativa, reenvio, recuperação/revogação, cookies, CSRF e autorização por membership implementados/testados.
- **MVP-014:** RLS nas tabelas de negócio atuais; ampliar nas próximas migrations. Identidade, sessões, fila e limites são infraestrutura global sem CRUD público.
- **MVP-015:** outbox com estados, retries, limpeza e SMTP local validado. SES, alertas e console operacional autenticado pendentes.
- **MVP-016:** shell/erros/correlation ID implementados; telemetria e observabilidade completas ainda pendentes.
- **MVP-020/021/022/023/024:** planos, trial, banners, eventos, BillingDecision, auditoria administrativa e expiração disponíveis. Retenção/exportação operacional para homologação.
- **MVP-025/026:** bloqueio real sobre perfil e consultas essenciais preservadas. Publicação e reserva temporária integradas; pagamento ainda pendente.
- **MVP-027/029:** reativação por pagamento, suspensão/retirada, consulta e auditoria no painel de plataforma com MFA concluídas. Sem cancelamento ou impersonação neste painel mínimo.
- **MVP-028:** email único impede novo trial na mesma conta. Documento/telefone ainda não são coletados.
- **MVP-030/031:** perfil, contato, modalidade/local, progresso e upload/remoção de logo implementados. Variante saneada exposta na página publicada.
- **MVP-032:** catálogo administrativo funcional e testado, integrado à publicação e aos snapshots da reserva temporária.
- **MVP-033:** configuração PIX/política, validação local, auditoria e versões imutáveis concluídas. Referência/cópia por reserva implementada em V013, sem reescrever condições anteriores.
- **MVP-034/035:** página, prévia, requisitos reais, publicação explícita e retirada implementados. Jornada pública e solicitação temporária integradas.

- **MVP-040:** configuração de expediente, pausas, exceções e bloqueios concluída.
- **MVP-041:** gerador e prévia privada concluídos e integrados com alocações persistidas.
- **MVP-042/043:** base de alocação interna e bloqueios transacionais concluída. **MVP-045 parcial:** locks com expediente/fuso e HOLDs integrados ao Booking temporário; confirmação, cancelamento e reagendamento coordenados; demais operações de atendimento seguem em MVP-067.
- **MVP-044:** calendário visual mensal/semanal e lista diária implementado em 28/09; consulta por período e criação/liberação de bloqueios integradas.

## Próximas implementações

1. **Concluído nesta entrega — publicação (MVP-034/035):** requisitos reais e publicação explícita implementados. CTA de reserva depende de Booking; PIX permanece privado.
2. **Concluído — Compartilhamento (MVP-036):** aba com copiar link/mensagem, convite editável e abertura no WhatsApp, condicionada à publicação.
3. **Concluído — Clientes/reserva assistida (MVP-067/MVP-070) e agendamento por telefone/código:** validação descrita na entrega de 06/10. Números comerciais dos limites mensais ainda pendentes.
4. **Financeiro MVP-069 concluído em 13/10. Recuperação/reemissão implementada em 22/10; próxima etapa: homologação integrada e privacidade/segurança.** Central interna MVP-068 implementada; mensagens externas e limites comerciais dos planos continuam pendentes de decisão/configuração.
5. **Homologação:** E2E de navegador e MFA real, acessibilidade, SES, ingress confiável/limites por IP real, métricas/alertas, retenção de histórico, backup/restore e deploy.

### Onde entram as funções solicitadas pela responsável

| Função | Situação / etapa |
|---|---|
| Acesso superadmin | Implementado e acesso com autenticador confirmado pela responsável |
| Página com logomarca do profissional | Página, prévia e publicação explícita com requisitos reais implementadas |
| Calendário e disponibilidade | Configuração, prévia, alocações e calendário MVP-040 a 044 concluídos; reservas, confirmação, cancelamento e reagendamento integrados |
| Aba de cadastrar clientes | Implementada, com cadastro/edição/busca/histórico e reserva assistida (MVP-070/067) |
| Financeiro do profissional | Aba consolidada Financeiro implementada (MVP-069), com movimentações, pendências e filtros, separada do billing da plataforma |
| Compartilhar link e mensagem no WhatsApp | MVP-036 implementado; copiar link/mensagem e abrir WhatsApp, sem envio automático |

## Limitações e execução

- O limite por conexão fica compartilhado quando a API está atrás do proxy Next. Antes de publicar, configurar ingress confiável; não aceitar X-Forwarded-For público como autoridade.
- SMTP aceita a mensagem, mas não garante entrega na caixa final de um provedor externo. Uma interrupção entre envio e commit pode duplicar email; token continua de uso único.
- V004 expira emails antigos ainda pendentes, que não tinham vínculo confiável ao token. Contas/perfis/trials são preservados; pedir novo link em `/verificar`.
- Verificação local registrada em 06/10: migração V024 aplicada com dados existentes preservados; API na porta 8080 com health UP e frontend na porta 3000 com resposta HTTP 200 em `/entrar`. Docker/PostgreSQL/Mailpit e aplicações iniciados com os dados existentes preservados. Para iniciar novamente, usar `powershell -NoProfile -File infra/local/start-api.ps1` na raiz; não iniciar outra cópia se a porta 8080 estiver ocupada. Nenhum banco do usuário foi apagado; testes usam containers descartáveis.
- Agenda da home é ilustrativa. Página, prévia e publicação funcional estão implementadas, com reservas temporárias. Comprovantes privados, conferência PIX, confirmação e cancelamento estão implementados. Aba Financeiro consolidada implementada em 13/10; deploy continua pendente. Aba Clientes implementada. Perfil/logo e serviços já estão disponíveis nas respectivas áreas do painel.
- Cadastro de serviço por POST não é idempotente; após falha de rede, verificar a lista antes de repetir. Interface desabilita envio em andamento. Reservas temporárias já usam chave idempotente própria (MVP-053).
- Variantes pequenas de logo ficam no banco nesta etapa (ADR-0006); revisar volume/backup antes de escalar. Imagens com orientação EXIF devem ser exportadas na orientação desejada. Não há publicação automática ao completar o perfil.
- Recuperação de MFA é operacional/auditada; não há backup codes ou reset público. A chave DPAPI depende deste usuário Windows; produção exige gestão de segredos própria.
- PIX: validação local não atesta registro/titularidade. Chaves ficam em tabelas privadas com RLS, sem cifragem adicional na aplicação; TLS, criptografia de volume/backup, acesso operacional e retenção de versões devem ser definidos no deploy. Não registrar corpos/chaves em logs.
- Credenciais padrão de banco/SMTP são locais. Nenhum email externo real ou cobrança foi realizado.

Execução: [desenvolvimento local](runbooks/desenvolvimento-local.md). Plataforma: [superadmin](runbooks/superadmin.md). Diagnóstico: [email indisponível](runbooks/email-indisponivel.md).
