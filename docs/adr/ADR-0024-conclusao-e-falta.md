# ADR-0024 — Conclusão e falta

Data: 05/10/2026. Status: aceita. Continuação de MVP-067.

O profissional registra COMPLETED ou NO_SHOW somente para reserva CONFIRMED após o término previsto. Usar o fim para ambos evita concluir antes do atendimento ou declarar falta durante a janela de comparecimento; não há tolerância automática configurada nesta entrega. Membership ADMIN, tenant derivado, CSRF, assinatura operacional e lock do tenant são obrigatórios. Versão do horário impede encerrar uma agenda reagendada sem nova consulta.

V022 amplia somente os estados de bookings e cria booking_outcomes (um por tenant/reserva), com resultado, versão considerada, observação visível ao cliente, operador/membership, data e correlation ID. RLS e FKs compostas; runtime SELECT/INSERT no histórico. Estado, resultado e booking_event são atômicos. Repetição do mesmo conteúdo é idempotente; resultado/observação diferente retorna conflito. Não há edição/reabertura nesta entrega.

Resultado operacional não significa quitação: payment_intent permanece CONFIRMED e recebimentos/devoluções/saldo continuam independentes. Não cria crédito, multa ou transferência. A alocação histórica BOOKING e a quota COMMITTED permanecem preservadas; o atendimento encerrado não pode ser cancelado ou reagendado pelos fluxos existentes. Lançamentos financeiros posteriores não reabrem o atendimento.

Conferência → Gerenciar reserva mostra o formulário de resultado, horário de término e confirmação explícita. Detalhe profissional e do cliente exibem a observação; calendário/portal distinguem conclusão/falta sem tratá-las como expiração. PIX e novo upload permanecem indisponíveis para esses estados. Operador e correlation ID não são expostos ao cliente.

Próximo recorte: reserva assistida com cadastro de clientes (MVP-067/MVP-070). Emails de resultado e demais notificações ficam em MVP-068. Identificação por telefone segue adiada.
