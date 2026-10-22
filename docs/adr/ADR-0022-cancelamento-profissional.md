# ADR-0022 — Cancelamento profissional e decisão de devolução

Data: 01/10/2026. Status: aceita. Primeira parte de MVP-067.

O profissional cancela AWAITING_PAYMENT, IN_REVIEW ou CONFIRMED antes do início do atendimento. ADMIN/membership deriva tenant; CSRF e assinatura operacional são obrigatórios. A ação usa o mesmo lock de reserva, conferência, devolução e expiração. Reserva expirada não é convertida em cancelada; segue seu tratamento financeiro próprio.

A política aceita está no snapshot PIX da reserva e é texto livre. A interface apresenta esse texto original; não interpreta automaticamente prazos ou percentuais. O profissional declara revisão, registra motivo visível ao cliente e valor a devolver entre zero e o líquido recebido. O servidor exige expectedNetCents igual ao líquido atual, impedindo decisão sobre uma tela desatualizada. Não cria regra jurídica nem substitui a avaliação da política pelo operador.

V020 adiciona CANCELED e booking_cancellations, com motivo, operador/membership, valor líquido considerado, valor mantido, data e correlation ID. RLS e FKs compostas; runtime somente SELECT/INSERT na decisão. Uma decisão por reserva; reenvio idêntico é idempotente e alteração é conflito. Política permanece no snapshot existente. Operador/correlation ID não são expostos ao cliente.

Cancelamento, intenção CANCELED, liberação da alocação e evento são atômicos. Quota provisória HELD é liberada; quota COMMITTED de reserva já confirmada permanece consumida, preservando a contabilização comercial da confirmação. Não apaga reserva, comprovantes, recebimentos ou devoluções. Não executa transferência.

Devolução pendente é max(0, recebido líquido atual - valor mantido na decisão). Registros de devolução usam o fluxo manual existente, limitados ao crédito de origem e ao pendente do cancelamento. Após cancelar, a entrada pode ser devolvida conforme a decisão. Recebimento posterior aumenta o pendente e nunca reabre o horário; devoluções já concluídas continuam abatidas. Estados financeiros REFUND_DUE/CANCELED_SETTLED são projeções separadas de CANCELED. Decisão não é editável nesta etapa.

Portal distingue cancelada de expirada, remove PIX/upload e mostra motivo, valor mantido e pendência de devolução. O calendário deixa de incluir a alocação liberada na próxima consulta. Ação fica em Conferência → Gerenciar reserva, aproveitando histórico financeiro e política existentes. Cancelamento pelo próprio cliente e emails automáticos não fazem parte desta entrega.

Próximo recorte de MVP-067: reagendamento do mesmo serviço, seguido de conclusão/falta e reserva assistida/cadastro de clientes. MVP-068 cobre as notificações. Migração de identidade para telefone continua adiada.
