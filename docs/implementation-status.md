# Agendou - Status de implementacao

**Data:** 22/09/2026  
**Base:** `exports/DELTA_Agendou_DUAL_2026-09-22` e `docs/`.

## Capability de codigo aplicada

Esta Task envolve desenvolvimento de software. A capability recomendada e explicitamente referenciada e o TLC Spec-Driven Development, variante especializada Bradesco, datada de 07/07/2026. Nesta execucao, ela foi aplicada como disciplina de saida: escopo rastreavel, artefatos de engenharia, criterios e validacao separados. Specs geradas ou codigo inicial nao representam implementacao final validada; PASS tecnico nao equivale a VERIFIED.

## O que foi implementado

| Item | Estado | Arquivos |
|---|---|---|
| Estrutura base do repositorio | Feito | `apps/api`, `apps/web`, `contracts`, `infra/local`, `docs/adr` |
| Backend Spring Boot inicial | Feito | `apps/api/pom.xml`, `ApiApplication`, `HealthController` |
| Dominio inicial de trial | Feito | `PlanCode`, `SubscriptionStatus`, `Subscription`, `TrialPolicy`, `SubscriptionService` |
| API inicial de assinatura | Feito | `SubscriptionController` |
| Migration inicial | Feito | `V001__identity_billing_foundation.sql` |
| Testes unitarios planejados para trial | Feito | `TrialPolicyTest` |
| Contrato OpenAPI inicial | Feito | `contracts/openapi.yaml` |
| Frontend Next.js responsivo | Feito | `apps/web/app/page.tsx`, `globals.css`, `layout.tsx` |
| Dependencias web instaladas | Feito | `apps/web/package-lock.json`, `node_modules` local |
| Infra local | Feito | `infra/local/compose.yaml` |
| ADRs iniciais | Feito | `ADR-0001`, `ADR-0002` |

## Backlog atualizado

### Concluido nesta etapa

- [x] **MVP-001:** registrar ADRs de stack, monolito modular, trial Premium/Top, bloqueio pos-trial e preservacao de dados.
- [x] **MVP-003:** criar OpenAPI inicial com padrao inicial de endpoints de assinatura.
- [x] **MVP-004:** inicializar repositorio com `apps/api`, `apps/web`, `contracts`, `infra`, `docs` e base de testes.
- [x] **MVP-020:** criar base de Plan, Subscription e eventos no schema inicial.
- [x] **MVP-021:** cadastrar planos Basico, Intermediario e Premium/Top na migration, com trial permitido somente no Premium/Top.
- [x] **MVP-022:** implementar politica de `TRIAL_ACTIVE` por 7 dias no dominio.
- [x] **MVP-023:** criar primeira tela responsiva exibindo trial e proposta de valor.
- [x] **MVP-027:** implementar caminho base de confirmacao de pagamento para `PAID_ACTIVE` preservando tenant.

### Parcialmente concluido

- [~] **MVP-005:** Compose local e lockfile web criados; Maven Wrapper ainda depende da proxima etapa.
- [~] **MVP-010:** entidades de assinatura e tenant iniciadas; identidade completa ainda nao implementada.
- [~] **MVP-024:** regra de expiracao existe no dominio; worker agendado ainda nao implementado.
- [~] **MVP-025:** estado bloqueado existe; enforcement em todas as rotas ainda nao implementado.

### Proxima etapa recomendada

1. Instalar/validar Maven ou adicionar Maven Wrapper oficial.
2. Persistir `SubscriptionRepository` em PostgreSQL com JDBC.
3. Implementar `TenantContext` com `SET LOCAL app.tenant_id` por transacao.
4. Criar autenticação admin minima: cadastro, verificacao de email, login e sessao.
5. Aplicar bloqueio `TRIAL_EXPIRED_BLOCKED` em endpoints reais, nao apenas na UI.
6. Trocar repositorio em memoria por persistencia e adicionar testes Testcontainers.
7. Conectar frontend ao endpoint `/api/v1/subscriptions/{tenantId}`.

## Validacao executada

| Check | Resultado | Observacao |
|---|---|---|
| `cd apps/web && npm install` | PASS | 28 pacotes instalados, 0 vulnerabilidades reportadas pelo npm. |
| `cd apps/web && npm run build` | PASS | Build Next.js concluido com sucesso. |
| `javac --release 21` no dominio puro de billing | PASS | Compilou `PlanCode`, `SubscriptionStatus`, `Subscription`, `TrialPolicy` e `SubscriptionRepository`. |
| `cd apps/api && mvn test` | BLOQUEADO | Maven nao esta disponivel no ambiente atual. |

## Validacao pendente

- `cd apps/api && mvn test` quando Maven estiver disponivel.
- Teste manual futuro: criar trial, avancar relogio, bloquear tenant e reativar com pagamento.

## Limitacoes atuais

- Maven nao estava disponivel no ambiente no momento do scaffold.
- Java local encontrado e OpenJDK 25, enquanto o alvo do projeto e Java 21.
- Backend ainda usa repositorio em memoria para a primeira fatia.
- A implementacao ainda nao cobre reserva, PIX manual, agenda, isolamento completo e login.
