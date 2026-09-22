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
