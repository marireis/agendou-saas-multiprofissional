# Superadmin — acesso local e operação

## Iniciar

Na raiz do projeto, com Docker Desktop aberto:

```powershell
docker compose -f infra/local/compose.yaml up -d postgres mailpit
powershell -NoProfile -File infra/local/start-api.ps1
```

O script local configura JDK 21 quando instalado no caminho conhecido, cookie HTTP local e a chave de criptografia MFA. A chave é aleatória e protegida pelo usuário Windows com DPAPI em `.local/mfa-key.dpapi`, ignorado pelo Git. Não apagar esse arquivo, não imprimir a chave e não usar esse setup em produção. Um terminal aberto com `mvnw.cmd` diretamente precisa receber `AGENDOU_MFA_ENCRYPTION_KEY` correspondente; preferir o script para reutilizar a mesma chave.

Em outro terminal, iniciar o frontend em `apps/web` com `npm run dev`. Abrir `http://localhost:3000/entrar?destino=plataforma`. Após login, configurar conta no aplicativo autenticador usando a chave manual exibida, tipo baseado em tempo, SHA1, 6 dígitos e 30 segundos. Confirmar o código. A chave deixa de ser exibida depois da ativação. MFA precisa ser repetido a cada 15 minutos.

## Conceder acesso inicial

Exige conta já cadastrada e verificada, solicitação explícita do responsável e role de migração/operação. Não há senha padrão de superadmin. O script SQL falha sem conta elegível e não altera senha, email, trial ou perfil.

```powershell
Get-Content -Raw infra/local/grant-superadmin.sql | docker compose -f infra/local/compose.yaml exec -T postgres psql -U agendou_app -d agendou -v email='conta-verificada@example.test' -v operator='responsavel-local' -v reason='Acesso autorizado pelo responsavel da plataforma'
```

Substituir o email somente pelo autorizado. O script registra origem/motivo na role e evento na auditoria. Nunca conceder permissão à role de runtime para administrar `platform_roles`.

## Decisões

- Abrir profissional e revisar sua assinatura. O painel não dá acesso ao painel privado nem aos clientes dele.
- Para pagamento: conferir externamente, registrar plano, valor recebido, referência bancária única e justificativa. Conferência registra 30 dias; não transfere dinheiro nem consulta banco.
- Suspender exige justificativa e preserva dados. Retirar suspensão não prorroga vigência; se vencida, confirmar pagamento em decisão separada.
- Duplo envio com mesmo UUID é idempotente. Referência duplicada gera conflito e desfaz toda a transação. Não inventar outra referência para contornar o conflito; verificar o registro anterior.
- Auditoria mostra operador, ação, tenant, data e correlation ID. Histórico de decisões inclui estados anterior/posterior no banco. Não incluir dados pessoais desnecessários na justificativa.

## Perda de autenticador ou revogação

Procedimento excepcional, feito pelo operador com credencial de migração/operação, após confirmar identidade e autorização. Identificar `user_id` exato; nunca usar um filtro amplo. Em uma única transação:

1. Registrar evento `MFA_RESET_BY_OPERATOR` em `platform_audit`, com `actor_id` da conta afetada e correlation ID/referência do chamado aprovado (a identidade do operador fica no registro operacional do chamado).
2. Excluir somente `platform_mfa` dessa conta.
3. Excluir `spring_session` com `principal_name` igual ao UUID dessa conta.
4. Para revogação definitiva, excluir também sua linha de `platform_roles`.

O próximo acesso exige login e nova configuração. Não há endpoint de bypass MFA. Perda da chave de criptografia exige recuperação pelo gestor de segredos/backup autorizado ou reset operacional dos autenticadores afetados, nunca chave fixa improvisada.

## Diagnóstico

- 403: confirmar role e MFA vigente. Uma conta profissional comum recebe 403 por definição.
- 429: aguardar Retry-After; limite compartilhado de configuração/verificação (5/5 min).
- Código inválido: relógio sincronizado, seis dígitos, aguardar próximo código se já utilizado.
- 503: chave MFA ausente/incorreta. Usar o mesmo arquivo DPAPI/segredo do provisionamento.
- Banco atualizado via V005; não editar migrations aplicadas nem apagar volumes para resolver falhas.
