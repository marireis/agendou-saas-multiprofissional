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
