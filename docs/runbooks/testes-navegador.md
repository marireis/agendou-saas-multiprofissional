# Testes de navegador em ambiente isolado

## Executar

Requisitos: Docker Desktop ativo, Java 21/Maven Wrapper, Node.js e a imagem `agendou-minio-local:7aac2a2` (a mesma de `infra/local/compose.yaml`). Na pasta `apps/web`:

```powershell
npm ci
npx playwright install chromium
npm run test:e2e
```

O comando usa `infra/local/test-browser.ps1` e seleciona `BrowserJourneyE2E` no Failsafe. Esse teste não entra no `mvnw.cmd verify` normal: a regressão de backend pode rodar sem navegador instalado. O script também executa os testes unitários; não contar suas repetições como testes novos.

## Isolamento

O harness Java cria PostgreSQL e MinIO temporários, aplica migrations, prepara duas contas sintéticas verificadas e inicia uma API em porta aleatória. Playwright compila um build de produção em `.next-e2e` e inicia outro frontend em porta aleatória, com proxy para essa API. Não utiliza os volumes ou contas do ambiente local em 3000/8080. A preparação cadastral ocorre por serviços/MockMvc e SQL exclusivamente no banco descartável; as ações verificadas nas jornadas passam pela interface real.

Fixture e comprovante ficam em `apps/api/target`; relatórios, screenshots e traces em `apps/web/test-results` e `apps/web/playwright-report`. São ignorados pelo Git. Contêm somente dados sintéticos, inclusive códigos de acesso gerados para o ensaio. O processo fecha serviços temporários ao terminar. Não executar `test:e2e:browser` diretamente contra dados reais; a configuração exige as variáveis criadas pelo harness.

## Cenários

Cada cenário roda em Chromium desktop e emulação Pixel 7, com locale pt-BR e fuso America/Sao_Paulo:

- Login inválido/válido, teclado, logout, bloqueio de área privada e persistência de tema.
- Reserva pública sem login/email → revisão → código → nova sessão do cliente → código inválido/válido → upload/download de comprovante → login profissional → recebimento → cancelamento → registro de devolução → consulta atualizada e saída do cliente.
- Navegação pelas abas administrativas e cadastro/edição de cliente sem email.
- Resposta genérica à recuperação de senha e recusa de acesso à plataforma para profissional comum.
- Leitura pública concorrente limitada: 20 requisições, concorrência 4, statuses e p95 anexados por projeto. Não representa capacidade ou SLO de produção.

O harness confere no banco duas reservas canceladas, dois comprovantes, dois recebimentos e duas devoluções após as jornadas. Não há transferência bancária nem mensagem externa real.

Axe verifica regras WCAG A/AA nas telas selecionadas (entrada nos dois temas, painel e recibo/cadastro); isso não substitui revisão humana com leitor de tela. A emulação mobile não equivale a testar Safari/iPhone ou aparelho físico. Confirmação de email/recuperação completa e MFA positivo continuam cobertos por integrações backend, mas não por estes cenários de navegador.

## Evidências

- `apps/web/browser-validation.log`: Maven e resultado do harness.
- `apps/api/target/browser-e2e.log`: build Next.js e resultados Playwright.
- `apps/web/test-results/results.json`: resultados estruturados e medições de leitura.
- `apps/web/playwright-report/index.html`: relatório navegável.

Conferir data e término da execução. Uma falha interrompe a suíte para investigação; testes não executados não contam como aprovados. Consultar `docs/implementation-status.md` para o resultado validado mais recente e pendências.
