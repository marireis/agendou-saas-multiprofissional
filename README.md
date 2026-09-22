# Agendou

SaaS de agendamento em desenvolvimento, com Spring Boot/Java 21, PostgreSQL e Next.js.

A etapa atual implementa cadastro e verificação de administrador, sessão JDBC, recuperação de senha, isolamento por tenant, trial Premium/Top de sete dias, bloqueio e perfil inicial. Reservas e PIX manual continuam no backlog.

- [Estado real e próximas etapas](docs/implementation-status.md)
- [Como executar e testar no Windows](docs/runbooks/desenvolvimento-local.md)
- [Backlog completo](docs/backlog.md)
- [Contrato OpenAPI](contracts/openapi.yaml)

## Validação

```powershell
$env:JAVA_HOME = 'C:\Users\maryn\.jdks\corretto-21.0.5'
cd apps/api
.\mvnw.cmd verify
cd ../web
npm ci
npm run typecheck
npm run build
```

Docker Desktop deve estar iniciado para `verify`, que inclui PostgreSQL real via Testcontainers. O Maven Wrapper oficial instala/seleciona Maven 3.9.9 automaticamente. Para HTTP local, usar `AGENDOU_SECURE_COOKIE=false` conforme o runbook; cookies Secure são o padrão.
