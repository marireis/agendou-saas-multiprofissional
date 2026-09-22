# Desenvolvimento local - Windows

Execute na pasta original do Agendou. JDK 21 e Docker Desktop sao necessarios.

## Backend

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
