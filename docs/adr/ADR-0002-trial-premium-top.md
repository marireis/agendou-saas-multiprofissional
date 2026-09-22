# ADR-0002 - Trial gratuito apenas no Premium/Top

## Estado

Aceita por decisao do consumidor neste DELTA.

## Contexto

O teste gratis deve existir apenas para o plano mais alto. Apos 7 dias, o usuario deve pagar ou ser bloqueado, sem perder a pagina e configuracoes ja criadas.

## Decisao

O MVP implementa trial de 7 dias apenas para `PREMIUM_TOP`. Ao vencer sem pagamento, a assinatura muda para `TRIAL_EXPIRED_BLOCKED`. Pagamento confirmado muda para `PAID_ACTIVE` e preserva os dados do tenant.

## Consequencias

- Billing/trial entra cedo no backlog.
- Bloqueio precisa ser aplicado no backend.
- Reativacao nao pode recriar tenant nem apagar configuracao.
