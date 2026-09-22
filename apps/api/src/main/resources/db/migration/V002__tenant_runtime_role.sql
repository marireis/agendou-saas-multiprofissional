-- Cria uma role de runtime separada da role de migracao/dona das tabelas.
--
-- Ate esta migration, a aplicacao conectava com a mesma role que criou as
-- tabelas (agendou_app, que no Postgres oficial em Docker e criada como
-- superuser-like pelo initdb quando POSTGRES_USER != 'postgres'). Role dona
-- de tabela e superuser SEMPRE ignoram RLS, entao as policies de isolamento
-- por tenant criadas em V001 nunca estavam realmente em vigor.
--
-- Esta migration roda com a role de migracao (agendou_app), configurada
-- separadamente em spring.flyway.* (ver application.yml). A partir de
-- agora a aplicacao roda com agendou_runtime, que:
--   * nao e superuser;
--   * nao e dona de nenhuma tabela;
--   * nao tem BYPASSRLS;
--   * so tem os privilegios explicitos concedidos abaixo.
--
-- ATENCAO: a senha abaixo e um valor fixo apenas para ambiente local
-- (Docker Compose em infra/local). Em homologacao/producao a senha desta
-- role deve vir do Secrets Manager (ver docs/architecture.md secao 5),
-- nunca ficar hardcoded em uma migration versionada.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agendou_runtime') THEN
        CREATE ROLE agendou_runtime
            LOGIN
            PASSWORD 'agendou_runtime_local_only'
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOBYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO agendou_runtime;

GRANT SELECT ON plans TO agendou_runtime;
GRANT SELECT, INSERT, UPDATE ON tenants TO agendou_runtime;
GRANT SELECT, INSERT, UPDATE ON subscriptions TO agendou_runtime;
GRANT SELECT, INSERT ON subscription_events TO agendou_runtime;

-- FORCE garante que a RLS tambem se aplique caso, no futuro, alguem
-- transfira a posse destas tabelas para a propria agendou_runtime (donos
-- de tabela sao isentos de RLS por padrao, a menos que FORCE esteja ativo).
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
ALTER TABLE subscriptions FORCE ROW LEVEL SECURITY;
ALTER TABLE subscription_events FORCE ROW LEVEL SECURITY;
