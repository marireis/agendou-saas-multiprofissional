# Matriz de testes automatizados

Esta matriz relaciona os fluxos implementados às suítes existentes. Aprovação significa que os cenários e asserções executados passaram; não é garantia de ausência de defeitos em todas as combinações possíveis. Resultados e pendências da última execução ficam em `implementation-status.md`.

| Fluxo | Evidência automatizada |
| --- | --- |
| Cadastro, verificação, login, sessão, recuperação e bloqueios | `IdentityJourneyIT`, `IdentityHardeningIT` |
| Superadmin, MFA, replay, expiração e decisões auditadas | `PlatformJourneyIT`, `TotpTest` |
| Trial, assinatura, vencimento e reativação | `JdbcSubscriptionRepositoryIT`, `IdentityJourneyIT`, `PlatformJourneyIT` |
| Perfil, logomarca, catálogo e PIX | `ProfileJourneyIT`, `ServiceJourneyIT`, `PaymentSettingsJourneyIT`, validadores unitários |
| Disponibilidade, exceções, intervalos e calendário | `AvailabilityJourneyIT`, `CalendarJourneyIT`, `SlotGeneratorTest` |
| Publicação e página pública sem login | `PublicPageJourneyIT` |
| Reserva sem email, código privado e consulta por identidade | `PublicPageJourneyIT`, `CustomerContactTest` |
| Clientes, reserva assistida e isolamento | `PublicPageJourneyIT` |
| Comprovante, limite/tipo de arquivo, conferência e confirmação | `PublicPageJourneyIT`, `EvidenceValidatorTest`, `S3EvidenceStorageIT` |
| Cancelamento, reagendamento, conclusão/falta, devolução e expiração | `PublicPageJourneyIT` |
| Financeiro, paginação, notificações e leitura individual | `PublicPageJourneyIT` |
| Reemissão de código, revogação e auditoria | `PublicPageJourneyIT` |
| Concorrência, idempotência, rollback, RLS e CSRF | Suítes de jornada, repositório e `TenantContextInterceptorTest` |
| Backup de esquema/dados, permissões e objeto privado | `BackupRestoreIT` |
| Reserva/comprovante vinculados, restauração e API reiniciada | `BookingRecoveryIT` |
| Cabeçalhos HTML e recusa de acesso privado via proxy | `infra/local/verify-http-security.ps1` |
| Jornadas Chromium desktop/mobile emulado, teclado, temas e Axe | `BrowserJourneyE2E`, `apps/web/e2e/journeys.spec.ts`; execução separada via `npm run test:e2e` |
| Compilação do frontend e tipos | `npm run build` em `apps/web` |

## Resultado em 26/10/2026

Regressão completa aprovada novamente: 36 unitários + 108 integrações, zero falhas/erros/ignorados; `apps/api/regression-20261026.log`, BUILD SUCCESS às 09:00. Execução separada de navegador aprovada: 10 cenários (5 desktop + 5 mobile emulado), build/TypeScript e asserções do harness sobre os registros no banco. `apps/web/browser-validation.log`, BUILD SUCCESS às 08:55. As repetições dos 36 unitários no comando de navegador não são testes distintos.

## Resultado histórico em 22/10/2026

144 testes distintos aprovados (36 unitários + 108 integrações), zero falhas/erros/ignorados. Log final: `apps/api/integration-regression-final.log`, BUILD SUCCESS às 19:45. TypeScript (`apps/web/integration-typecheck.log`) e sete checks HTTP (`.local/integration-http-check.log`) aprovados. Esses resultados não incluem E2E de navegador.

## Reexecutar

Com Docker Desktop ativo, imagens locais disponíveis e Java 21, em `apps/api`:

```powershell
$env:JAVA_HOME='C:\Users\maryn\.jdks\corretto-21.0.5'
.\mvnw.cmd verify
```

Para selecionar a integração de recuperação, mantendo os unitários:

```powershell
.\mvnw.cmd '-Dit.test=BookingRecoveryIT' verify
```

Relatórios JUnit ficam em `apps/api/target/surefire-reports` e `apps/api/target/failsafe-reports`. Conferir o horário da execução e o log completo; relatórios antigos ou execuções seletivas não comprovam uma regressão completa atual. Não contar cenários executados novamente como novos testes distintos.

## Encerramento dos recursos de teste

As jornadas Spring herdam `IsolatedSpringIntegrationTest`: `@DirtiesContext(AFTER_CLASS)` solicita ao Spring o fechamento e a remoção do contexto do cache ao fim de cada classe, preservando a ordem dos callbacks do framework. Isso encerra pools e tarefas agendadas, evitando consultas contra bancos já removidos e espera de encerramento no fim da JVM. Os dois testes de recuperação fecham seus contextos e contêineres explicitamente com try-with-resources.

## Limites de cobertura

A maior parte da regressão usa MockMvc com filtros de segurança e PostgreSQL real; algumas dependências são simuladas. `BookingRecoveryIT` usa MinIO real e HTTP real para código, sessão e download; configuração administrativa e conferência usam MockMvc com identidade sintética. Não simula transferência bancária real. A entrega SMTP é testada em Mailpit descartável, não em um provedor externo.

A suíte Playwright executa jornadas na interface com API, PostgreSQL e MinIO temporários reais; veja `runbooks/testes-navegador.md`. Cobre Chromium desktop e emulação Pixel 7, teclado e Axe em telas selecionadas. Não cobre todos os dispositivos/navegadores, leitor de tela humano nem todos os fluxos administrativos no navegador. MFA positivo e consumo do email de recuperação continuam cobertos no backend. Também não comprovam carga de produção, envio WhatsApp/SMS, recuperação de chave MFA em outro host ou procedimentos de exportação/exclusão ainda não implementados. Esses itens devem permanecer abertos até terem evidência própria.
