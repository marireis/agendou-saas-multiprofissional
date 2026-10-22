# ADR-0028 — Reemissão profissional do código de acesso

Data: 22/10/2026.

O cliente sem email pede ajuda ao profissional quando perde o código. Não há recuperação pública apenas por nome/telefone. Em Conferência → atendimento → Acesso do cliente, um ADMIN com assinatura operacional confirma a identidade por contato confiável ou presencialmente, registra motivo e emite outro código. A aplicação registra essa declaração, sem executar verificação externa de identidade. Reservas legadas por email continuam com o fluxo de link por email.

V026 acrescenta versão/data de rotação ao registro de acesso e auditoria append-only com tenant/reserva/versão, operador, motivo, data e correlation ID, sem segredo/hash na auditoria. FKs compostas, FORCE RLS e permissões mínimas. Rotação e auditoria são atômicas sob lock de tenant; falha desfaz ambas. Versão esperada impede solicitações simultâneas/repetidas de emitirem dois códigos. Intervalo mínimo de 60 segundos por reserva.

Novo segredo aleatório de 32 bytes; somente SHA-256 persistido. POST exige ADMIN, CSRF, assinatura operacional, versão, motivo e confirmação explícita. Retorna o código apenas nessa resposta no-store. GET retorna metadados e dez últimas reemissões, nunca o segredo. A interface mantém o novo código em memória por até cinco minutos e permite copiar/ocultar; não há armazenamento em localStorage, URL, outbox ou sessão administrativa. Entrega manual ao cliente verificado. Se a resposta se perder, consulte novamente: não há reprodução do segredo, e outra emissão exige nova confirmação/versão e intervalo mínimo.

A troca do hash invalida consultas com o código anterior. Sessões do portal e recibos originais usam a identidade derivada do hash e validam essa identidade no banco para acessar reserva, PIX e comprovantes. Sessões não são apagadas globalmente, mas deixam de autorizar consultas. Dados já visualizados/baixados não podem ser recolhidos. Repetir a criação pública não entrega código revogado. Nome/telefone originais exigidos no portal permanecem iguais.

Nenhuma mudança em horário, estado, valores, pagamentos ou acesso a outras reservas. Consulta administrativa preservada após bloqueio da assinatura, mas reemissão exige operação ativa. Recuperação de conta/MFA é um fluxo diferente.
