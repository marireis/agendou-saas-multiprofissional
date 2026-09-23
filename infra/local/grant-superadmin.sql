\set ON_ERROR_STOP on
BEGIN;
SELECT EXISTS(SELECT 1 FROM app_users WHERE email=lower(trim(:'email')) AND verified) AS eligible \gset
\if :eligible
INSERT INTO platform_roles(user_id,role,granted_by,reason)
 SELECT id,'SUPER_ADMIN',:'operator',:'reason' FROM app_users WHERE email=lower(trim(:'email')) AND verified
 ON CONFLICT(user_id) DO NOTHING;
INSERT INTO platform_audit(id,actor_id,action,correlation_id)
 SELECT gen_random_uuid(),id,'ROLE_GRANTED_BY_OPERATOR','local-provisioning' FROM app_users WHERE email=lower(trim(:'email')) AND verified;
COMMIT;
\echo 'Conta habilitada. Entre em /plataforma e configure o MFA.'
\else
ROLLBACK;
\echo 'Conta inexistente ou email ainda nao verificado. Nenhuma permissao concedida.'
\quit 1
\endif
