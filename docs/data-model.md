# Agendou - Modelo de dados e estados

## 1. Migracoes iniciais

| Migracao | Entidades | Regras principais |
|---|---|---|
| V001 | Tenant, User, Membership, PlatformRole, Session, AuthToken | Slug unico, token com hash, roles separadas. |
| V002 | Plan, Subscription, SubscriptionEvent, BillingDecision | Trial apenas Premium/Top, 7 dias, bloqueio e reativacao. |
| V003 | ProfessionalResource, PublicProfile, Service, PixReceiver | Uma agenda inicial, preco em centavos, entrada 50-100%. |
| V004 | AvailabilityRule, AvailabilityException, CalendarAllocation | UTC, fuso IANA, constraint GiST e bloqueios. |
| V005 | Customer, CustomerAccessToken, Booking, BookingEvent | Contato verificado, snapshots e historico. |
| V006 | UsageBucket, UsageEntry | Quota confirmada e provisoria por mes. |
| V007 | PaymentIntent, PaymentTransaction, PaymentEvidence, Refund | PIX manual, comprovante privado, referencia unica. |
| V008 | OutboxEvent, Notification, AuditLog | Deduplicacao, retries e auditoria. |

## 2. Entidades centrais

### Tenant

- `id`
- `slug`
- `display_name`
- `status`
- `created_at`
- `updated_at`

### Subscription

- `id`
- `tenant_id`
- `plan_code`
- `status`
- `trial_started_at`
- `trial_ends_at`
- `paid_until`
- `blocked_at`
- `reactivated_at`
- `cancelled_at`

### Service

- `id`
- `tenant_id`
- `name`
- `description`
- `duration_minutes`
- `price_cents`
- `deposit_percent`
- `buffer_before_minutes`
- `buffer_after_minutes`
- `active`

### Booking

- `id`
- `tenant_id`
- `customer_id`
- `service_id`
- `status`
- `starts_at_utc`
- `ends_at_utc`
- `expires_at_utc`
- `service_snapshot_json`
- `pix_snapshot_json`
- `price_cents`
- `deposit_due_cents`

### PaymentIntent

- `id`
- `tenant_id`
- `booking_id`
- `status`
- `amount_due_cents`
- `amount_validated_cents`
- `deadline_at_utc`
- `provider = MANUAL_PIX`

## 3. Estados de tenant/assinatura

```mermaid
stateDiagram-v2
    [*] --> TRIAL_ACTIVE
    TRIAL_ACTIVE --> TRIAL_EXPIRING: faltam 2 dias
    TRIAL_ACTIVE --> PAID_ACTIVE: pagamento confirmado
    TRIAL_EXPIRING --> PAID_ACTIVE: pagamento confirmado
    TRIAL_EXPIRING --> TRIAL_EXPIRED_BLOCKED: venceu sem pagamento
    TRIAL_ACTIVE --> TRIAL_EXPIRED_BLOCKED: venceu sem pagamento
    TRIAL_EXPIRED_BLOCKED --> PAID_ACTIVE: pagamento confirmado
    PAID_ACTIVE --> PAST_DUE: pagamento vencido
    PAST_DUE --> PAID_ACTIVE: regularizado
    PAID_ACTIVE --> SUSPENDED: suspensao admin
    SUSPENDED --> PAID_ACTIVE: reativacao admin
    PAID_ACTIVE --> CANCELED: cancelamento
```

## 4. Estados de reserva

```mermaid
stateDiagram-v2
    [*] --> AguardandoPagamento
    AguardandoPagamento --> EmConferencia: comprovante enviado no prazo
    AguardandoPagamento --> Expirada: prazo venceu
    EmConferencia --> Confirmada: entrada validada suficiente
    EmConferencia --> Cancelada: desistência ou validacao recusada
    EmConferencia --> PendenciaFinanceira: pagamento tardio/insuficiente/excedente
    Confirmada --> Reagendada: novo horario valido
    Confirmada --> Cancelada: politica permitiu
    Confirmada --> Concluida: atendimento realizado
    Confirmada --> Falta: cliente nao compareceu
```

## 5. Regras de integridade

- FKs de negocio usam `(tenant_id, id)` quando a entidade pertence ao tenant.
- RLS nega contexto ausente.
- Role da aplicacao nao e dona das tabelas e nao possui `BYPASSRLS`.
- `CalendarAllocation` impede sobreposicao ativa por tenant/recurso.
- `PaymentTransaction` possui unicidade de referencia bancaria por tenant/conta quando aplicavel.
- `SubscriptionEvent` e `AuditLog` sao append-only.
- Snapshots preservam preco, duracao, recebedor PIX e politica vigentes no momento da reserva.

## Migrations efetivamente implementadas

O quadro inicial acima é o plano de domínio. O histórico real até esta etapa é:

- V001: tenants, plans, subscriptions e subscription_events.
- V002: role de runtime separada e RLS forçada nas tabelas de negócio.
- V003: usuários, memberships, tokens, outbox SMTP, sessões JDBC, perfil inicial e constraints adicionais de assinatura/eventos.

As próximas migrations devem continuar a numeração existente, sem reutilizar versões do quadro planejado.

- V008: payment_settings_versions com chave/recebedor/politicas, enabled, versao, ator, motivo, campos alterados e correlation ID. RLS e FK de membership; runtime somente SELECT/INSERT. Dados atuais pela maior versao; reservas futuras deverao referenciar/copiar a versao utilizada.

- V007: services com tenant_id/RLS, duracao, preco em centavos, intervalos, entrada, ativo, versao e timestamps. Constraints numericas e UNIQUE(tenant_id,id) preparam referencias compostas futuras. Runtime sem DELETE; inativacao preserva dados.

- V006: contato comercial, modalidade/local e variante PNG/UUID no perfil, mantendo RLS. Limite de 2 MiB no banco e par logo/versao consistente. Campos existentes e assinaturas preservados; novos campos iniciam vazios.

- V005: platform_roles (runtime somente leitura), platform_mfa (segredo cifrado, versao e consumo TOTP), billing_decisions (RLS, referencia unica, estados antes/depois), platform_audit (metadados append-only para runtime) e funcao de listagem restrita. Sem BYPASSRLS para a aplicacao. Provisionamento nao altera usuarios, perfis ou trials.

- V004: contadores globais de autenticacao e ciclo de vida da outbox, com status, finalidade, vinculo ao usuario/token, validade e diagnostico sem dados sensiveis. Tabelas globais de infraestrutura nao aceitam tenant fornecido pelo cliente e nao possuem CRUD publico.
