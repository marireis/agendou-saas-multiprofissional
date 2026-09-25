# Agendou - Contrato de API inicial

Todas as rotas abaixo ficam sob `/api/v1`. Erros retornam `code`, `message`, `correlation_id` e, quando seguro, `field_errors`.

## 1. Convencoes

Página/publicação (25/09, OpenAPI 0.8.0): `GET /admin/publication` retorna published, canPublish=false, missingRequirements e path. POST exige CSRF/assinatura e retorna 409 até haver disponibilidade real; DELETE retira somente a própria página, inclusive após bloqueio, sem apagar dados. `GET /public/pages/{slug}` e `/logo` são anônimos e sem cache: 404 idêntico para rascunho/inexistente; projeção somente de campos públicos e serviços ativos, sem PIX. Nenhuma publicação automática; prévia privada na aba Publicação.

Entrega PIX/política (25/09, OpenAPI 0.7.0): `GET/PUT /admin/payment-settings` lê/grava configuração do próprio tenant; `GET /admin/payment-settings/history?offset=0` lista apenas metadados. PUT exige version (zero inicialmente), keyType, pixKey, recipientName, paymentInstructions, cancellationPolicy, enabled, confirmed=true e changeReason. Revisões imutáveis; conflito 409 para versão antiga. Validação local 422 não atesta registro/titularidade bancária. Leitura autenticada com no-store preservada após bloqueio; mutações exigem assinatura operacional/CSRF. Referência e snapshots de reservas serão adicionados no MVP-052.

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

## Plataforma - entrega seguinte de 23/09/2026

OpenAPI 0.4.0 documenta `/platform/session`, `/platform/mfa/enrollment`, `/platform/mfa/verify`, `/platform/tenants`, `/platform/tenants/{tenantId}`, `/platform/tenants/{tenantId}/decisions` e `/platform/audit`. Todas exigem sessao e papel SUPER_ADMIN persistido; dados e decisoes exigem MFA vigente. Mutacoes exigem CSRF. Decisao usa UUID idempotente no corpo, motivo e dados de pagamento quando aplicavel; referencia bancaria unica. Operacao publica de autoativacao continua negada. Ver ADR-0005 e runbook superadmin.

## Identidade e erros - 23/09/2026

## Catálogo de serviços - OpenAPI 0.6.0

`GET /admin/services?offset=0` retorna items (até 50), total e activeCount do próprio tenant. `POST` cadastra; `PUT /admin/services/{id}` edita/inativa/reativa exigindo version. Campos: name, description, durationMinutes (5–480), priceCents (1–100000000), bufferBeforeMinutes e bufferAfterMinutes (0–240), depositPercent (50–100), active. Resposta inclui id, version e depositCents calculado pelo servidor com arredondamento para cima. Inteiros fracionários são rejeitados (400), limites inválidos retornam 422, versão antiga 409 e ID de outro tenant 404. CSRF e assinatura operacional obrigatórios em mutações; não há DELETE. POST não é idempotente; após falha de rede, consultar a lista antes de repetir.

## Perfil e marca - OpenAPI 0.5.0

`GET/PATCH /admin/profile` inclui contactEmail, contactPhone, serviceMode (UNSET/IN_PERSON/ONLINE/HYBRID) e location. Omissão/null dos campos novos preserva valores anteriores; vazio limpa rascunhos. Resposta inclui logoVersion, profileProgress, profileComplete e missingFields. Perfil completo não autoriza publicação. Fuso exige identificador IANA.

`PUT /admin/profile/logo` recebe bytes brutos PNG/JPEG até 2097152 bytes (sem multipart), com CSRF. Retorna perfil atualizado após saneamento. 413 para excesso de bytes; 422 para imagem inválida/dimensões excessivas; 403 para assinatura bloqueada. `DELETE` remove e retorna perfil. `GET` entrega somente a logo do tenant da sessão, inclusive bloqueado, com Content-Type image/png, no-store/nosniff; 404 se ausente. Não existe rota pública nesta etapa.

### Reenvio e erros de identidade

POST /auth/verification-email solicita novo link, com CSRF, email valido e resposta 202 generica. Reenvio nao reinicia trial. Rotas auth podem retornar 429 com Retry-After em segundos; cadastro, reenvio e recuperacao compartilham limite por email. X-Correlation-ID gerado pelo servidor e correlation_id no JSON permitem correlacionar erros tratados. Consulte OpenAPI para o contrato atualizado.
