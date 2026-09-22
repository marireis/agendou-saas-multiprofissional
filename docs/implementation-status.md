# Agendou - Status de implementação

**Atualizado em:** 22/09/2026. Desenvolvimento realizado diretamente na pasta original do projeto.

## Revisão do estado anterior

A documentação descrevia um scaffold, mas já existiam arquivos JDBC, contexto de tenant e testes de integração adicionados posteriormente. A revisão encontrou:

- Repositórios em memória e JDBC registrados simultaneamente.
- Dependências Testcontainers e suporte Flyway/PostgreSQL ausentes; testes `*IT` fora do ciclo de build.
- Configuração ainda conectando com a role de migração, apesar da role restrita criada em V002.
- Tenant aceito da URL sem autenticação/membership; confirmação pública de pagamento.
- Serialização de assinatura sem DTO próprio.
- Política de trial capaz de sobrescrever suspensão/cancelamento.
- Teste RLS configurando tenant em uma conexão e consultando em outra, sem provar o isolamento pretendido.

Esses pontos foram corrigidos. Na inspeção inicial a pasta não era um repositório Git; durante a execução passou a existir um repositório com o commit `652d9a7`. Nenhuma branch, commit ou publicação foi criada por esta execução.

## Implementado e validado nesta etapa

| Entrega | Evidência |
|---|---|
| Maven 3.9.9 instalado e JDK 21.0.5 validado | `mvnw.cmd --version`; Wrapper oficial 3.3.2 gerado |
| Persistência JDBC exclusiva | Repositório em memória removido; Flyway e runtime separados |
| Contexto transacional e RLS | Runtime sem BYPASSRLS; testes PostgreSQL 17; contexto obrigatório por transação |
| Cadastro de administrador | Usuário, membership, tenant, perfil, trial e outbox na mesma transação |
| Verificação de email | Token aleatório, hash, 15 minutos e uso único |
| Login/logout e sessão JDBC | Cookie HttpOnly/Secure/SameSite=Lax; CSRF; rotação do ID no login |
| Recuperação de senha | Token de uso único e revogação das sessões persistidas |
| Autorização por membership | Tenant derivado da identidade; acesso pela URL a outro tenant retorna 404 |
| Trial Premium de 7 dias | Expiração periódica e síncrona; lock por tenant; eventos persistidos |
| Bloqueio real | PATCH de perfil negado; consulta de perfil e assinatura preservada |
| Reativação interna | Pagamento no serviço reativa sem recriar tenant/perfil; não há autoativação pública |
| Perfil inicial | Nome, descrição e fuso IANA; consulta e alteração autenticadas |
| Frontend conectado | Cadastro, verificação, login, recuperação e painel com assinatura real e edição de perfil |
| Contrato/setup | OpenAPI atualizado, ADR-0003 e runbook local |

## Evidências de validação

| Comando/check | Resultado |
|---|---|
| `apps/api/mvnw.cmd verify` com Java 21 e Docker | PASS: 8 testes unitários + 5 testes de integração, 0 falhas, 0 ignorados |
| Migrations V001, V002, V003 em PostgreSQL 17 | PASS via Testcontainers |
| Jornada HTTP com Spring Security e cookie JDBC | PASS: cadastro, verificação, token reutilizado negado, login, tenant alheio negado, perfil, expiração, bloqueio, reativação, recuperação/revogação e logout |
| RLS com role real | PASS: leitura do próprio tenant, rejeição de tenant divergente, isolamento SQL em uma mesma transação e negação sem contexto |
| `npm run build` | PASS: páginas de cadastro, login, verificação, recuperação e painel |
| `npm run typecheck` | PASS: TypeScript sem erros |
| `npm install --package-lock-only --ignore-scripts` | PASS: versões instaladas fixadas; 0 vulnerabilidades reportadas nessa execução |

Relatórios completos em `apps/api/target/surefire-reports` e `apps/api/target/failsafe-reports`. Logs locais em `apps/api/verification-final.log`. PASS técnico não equivale a homologação de negócio.

## Backlog: alcance real

- **MVP-005:** Wrapper e lockfile concluídos; lint/formatter e setup completo de produção ainda pendentes.
- **MVP-010/011/012/013:** identidade administrativa, sessão, recuperação, CSRF e membership implementados; PlatformRole/MFA e reenvio de verificação ainda pendentes.
- **MVP-014:** RLS nas tabelas de negócio existentes; ampliar a cada nova tabela.
- **MVP-015:** outbox SMTP com retries e Mailpit configurado; entrega real de email/SES e operação de falhas ainda não homologadas.
- **MVP-016/023:** painel com assinatura e estados reais; observabilidade/correlation ID ponta a ponta ainda pendente.
- **MVP-020/021/022/024:** planos, trial, eventos e expiração implementados; BillingDecision e auditoria administrativa completa ainda pendentes.
- **MVP-025/026:** bloqueio aplicado ao perfil atual e consultas preservadas. Reservas/publicação/pagamento terão enforcement ao serem implementados.
- **MVP-027:** serviço interno testado; falta fluxo administrativo autorizado com MFA e evidência da conferência.
- **MVP-028:** email único impede novo cadastro/trial para a mesma conta; documento/telefone ainda não fazem parte do cadastro.
- **MVP-030:** perfil inicial; contato, modalidade/local e progresso de onboarding pendentes.

## Próximas implementações

1. Completar identidade: reenvio de verificação, rate limits e tratamento operacional da outbox.
2. Criar PlatformRole/MFA e decisão auditada de pagamento/reativação; não reabrir confirmação pública.
3. Implementar serviços, PIX, política e perfil público com critérios de publicação (MVP-030 a 035).
4. Disponibilidade, alocação GiST e testes de concorrência (MVP-040 a 045).
5. Reserva, acesso de cliente, idempotência, quota e expiração (MVP-050 a 056).
6. Comprovantes privados, conferência e operação de PIX manual (MVP-060 a 068).
7. E2E de navegador, acessibilidade, observabilidade, backup/restore e homologação antes de piloto.

O produto completo ainda não está pronto para produção. Não foram implementados reservas, agenda, uploads, conferência PIX, plataforma com MFA ou deploy. Nenhuma cobrança ou mensagem externa real foi enviada. Credenciais fixas da V002/Compose são exclusivas de desenvolvimento local.

Instruções de execução: [desenvolvimento local](runbooks/desenvolvimento-local.md).

## Atualizacao visual do frontend

Home redesenhada a partir da referencia ClipCraft indicada pelo usuario: tema escuro, verde-agua, slogan 'Seu cliente agenda. Voce cuida do seu negocio.', previa ilustrativa, vantagens, como funciona e perguntas frequentes. Logo original preservada em assets/agendou-logo.svg; variante para fundo escuro em apps/web/public/agendou-logo-dark.svg. Cadastro, login e painel compartilham a marca e os estilos. Build Next.js aprovado e verificacao visual desktop/mobile realizada, incluindo logo, ausencia de overflow horizontal e link de cadastro. A ilustracao de agenda nao representa reserva funcional implementada.

