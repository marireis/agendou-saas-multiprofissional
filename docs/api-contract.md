# Agendou - Contrato de API inicial

Todas as rotas abaixo ficam sob `/api/v1`. Erros retornam `code`, `message`, `correlation_id` e, quando seguro, `field_errors`.

## 1. Convencoes

- `400`: requisicao malformada.
- `401`: autenticacao ausente ou invalida.
- `403`: autenticado sem permissao.
- `404`: recurso inexistente ou de outro tenant, sem revelar existencia.
- `409`: conflito de concorrencia, quota, slot, idempotencia ou versao.
- `422`: entrada semanticamente invalida.
- Valores monetarios sempre em centavos.
- Datas e horarios persistidos em UTC; respostas podem incluir fuso e offset.
- Mutacoes criticas aceitam `Idempotency-Key`.

## 2. Identidade

| Metodo | Rota | Uso |
|---|---|---|
| POST | `/auth/register` | Criar admin e tenant em trial Premium/Top. |
| POST | `/auth/verify` | Verificar email. |
| POST | `/auth/login` | Iniciar sessao. |
| POST | `/auth/logout` | Encerrar sessao. |
| POST | `/auth/password-reset` | Solicitar recuperacao. |
| POST | `/auth/password-reset/confirm` | Confirmar nova senha. |

## 3. Assinatura e trial

| Metodo | Rota | Uso |
|---|---|---|
| GET | `/admin/subscription` | Ver plano, trial, bloqueios e proximas acoes. |
| POST | `/admin/subscription/payment-intent` | Solicitar instrucao de pagamento da assinatura quando manual. |
| POST | `/admin/subscription/payment-evidence` | Enviar comprovante de assinatura, se o MVP usar conferencia manual. |
| GET | `/platform/tenants` | Super admin lista tenants. |
| PATCH | `/platform/tenants/{id}/subscription` | Atualizar status, vigencia, bloqueio e reativacao. |
| POST | `/platform/tenants/{id}/reactivate` | Reativar apos pagamento confirmado. |

## 4. Perfil e catalogo

| Metodo | Rota | Uso |
|---|---|---|
| GET | `/admin/profile` | Obter perfil. |
| PATCH | `/admin/profile` | Atualizar perfil. |
| POST | `/admin/profile/logo` | Enviar logo. |
| POST | `/admin/profile/publish` | Publicar quando criterios forem atendidos. |
| GET | `/admin/services` | Listar servicos. |
| POST | `/admin/services` | Criar servico. |
| PATCH | `/admin/services/{id}` | Atualizar ou inativar servico. |

## 5. Disponibilidade

| Metodo | Rota | Uso |
|---|---|---|
| GET | `/admin/availability` | Obter regras. |
| PUT | `/admin/availability` | Substituir regras com validacao. |
| POST | `/admin/blocks` | Criar bloqueio. |
| DELETE | `/admin/blocks/{id}` | Remover bloqueio. |
| GET | `/public/{slug}/availability` | Listar horarios publicos. |

## 6. Publico e cliente

| Metodo | Rota | Uso |
|---|---|---|
| GET | `/public/{slug}` | Ver pagina publica. |
| POST | `/public/{slug}/access-links` | Solicitar link de acesso por contato. |
| POST | `/client/access-links/consume` | Consumir link e criar sessao restrita. |
| POST | `/public/{slug}/bookings` | Criar reserva temporaria. |
| GET | `/client/bookings` | Listar reservas do cliente no tenant. |
| GET | `/client/bookings/{id}` | Ver detalhe autorizado. |

## 7. PIX manual e operacao

| Metodo | Rota | Uso |
|---|---|---|
| POST | `/client/bookings/{id}/evidence` | Enviar comprovante PIX. |
| GET | `/admin/payments/pending` | Fila de conferencia. |
| POST | `/admin/payments/{id}/validate` | Registrar conferencia manual. |
| POST | `/admin/payments/{id}/refunds` | Solicitar/registrar devolucao. |
| POST | `/admin/refunds/{id}/record-settlement` | Registrar devolucao realizada fora do sistema. |
| GET | `/admin/bookings` | Listar reservas. |
| POST | `/admin/bookings` | Criar reserva assistida. |
| POST | `/admin/bookings/{id}/reschedule` | Reagendar. |
| POST | `/admin/bookings/{id}/cancel` | Cancelar. |
| POST | `/admin/bookings/{id}/complete` | Concluir atendimento. |
| POST | `/admin/bookings/{id}/no-show` | Marcar falta. |

## 8. Bloqueios por assinatura

Quando `Subscription.status = TRIAL_EXPIRED_BLOCKED`, a API deve:

- permitir login;
- permitir ler `GET /admin/subscription`;
- permitir iniciar fluxo de pagamento/reativacao;
- bloquear criacao ou publicacao de servicos, disponibilidade e reservas;
- fazer pagina publica retornar indisponibilidade segura;
- preservar consultas administrativas minimas necessarias para o usuario entender o bloqueio.

## Rotas disponíveis na etapa atual

O contrato executável em `contracts/openapi.yaml` documenta somente a implementação atual: autenticação, recuperação, consulta da assinatura e perfil inicial. Os demais quadros deste documento representam o contrato planejado.

`GET /auth/csrf` retorna token e nome do header. Toda mutação exige esse header e o cookie da sessão, inclusive cadastro/login. O frontend renova o token após login. O alias `GET /subscriptions/{tenantId}` exige sessão e membership correspondente; a tela usa `GET /admin/subscription`, sem selecionar tenant no navegador. As antigas rotas públicas de criação de trial e confirmação de pagamento foram desativadas.
