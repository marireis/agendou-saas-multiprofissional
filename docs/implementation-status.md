# Agendou - Status de implementação

**Atualizado em:** 25/09/2026. Desenvolvimento na própria pasta do projeto.

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

## Validação desta entrega

| Check | Evidência |
|---|---|
| `mvnw.cmd verify` | PASS: 27 testes unitários + 52 de integração (79 no total), sem falhas ou testes ignorados; PostgreSQL 17 e Mailpit descartáveis |
| Migrations V001 a V011 | Aplicadas em bancos descartáveis, usando role real de runtime |
| Atualização de banco existente | V003 → V011 preserva usuário, token e todos os dados da assinatura/trial; email legado pendente expira e tem conteúdo limpo |
| Alocações/bloqueios | Corridas HOLD/bloqueio e HOLD/expediente, GiST direto, limites adjacentes, intervalo, expiração, fuso protegido, RLS/CSRF, bloqueio de assinatura e liberação sem excluir histórico |
| Gerador de horários | Grade/fuso, antecedência/horizonte, pausas, exceções, duração/buffers, intervalo, limites semiabertos e DST; consulta isolada, serviço inativo/externo negado |
| Horários | Semana, pausas, dia fechado, validação de períodos/dias, RLS, CSRF, isolamento, conflito concorrente e bloqueio de trial com leitura preservada |
| Página/publicação | Rascunhos/logo privados, projeção só com serviços ativos, isolamento entre tenants, RLS direta, entrada em centavos, no-store, requisitos, CSRF, bloqueio de publicação e retirada sem perda de dados |
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

Relatórios: `apps/api/target/surefire-reports`, `apps/api/target/failsafe-reports`. Log da suíte atual: `apps/api/calendar-verify.log`; build em `apps/web/calendar-build.log`. PASS técnico não equivale à homologação de negócio. Frontend validado por tipagem/build; E2E de navegador de bloqueios/prévia de horários/publicação/PIX/catálogo/menu e revisão visual/acessibilidade ainda pendentes. A titular confirmou acesso real ao superadmin com MFA e testou o perfil/logo.

## Backlog: concluído e parcial

- **MVP-005:** Maven/Wrapper/lockfile/typecheck disponíveis. Lint/formatter e pipeline completo pendentes.
- **MVP-010:** usuário, membership, token, sessão, PlatformRole e MFA TOTP implementados.
- **MVP-011/012/013:** identidade administrativa, reenvio, recuperação/revogação, cookies, CSRF e autorização por membership implementados/testados.
- **MVP-014:** RLS nas tabelas de negócio atuais; ampliar nas próximas migrations. Identidade, sessões, fila e limites são infraestrutura global sem CRUD público.
- **MVP-015:** outbox com estados, retries, limpeza e SMTP local validado. SES, alertas e console operacional autenticado pendentes.
- **MVP-016:** shell/erros/correlation ID implementados; telemetria e observabilidade completas ainda pendentes.
- **MVP-020/021/022/023/024:** planos, trial, banners, eventos, BillingDecision, auditoria administrativa e expiração disponíveis. Retenção/exportação operacional para homologação.
- **MVP-025/026:** bloqueio real sobre perfil e consultas essenciais preservadas. Fluxos de pagamento, reserva e publicação ainda não implementados.
- **MVP-027/029:** reativação por pagamento, suspensão/retirada, consulta e auditoria no painel de plataforma com MFA concluídas. Sem cancelamento ou impersonação neste painel mínimo.
- **MVP-028:** email único impede novo trial na mesma conta. Documento/telefone ainda não são coletados.
- **MVP-030/031:** perfil, contato, modalidade/local, progresso e upload/remoção de logo implementados. Exposição pública da variante saneada aguarda publicação da página.
- **MVP-032:** catálogo administrativo funcional e testado; publicação, snapshots e reserva dependem das próximas etapas.
- **MVP-033:** configuração PIX/política, validação local, auditoria e versões imutáveis concluídas. Referência/cópia por reserva entra no MVP-052, sem reescrever condições anteriores.
- **MVP-034/035 (parciais):** página, prévia privada e bloqueio/requisitos de publicação implementados; liberação pública e CTA dependem de disponibilidade/reserva reais.

- **MVP-040:** configuração de expediente, pausas, exceções e bloqueios concluída.
- **MVP-041:** gerador e prévia privada concluídos e integrados com alocações persistidas.
- **MVP-042/043:** base de alocação interna e bloqueios transacionais concluída. **MVP-045 parcial:** locks com expediente/fuso e HOLDs; integração com Booking pendente.

## Próximas implementações

1. **Próxima entrega — calendário administrativo (MVP-044):** visão semanal desktop e lista diária mobile, com expediente e bloqueios reais, sem drag-and-drop ou compromissos fictícios. Ampliar com reservas quando Booking existir; concluir MVP-045 para esses estados no fluxo de reserva.
2. **Concluir publicação (MVP-034/035):** substituir o bloqueio de disponibilidade pelo cálculo real, validar todos os requisitos no servidor e liberar publicação explícita. CTA só inicia reservas quando o fluxo estiver funcional; PIX nunca aparece na página geral.
3. **Compartilhamento (MVP-036):** copiar link e mensagem editável para abrir no WhatsApp depois da publicação funcional; sem envio automático.
4. **Reserva:** acesso do cliente, idempotência, quota, snapshots e expiração (MVP-050 a 056).
5. **Operação do profissional:** PIX manual, comprovantes, conferência, atendimento, reserva assistida (MVP-060 a 068), aba Financeiro (MVP-069), cadastro/edição/lista/histórico de Clientes (MVP-070, antecipado junto da reserva assistida).
6. **Homologação:** E2E de navegador e MFA real, acessibilidade, SES, ingress confiável/limites por IP real, métricas/alertas, retenção de histórico, backup/restore e deploy.

### Onde entram as funções solicitadas pela responsável

| Função | Situação / etapa |
|---|---|
| Acesso superadmin | Implementado e acesso com autenticador confirmado pela responsável |
| Página com logomarca do profissional | Página e prévia em Publicação implementadas; liberação pública depende de disponibilidade real |
| Calendário e disponibilidade | Configuração, prévia e alocações MVP-040 a 043 concluídas; calendário MVP-044 e integração futura de reservas em MVP-045 pendentes |
| Aba de cadastrar clientes | MVP-070, junto da operação/reserva assistida MVP-067 |
| Financeiro do profissional | MVP-060 a 069; depende de reservas e pagamentos reais. Separado do billing da plataforma |
| Compartilhar link e mensagem no WhatsApp | MVP-036 explicitado no backlog; copiar/abrir mensagem, sem envio automático |

## Limitações e execução

- O limite por conexão fica compartilhado quando a API está atrás do proxy Next. Antes de publicar, configurar ingress confiável; não aceitar X-Forwarded-For público como autoridade.
- SMTP aceita a mensagem, mas não garante entrega na caixa final de um provedor externo. Uma interrupção entre envio e commit pode duplicar email; token continua de uso único.
- V004 expira emails antigos ainda pendentes, que não tinham vínculo confiável ao token. Contas/perfis/trials são preservados; pedir novo link em `/verificar`.
- Banco local atualizado até V011 e API reiniciada na porta 8080 em 25/09; frontend na porta 3000, com resposta HTTP 200 em `/painel/horarios`, health da API UP e 404 para slug público inexistente. Para iniciar novamente, usar `powershell -NoProfile -File infra/local/start-api.ps1` na raiz; não iniciar outra cópia se a porta 8080 estiver ocupada. Nenhum banco do usuário foi apagado; testes usam containers descartáveis.
- Agenda da home é ilustrativa. Página e prévia estão implementadas, mas publicação funcional, reservas, uploads de comprovantes, financeiro do profissional, clientes, conferência PIX e deploy ainda não estão concluídos. Perfil/logo e serviços já estão disponíveis nas respectivas áreas do painel.
- Cadastro de serviço por POST não é idempotente; após falha de rede, verificar a lista antes de repetir. Interface desabilita envio em andamento. Reservas terão idempotência própria em MVP-053.
- Variantes pequenas de logo ficam no banco nesta etapa (ADR-0006); revisar volume/backup antes de escalar. Imagens com orientação EXIF devem ser exportadas na orientação desejada. Não há publicação automática ao completar o perfil.
- Recuperação de MFA é operacional/auditada; não há backup codes ou reset público. A chave DPAPI depende deste usuário Windows; produção exige gestão de segredos própria.
- PIX: validação local não atesta registro/titularidade. Chaves ficam em tabelas privadas com RLS, sem cifragem adicional na aplicação; TLS, criptografia de volume/backup, acesso operacional e retenção de versões devem ser definidos no deploy. Não registrar corpos/chaves em logs.
- Credenciais padrão de banco/SMTP são locais. Nenhum email externo real ou cobrança foi realizado.

Execução: [desenvolvimento local](runbooks/desenvolvimento-local.md). Plataforma: [superadmin](runbooks/superadmin.md). Diagnóstico: [email indisponível](runbooks/email-indisponivel.md).
