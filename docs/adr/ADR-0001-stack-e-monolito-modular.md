# ADR-0001 - Stack e monolito modular

## Estado

Aceita como baseline de implementacao inicial.

## Contexto

O roadmap do Agendou recomenda Java 21, Spring Boot, PostgreSQL, Next.js, React e TypeScript, com API e worker derivados do mesmo projeto Java.

## Decisao

Iniciar o MVP com monolito modular no backend, frontend Next.js separado, PostgreSQL como banco relacional e Compose local para dependencias.

## Consequencias

- Menos sobrecarga operacional no MVP.
- Mantem transacoes e locks criticos no banco.
- Permite evoluir modulos sem criar microservicos prematuros.
