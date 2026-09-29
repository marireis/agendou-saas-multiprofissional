# Desenvolvimento local - Windows

Execute na pasta original do Agendou. JDK 21 e Docker Desktop sao necessarios.

## Backend

Com superadmin/MFA, prefira iniciar pela raiz: `powershell -NoProfile -File infra/local/start-api.ps1`. Se estiver em `apps/api`, use `powershell -NoProfile -File ../../infra/local/start-api.ps1`. O script configura Java e reutiliza a chave MFA protegida pelo Windows. Detalhes em [superadmin](superadmin.md).

```powershell
$env:JAVA_HOME = 'C:\Users\maryn\.jdks\corretto-21.0.5'
docker compose -f infra/local/compose.yaml up -d postgres mailpit
cd apps/api
$env:AGENDOU_SECURE_COOKIE = 'false' # apenas HTTP local
.\mvnw.cmd spring-boot:run
```

Maven 3.9.9 foi instalado no cache do usuario. O Wrapper oficial baixa e seleciona a mesma versao; nao depende de `mvn` no PATH. A API usa a role `agendou_runtime`; o Flyway usa `agendou_app`. Nunca trocar a conexao de runtime pela role de migracao para contornar RLS.

## Frontend (outro terminal)

```powershell
cd apps/web
npm ci
npm run dev
```

Abrir http://localhost:3000/cadastro. Criar conta de teste, abrir o email no Mailpit em http://localhost:8025, confirmar e entrar. O painel permite consultar assinatura e editar nome, descricao e fuso. A pagina publica e a reserva ainda nao estao implementadas.

Next encaminha `/api/*` para localhost:8080, preservando a mesma origem do cookie. `AGENDOU_API_URL` altera o destino. `AGENDOU_PUBLIC_URL` configura a origem dos links de email. Nao colocar segredos no frontend.

## Validacao

```powershell
cd apps/api
.\mvnw.cmd verify
cd ../web
npm run typecheck
npm run build
```

`test` executa testes unitarios. `verify` tambem executa os testes `*IT` em PostgreSQL 17 com Testcontainers; nao os ignora quando Docker esta indisponivel. `src/test/resources/docker-java.properties` seleciona API 1.44 para compatibilidade com o Docker instalado (configuracao suportada pelo [docker-java](https://github.com/docker-java/docker-java/blob/main/docs/getting_started.md)).

## Configuracao

- Banco: `AGENDOU_DATABASE_URL`, `AGENDOU_DATABASE_USER`, `AGENDOU_DATABASE_PASSWORD`.
- Migracao: `AGENDOU_MIGRATION_USER`, `AGENDOU_MIGRATION_PASSWORD`.
- SMTP: `AGENDOU_MAIL_HOST`, `AGENDOU_MAIL_PORT`, `AGENDOU_MAIL_FROM`.
- Cookies Secure ficam habilitados por padrao. Desabilitar somente no localhost HTTP.
- As credenciais fixas existentes na V002/Compose sao locais; provisionar e rotacionar secrets antes de homologacao.
- Nao executar `docker compose down -v` se quiser preservar os dados.

Os testes de integracao usam bancos descartaveis e emails sinteticos. A jornada testa expiracao alterando datas somente nesse banco, sem endpoints de teste na aplicacao.

## Atualizacao de 23/09/2026

Atualização 25/09: V008 adiciona configuração PIX/política em `/painel/pagamentos`. Use uma chave já registrada no banco; confira tipo, recebedor e regras antes de salvar. Dados são privados nesta etapa. Desativar preserva histórico; erro 409 exige recarregar e revisar. Nunca inclua chave em logs, prints de suporte ou motivo livre. Não há consulta bancária, QR Code ou pagamento automático. Versões antigas são preservadas para futura referência de reservas; retenção/backup devem incluir esses dados privados.

Catálogo: V007 adiciona serviços. `/painel` agora é a visão geral; perfil/logo ficam em `/painel/minha-pagina`, catálogo em `/painel/servicos` e vigência em `/painel/assinatura`. Acesse Serviços → Novo serviço; para inativar, abra Editar e desmarque Serviço ativo. Se receber conflito de edição, recarregue a lista e abra o registro novamente. Reiniciar a API aplica a migration sem apagar os dados anteriores.

Perfil e marca: V006 adiciona contato comercial, modalidade/local, progresso e logomarca. Em `/painel`, salve o rascunho do perfil ou envie uma imagem PNG/JPEG até 2 MiB. A logo é opcional; a prévia é privada. Arquivos SVG/GIF não são aceitos. Se o upload falhar, a imagem anterior é preservada. Trial vencido permite consultar, mas impede editar/remover. O banco guarda apenas o PNG saneado, incluído no backup do perfil (ADR-0006). Reinicie a API após atualizar o código; não basta recarregar o navegador.

Reinicie a API pelo script acima para aplicar V004/V005 via Flyway e carregar reenvio, limites e plataforma/MFA. Emails antigos ainda pendentes sao expirados na V004; solicitar outro em /verificar. Contas e configuracoes sao preservadas. Veja [email indisponivel](email-indisponivel.md) para diagnostico. O procedimento local de 23/09 aplicou V004/V005 no banco existente sem apagar volumes.
# Página e publicação — atualização de 25/09/2026

Acesse `/painel/publicacao` com sua conta para conferir a prévia privada e requisitos. O backend aplica V009 ao iniciar pelo script local. Todos os perfis começam não publicados; `/a/{slug}` retorna página indisponível até liberação explícita em entrega futura. Não alterar `published` manualmente no banco real para contornar a disponibilidade. Testes de projeção usam somente bancos descartáveis. Nesta entrega, health UP, rota do painel 200, slug inexistente 404, Maven verify (62 testes), build e typecheck passaram. Revisão visual pela responsável ainda pendente.
# Horários — atualização de 25/09/2026

Revisão de interface: use “Configurar vários dias”, selecione os dias, ajuste os períodos e clique em “Aplicar aos dias selecionados”. Para folga semanal use “Marcar como folga”. Confira Sua semana e salve. Para férias/ausência, informe primeiro e último dia em Ausências; o fim é inclusivo e pode ficar vazio para um único dia. Datas já configuradas exigem ajuste individual, evitando sobrescrita silenciosa. “Configurar dia por dia” mantém o editor individual.

Em `/painel/horarios`, configure a semana e salve. Para pausa de almoço, crie dois períodos; para feriado, adicione uma data especial sem períodos. Exceção substitui o dia inteiro, não soma horários ao expediente semanal. O fuso é o de Minha página. A migração V010 foi aplicada localmente sem excluir dados. API health UP e rota do painel 200; 65 testes, build e typecheck passaram. Revisão visual/manual de horários ainda pendente. Publicação continua aguardando geração e alocação reais; salvar expediente não libera reservas.
# Prévia de horários — atualização de 25/09/2026

Salve o expediente e abra “Confira como fica o seu dia” no fim da aba Horários. Selecione serviço ativo e data entre hoje e os próximos 59 dias no fuso do perfil. Clique em Calcular horários. A sequência respeita duração/intervalo/buffers; “todos os inícios possíveis” mostra alternativas que podem se sobrepor. Antecedência mínima de 24h. Ausências e pausas reduzem a lista; períodos que cruzam transição de offset são omitidos. Não cria reservas nem verifica alocações persistidas ainda. Backend atualizado, health UP e painel HTTP 200; 74 testes, build e typecheck passaram (`slots-verify.log`, `slots-build.log`). Homologação visual/manual pendente.
# Bloqueios pontuais e alocações — atualização de 25/09/2026

Na aba Horários, use “Bloquear um período específico”, informando início/fim no fuso exibido e motivo privado. Sobreposição com outro bloqueio/ocupação retorna conflito; confira a lista antes de repetir após falha de rede. “Liberar período” desativa preservando histórico. A prévia é recalculada após nova consulta e já considera os bloqueios. Não há endpoint público para reservas/HOLD. O núcleo expira ocupações temporárias antes de novas mutações, e leituras ignoram expiradas imediatamente. V011 aplicada localmente, API health UP, painel HTTP 200. Suíte completa: 79 testes, build e typecheck passaram (`calendar-verify.log`, `calendar-build.log`). Revisão visual/manual pendente; próxima etapa calendário diário/semanal.
# Calendário administrativo — atualização de 28/09/2026

Acesse `/painel/calendario`. Desktop abre na semana e celular no dia; use Dia/Semana, seletor, anterior/próxima e Hoje. Horários e datas seguem o fuso do perfil. Expediente exibido é a configuração atual, mesmo para datas passadas. Bloqueios que atravessam dias aparecem em cada dia afetado; ocupações temporárias são identificadas e não significam reservas confirmadas. Criar/liberar bloqueio atualiza a consulta, além da atualização automática a cada minuto. Consulta continua após bloqueio da assinatura; criação/liberação permanece restrita. Sem drag-and-drop.

Sem nova migration (V011). Docker estava fechado na primeira tentativa de testes; após iniciar, a suíte completa passou: 27 unitários + 55 de integração = 82. Build e typecheck aprovados (`calendar-view-verify.log`, `calendar-view-build.log`). Backend health UP e rota frontend HTTP 200 em 28/09. Homologação visual/acessibilidade no navegador ainda pendente. Próxima entrega: critérios reais de publicação da página.

### Atualização: calendário mensal e tema (28/09)
Sem migration. Verify: 27 unitários + 56 integração = 83, sem falhas/ignorados (calendar-theme-verify.log). Build (calendar-theme-build.log) e typecheck aprovados. Tema claro/escuro e persistência após recarregar verificados no login; calendário autenticado ainda requer homologação visual. Reinicie a API para suportar days=42 e os indicadores. Preferência de tema é local ao navegador.

### Publicação explícita (28/09)
V011 mantida, sem migration. 86 testes (27 unitários + 59 integração), build e typecheck aprovados: publication-verify.log e publication-build.log. Reinício pelo script infra/local/start-api.ps1, log publication-local.log. Na aba Publicação, completar pendências e clicar Publicar página; Ver página pública abre o endereço local. Compartilhar na internet depende do deploy, ainda pendente. Não publica nenhuma conta automaticamente. Testes usam contas sintéticas em containers; homologação visual autenticada pendente.

### Jornada pública do cliente (28/09)
Migração V012 aplicada localmente pelo start-api.ps1, log customer-local.log; health UP. Fluxo em /a/{slug}/agendar exige página publicada, assinatura e PIX ativos. Selecionar serviço/data/horário, informar nome/email e abrir o link no Mailpit local. O fragmento token é consumido somente após clicar Verificar email e revisar. A revisão não cria reserva; próximos MVP-052/053/054. Relatórios finais: 27 unitários + 63 integrações aprovados após corrigir teste de sessão e reexecutar PublicPageJourneyIT (10 testes). Build e typecheck aprovados. Logs customer-verify.log (primeira execução), customer-journey-verify.log (reexecução aprovada), customer-build.log. Token único/expirado, escopo, RLS, assinatura, CSRF e outbox cobertos; email externo/E2E visual pendentes.

### Reserva temporária (29/09)
V013 aplicada pelo start-api.ps1, preservando dados locais (booking-local.log). Na revisão, confira valores/política, marque o aceite e clique Solicitar reserva temporária. Recibo mostra protocolo e prazo de 30 minutos; recarregar recupera a solicitação recente na mesma sessão. Calendário profissional mostra Solicitação pendente. Ainda não há confirmação/comprovante/pagamento habilitado; não realizar transferência. Worker periódico e consultas liberam horário/quota após expiração. SMTP local usa Mailpit, sem envio externo.

Limites comerciais permanecem sem teto até definição; coluna plans.monthly_booking_limit configura cada plano, null ilimitado/zero bloqueia novas reservas. Sugestão pendente: 50/200/ilimitado. Contagem pelo mês do atendimento no fuso do profissional. Não editar migration aplicada; eventual definição deve usar nova migration. Próxima tarefa: Meus agendamentos, depois completar operação da expiração e PIX.

Relatórios finais: 27 unitários + 68 integrações sem falhas. Execução inicial teve erro na fixture Básico/trial, corrigido; reexecução PublicPageJourneyIT/CalendarJourneyIT passou 24 testes (booking-final-verify.log). Build booking-build.log e typecheck aprovados. Homologação completa no navegador permanece pendente.

### Meus agendamentos (29/09)
V014 aplicada localmente preservando dados. Backend atualizado na porta 8080, health UP (portal-local.log). Na página pública, abra Meus agendamentos; use o email da reserva e abra o link pelo Mailpit. Clique Acessar meus agendamentos. Lista/detalhe mostram snapshots e prazo; saída encerra o acesso de cliente. Criar reserva abre esse acesso na mesma sessão. Consulta funciona com página retirada ou assinatura bloqueada. Nome/sobrenome/telefone substituirão o email em ajuste posterior solicitado e adiado pela responsável.
Validação desta etapa: 27 unitários e 18 integrações de PublicPageJourneyIT, build e typecheck aprovados. Tela de acesso conferida no navegador; consulta autenticada completa por navegador/email externo pendente. Próxima tarefa: completar operação de expiração MVP-056.

### Expiração coordenada (29/09)
V015 substitui varredura de memberships por reivindicação de tenants com reservas vencidas. Ciclo padrão 60s (agendou.booking-expiration-delay), até 20 lotes de 100. Lease de 2 minutos; falhas usam espera progressiva de 30s a 15min, sem descarte terminal. Não limpar leases manualmente enquanto houver execução ativa. Logs booking_expiration_completed, booking_expiration_failed e booking_expiration_retry_deferred mostram tenant/quantidade, sem dados pessoais.
Diagnóstico com conexão operacional autorizada: consultar failures, next_attempt_at e leased_until em booking_expiration_jobs. Runtime não tem SELECT direto nessa fila. Corrigir causa de falha no banco/aplicação e aguardar nova tentativa; uma execução interrompida recupera pelo prazo do lease. Ainda faltam alertas de produção. Pagamentos futuros devem coordenar transições com o mesmo lock do tenant.
Validação: 27 unitários e 30 integrações passaram (expiration-verify.log). Reexecução do cenário de disputa simultânea de lease passou (expiration-concurrency-verify.log). Sem alterações no frontend; nenhum build adicional. Backend local usa start-api.ps1, log expiration-local.log. Próxima tarefa: base PIX manual/tela MVP-060/061.

### Base PIX e prévia (29/09)
V016 cria intenção para reservas existentes sem renovar prazo; novas reservas criam intenção atomicamente. No recibo ou detalhe de Meus agendamentos, Ver dados PIX da reserva abre valores, chave/recebedor e prazo preservados. Copiar chave tem seleção manual como alternativa. Ao expirar, a chave é ocultada. Tela está em prévia: não realizar transferência; comprovantes/conferência ainda pendentes.
27 unitários e 27 integrações aprovados (pix-foundation-verify.log); build e typecheck aprovados (pix-foundation-build.log). Homologação visual autenticada/clipboard pendente. Reinício pelo start-api.ps1, log pix-foundation-local.log. Próxima tarefa: MVP-062/063, comprovantes privados e EmConferencia.
