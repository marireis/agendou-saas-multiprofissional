# Agendou

MVP de SaaS de agendamento baseado no DELTA exportado em `exports/DELTA_Agendou_DUAL_2026-09-22`.

## Escopo desta base

- Backend Spring Boot em `apps/api` com dominio inicial de trial Premium/Top.
- Frontend Next.js em `apps/web` com tela inicial responsiva.
- Contrato OpenAPI inicial em `contracts/openapi.yaml`.
- Infra local em `infra/local/compose.yaml`.
- ADRs iniciais em `docs/adr`.
- Status de implementacao em `docs/implementation-status.md`.

## Regra comercial central

O teste gratis existe somente no plano Premium/Top, dura 7 dias e bloqueia o tenant caso nao haja pagamento confirmado. A configuracao, pagina e historico devem ser preservados para reativacao apos pagamento.

## Comandos previstos

Frontend:

```bash
cd apps/web
npm install
npm run build
```

Backend, quando Maven estiver disponivel:

```bash
cd apps/api
mvn test
mvn spring-boot:run
```
