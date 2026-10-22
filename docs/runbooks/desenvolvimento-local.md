# Desenvolvimento local - Windows

Execute na pasta original do Agendou. JDK 21 e Docker Desktop sao necessarios.

## Backend

Com superadmin/MFA, prefira iniciar pela raiz: `powershell -NoProfile -File infra/local/start-api.ps1`. Se estiver em `apps/api`, use `powershell -NoProfile -File ../../infra/local/start-api.ps1`. O script configura Java e reutiliza a chave MFA protegida pelo Windows. Detalhes em [superadmin](superadmin.md).

```powershell
$env:JAVA_HOME = 'C:\Users\maryn\.jdks\corretto-21.0.5'
docker compose -f infra/local/compose.yaml up -d --build postgres mailpit minio
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

Abrir http://localhost:3000/cadastro. Criar conta de teste, abrir o email no Mailpit em http://localhost:8025, confirmar e entrar. O painel permite configurar perfil, serviços, horários e publicação. Reservas e acesso verificado do cliente estão implementados; confirmação manual do pagamento está disponível na aba Conferência.

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

### Comprovantes privados — 30/09/2026
V017 é aplicada ao reiniciar a API pelo script local. Docker Desktop deve estar aberto. Na raiz, execute `docker compose -f infra/local/compose.yaml up -d --build postgres mailpit minio`. A primeira compilação do MinIO usa código oficial fixado em revisão no Dockerfile e pode levar vários minutos; depois a imagem fica em cache. Antes de executar `mvnw.cmd verify` numa máquina nova, construa essa imagem: o teste S3EvidenceStorageIT usa `agendou-minio-local:7aac2a2`.

Storage local: API S3 em localhost:9000, console em localhost:9001, credenciais de desenvolvimento definidas no Compose. Variáveis da API: AGENDOU_STORAGE_ENDPOINT, AGENDOU_STORAGE_ACCESS_KEY, AGENDOU_STORAGE_SECRET_KEY e AGENDOU_STORAGE_BUCKET. Padrão: bucket agendou-evidence-private, criado no primeiro upload; não configure políticas públicas. Não use essas credenciais em produção. Produção requer endpoint TLS, credenciais próprias de menor privilégio, bloqueio de acesso público e revisão de segurança/processamento de arquivos. Backup deve incluir banco e objetos privados.

Em Meus agendamentos → detalhes/pagamento, envie JPEG/PNG/PDF até 5 MB. PDFs sem senha, até dez páginas e sem conteúdo ativo; imagens até 16 megapixels são reencodadas sem metadados. Máximo cinco arquivos por reserva. Primeiro envio válido abre Em conferência por 24h, preservando horário; outros arquivos não renovam o prazo. Download exige sessão proprietária. Arquivo nunca comprova recebimento automaticamente: transferência operacional permanece desabilitada até MVP-064/065.

503 no upload: verifique serviço MinIO, credenciais e bucket sem policy configurada. Rollback tenta remover o objeto novo; falha de processo pode deixar objeto privado órfão. Não há coletor automático: reconcilie inventário S3 com payment_evidence usando acesso operacional autorizado e janela sem uploads em andamento; nunca remova objetos referenciados. Revisão visual autenticada da interface permanece para homologação.

### Conferência e devoluções — 01/10/2026
Inicie os serviços Docker e reinicie a API usando infra/local/start-api.ps1; V018/V019 preservam dados existentes. Acesse `/painel/conferencia`. Reservas Em conferência aparecem primeiro. Abra a reserva, baixe o comprovante, confira o crédito no banco e registre valor, conta, referência e data. Valores no formulário são em reais; API/banco usam centavos. Data/hora de entrada usam o fuso do dispositivo, explicitado na tela; datas consultadas seguem o fuso do estabelecimento.

Entrada suficiente sem excedente e reserva válida confirma o atendimento. Calendário e Meus agendamentos passam a mostrar Confirmado. Parcial não renova prazo; excedente exige regularização. Recebimento registrado após expiração permanece associado ao histórico financeiro sem recuperar horário. Para devolver, realize a transferência no banco e registre sua referência/motivo/data, selecionando o crédito original. Não registre devolução antes de efetuá-la. O sistema apenas registra a operação externa.

409 de referência: consulte o histórico antes de tentar outra referência. Repetir todos os dados exatos retorna o lançamento existente; alterar dados com a mesma referência é rejeitado. Devolução não pode ultrapassar o saldo do crédito original. Devolução que comprometa a entrada de atendimento confirmado aguarda MVP-067 (cancelamento/política); não contorne isso editando o banco. Conta bloqueada mantém consultas mas não registros financeiros. Histórico inclui operador e correlation ID, sem edição/exclusão pela API. Não há email novo de confirmação nesta entrega; cliente consulta seu portal.

Validação desta entrega: 69 testes distintos (32 unitários, 28 PublicPageJourneyIT, 9 CalendarJourneyIT). Jornada final aprovada em review-public-verify.log; calendário aprovado em review-final-verify.log, cuja falha de fixture da jornada foi corrigida no rerun. Inclui rollback atômico, acesso cruzado negado, repetição sem duplicação, insuficiência/excedente, tardio sem reabertura, limite de devolução concorrente e bloqueio de escrita após trial. Build review-final-build.log e typecheck aprovados; V018/V019 aplicadas localmente, health UP e /painel/conferencia HTTP 200. Homologação visual autenticada pendente.

### Cancelamento profissional — 01/10/2026
Na aba Conferência, abra Gerenciar reserva → Cancelar atendimento. Leia a política original aceita pelo cliente, informe motivo visível ao cliente e valor a devolver (zero quando não houver), marque a revisão e confirme. A política é texto livre: a aplicação não calcula percentuais/prazos. Só é possível cancelar reserva ativa antes do início. Cancelamento libera o horário imediatamente no servidor; calendário reflete na próxima atualização.

O formulário compara o recebido líquido exibido com o atual. Em 409 por valores alterados, use Atualizar reserva e revise a decisão. Repetição idêntica é segura; decisão registrada não pode ser editada nesta etapa. Quota provisória é liberada; reserva já confirmada conserva quota comercial consumida. Dados financeiros, evidências e auditoria são preservados.

Se houver devolução pendente, faça no banco e registre pelo formulário existente, dentro do limite da decisão e do recebimento original. Crédito recebido depois do cancelamento aumenta a pendência, sem reabrir o horário. Portal mostra Cancelada, motivo, valor mantido e pendência; dados PIX e upload ficam ocultos. Não envia email novo nem executa transferência. Cancelamento pelo cliente ainda não disponível.

V020 aplicada ao reiniciar pelo script local. Testes: 32 unitários + 31 jornada + 9 calendário = 72 aprovados, incluindo rollback, acesso cruzado, saldo desatualizado, repetição, devolução planejada, pagamento posterior e reutilização do horário. Build/typecheck aprovados (cancellation-verify.log/cancellation-build.log). Homologação visual autenticada pendente.

### Reagendamento — 05/10/2026
Em Conferência → Gerenciar reserva → Reagendar o mesmo serviço, escolha a data, busque horários, selecione a opção e informe motivo visível ao cliente. Horários seguem o fuso da reserva, antecedência de 24h e horizonte de 60 dias. Valores, duração/buffers contratados, recebimentos e prazo original são mantidos; serviço inativado posteriormente ainda pode ser reagendado conforme contrato original. Horário antigo só é liberado se o novo puder ser reservado.

409: atualize a reserva/consulte horários; pode haver nova ocupação, alteração em outra janela ou quota do mês de destino atingida. Após falha de rede, repetir os mesmos dados/chave é seguro; consulte o histórico antes de criar outra alteração. Profissional e cliente veem datas anteriores/novas e motivo. Não envia email de alteração nesta entrega (MVP-068).

V021 aplica pelo script local. Reagendamento permitido apenas antes do início, em reserva ativa; expiradas/canceladas não são reabertas. Histórico preservado sob RLS; quota move de mês sem nova cobrança e preserva HELD/COMMITTED. Próxima etapa: concluir atendimento e marcar falta.

Validação de reagendamento: 75 testes (32 unitários + 34 jornada + 9 calendário), sem falhas/ignorados; inclui concorrência, conflito, rollback do histórico, repetição, versão desatualizada, quota de destino e duração/preço originais após mudança do catálogo. Build/typecheck aprovados; V021 aplicada localmente, API health UP e Conferência HTTP 200. Logs reschedule-final-verify.log, reschedule-build.log, reschedule-local.log. Homologação visual autenticada pendente.

### Concluir atendimento e marcar falta — 05/10/2026
Em Conferência → Gerenciar reserva, abra Concluir atendimento ou marcar falta. Após o término previsto, escolha o resultado, informe observação visível ao cliente e confirme. Somente atendimento confirmado pode ser encerrado; antes do término a API recusa, inclusive para falta. Se houve reagendamento, atualize a reserva e reveja o horário antes de registrar.

Resultado é definitivo nesta etapa: repetir os mesmos dados não duplica; resultado/observação diferente recebe conflito. Não há edição ou reabertura. Histórico no calendário e quota comercial são preservados. Encerramento não quita saldo, registra multa, cancela pagamento ou executa devolução. Lançamentos financeiros seguem na aba Conferência. Cliente consulta resultado/observação no portal; emails ainda pertencem à etapa MVP-068.

V022 aplica pelo script local. Validação: 78 testes (32 unitários + 37 jornada + 9 calendário), com término/status, versão desatualizada, CSRF/isolamento, repetição, decisões concorrentes e rollback da auditoria. Build e typecheck aprovados; logs outcomes-verify.log/outcomes-build.log. Homologação visual autenticada pendente.

## Agendamento público sem email — 06/10/2026

1. No painel, configure serviço, PIX/política e horários futuros; publique em Publicação. Copie o link em Compartilhar.
2. Abra `/a/{slug}/agendar` em janela anônima, sem entrar no painel. Selecione serviço, data e horário; informe nome, sobrenome e telefone com DDD. Revise valores/política e solicite a reserva temporária.
3. Copie o código privado exibido no recibo. O código autoriza somente essa reserva; não é o protocolo UUID. Não há envio por email/WhatsApp/SMS. O recibo fica recuperável na mesma sessão por uma hora, até sair.
4. Abra `/a/{slug}/meus-agendamentos` em outra janela. Informe os mesmos dados de contato e o código. Confira status/detalhe/PIX. Tentar outro código ou nome/telefone deve falhar. Reservas distintas exigem seus próprios códigos.
5. Na aba Clientes, consulte o cadastro e histórico; na Conferência, registre somente recebimentos efetivamente conferidos. A solicitação não confirma atendimento automaticamente. Use a seção de email em Meus agendamentos apenas para reservas antigas.
6. Reserva assistida: Clientes → Novo cliente, nome e sobrenome + telefone, email vazio → Ver cliente e agendar. Entregue ao cliente o código exibido ao finalizar. Sem o código, o profissional deve verificar o solicitante e consultar o atendimento; reemissão/recuperação automática de código ainda não existe.

V023/V024 preservam dados anteriores. Não mesclam histórico antigo de email com novo cadastro sem email. Edite contato administrativo com cuidado: acesso por código usa os dados originais de cada reserva. Não coloque códigos reais em prints/logs/documentação.

## Notificações internas (V025)

Após reiniciar a API pelo script local, entre com uma conta de administrador e abra `/painel/notificacoes`. Reservas já existentes também aparecem. Filtre não lidas, marque um aviso e confira que ele desaparece desse filtro; em Todos deve continuar como Lida. Outro administrador deve manter seu próprio estado de leitura. Ver atendimento deve abrir o detalhe correspondente na Conferência. Use Atualizar para buscar eventos novos; não há push nem mensagens externas nesta etapa.

A migração é aditiva e preserva reservas e códigos. Para validar tecnicamente, execute `mvnw.cmd verify -Dit.test=PublicPageJourneyIT` em `apps/api` com Docker ativo. A suíte cobre autorização, CSRF, isolamento, leitura individual/idempotente, cursor com timestamps iguais e jornada de reservas/pagamentos. Build em `apps/web` verifica a rota nova. A revisão visual autenticada deve incluir temas claro/escuro e celular.

## Financeiro

Reinicie a API para carregar `/admin/finance` e entre em `/painel/financeiro`. Selecione o período e confira Movimentações (datas do recebimento/devolução) e Pendências (datas dos atendimentos, com posição financeira atual). Um reembolso no período de recebimento anterior pode gerar líquido negativo. Entrada pendente já está incluída no total restante. Ver atendimento abre a Conferência; valores não são editados pelo relatório. Homologue com conta de teste nos temas claro/escuro e celular.

## Recuperação de código (V026)

Reinicie a API pelo script local para aplicar V026. Em Conferência, abra uma reserva por telefone e consulte Acesso do cliente. Confirme a identidade por contato confiável/presencial, informe motivo e confirme a invalidação antes de emitir. Entregue o novo código somente ao cliente verificado; use os dados originais exibidos para acessar o portal. Ele fica visível por até cinco minutos e não pode ser consultado novamente. Perda da resposta exige atualizar a consulta e emitir novamente após um minuto. Teste que código/sessão/recibo antigos não acessam reserva, PIX ou comprovantes, enquanto o código novo funciona. Não execute esse teste em reserva real sem solicitação do cliente. Legados por email mantêm solicitação de novo link.

## Homologação HTTP

Com API/frontend ativos, execute `powershell -NoProfile -File infra/local/verify-http-security.ps1`. O script só consulta endpoints e valida cabeçalhos/status, sem criar registros. Para validar o artefato de produção, pare o dev server, execute `npm run build` e `npm run start` em `apps/web`. Não mantenha duas instâncias na porta 3000. Em 22/10 foi deixado o frontend em modo produção local; para voltar ao desenvolvimento, pare-o e execute `npm run dev`. Checklist e limites em [privacidade e segurança](privacidade-e-seguranca.md).
