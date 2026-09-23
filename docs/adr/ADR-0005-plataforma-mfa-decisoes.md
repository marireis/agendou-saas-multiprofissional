# ADR-0005 — Plataforma, MFA e decisões de assinatura

Data: 23/09/2026. Status: implementada.

## Decisões

- `/plataforma` é uma área própria, sem impersonação. Login usa a identidade existente; `PlatformRole=SUPER_ADMIN` é concedido somente por operador de banco, para conta verificada. A role runtime tem apenas SELECT em `platform_roles`.
- Toda operação de plataforma valida o papel persistido; listagem/detalhe/decisões/auditoria exigem também MFA vigente. Nenhum papel é concedido pelo cadastro público ou pelo corpo de uma requisição.
- TOTP conforme [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238): SHA1, segredo aleatório de 160 bits, 6 dígitos, período 30 s, tolerância de um período em cada direção. Consumo monotônico com lock impede replay, inclusive concorrente. Testes usam os vetores publicados, incluindo datas posteriores a 2038.
- Configuração exige senha atual; segredo pendente vence em 10 minutos. Autenticador ativado não pode ser substituído pela interface. Tentativas de configuração/verificação compartilham limite persistido de 5 por conta a cada 5 minutos.
- Segredo cifrado com AES-256-GCM e AAD vinculada ao usuário. `AGENDOU_MFA_ENCRYPTION_KEY` é obrigatório para usar MFA; não existe chave padrão. Em Windows local, `infra/local/start-api.ps1` mantém a chave protegida por DPAPI, no arquivo ignorado `.local/mfa-key.dpapi`. Em produção, usar gestão de segredos e HTTPS.
- A elevação dura 15 minutos absolutos, vinculada a usuário e versão do autenticador, com rotação de sessão. Novo login remove a elevação. Logout/reset de senha revogam a sessão; revogação da role/reset operacional do autenticador invalida acesso na próxima requisição.
- A listagem global usa função SECURITY DEFINER de projeção limitada, search_path fixo e execução revogada de PUBLIC; consulta a existência da role. A autorização de sessão/MFA pertence ao serviço. Runtime continua sem BYPASSRLS; detalhe e mutações usam contexto transacional de tenant somente após autorização explícita de plataforma.
- `billing_decisions` é isolada por RLS. Decisão, evento e auditoria são gravados na mesma transação que a assinatura. Runtime não pode atualizar/excluir decisões ou auditoria.
- Pagamento manual exige motivo, plano, valor positivo em centavos e referência bancária globalmente única (trim/uppercase). Acrescenta 30 dias à vigência paga futura ou ao instante atual. O valor é um registro da conferência: não há tabela comercial de preços, integração bancária nem cobrança automática.
- UUID de decisão garante idempotência com comparação dos campos. Repetição idêntica retorna o estado original; conteúdo diferente ou referência já usada gera 409 sem extensão parcial. Lock do tenant coordena concorrência com expiração e demais mutações.
- Suspensão preserva dados. Retirar suspensão restaura somente a vigência existente; vencida, volta ao bloqueio e exige pagamento. Não renova trial. Assinatura paga vence em PAST_DUE sem carência no MVP (decisão conservadora até política comercial específica).
- Pagamento não retira suspensão automaticamente; operador primeiro registra a resolução da suspensão. Cancelamento permanece fora deste painel mínimo.

## Limites

Sem recuperação automática/backup codes de MFA: recuperação exige operador autorizado, confirmação de identidade e procedimento auditado. Sem acesso a dados pessoais de clientes pelo superadmin. Retenção/exportação de auditoria, alertas, implantação e revisão independente de segurança ficam para homologação. A lista mostra estado persistido; detalhe e worker atualizam a expiração.
