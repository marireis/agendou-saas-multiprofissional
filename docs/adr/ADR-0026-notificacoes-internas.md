# ADR-0026 — Notificações internas por administrador

Data: 13/10/2026.

O agendamento por telefone não coleta email e não há provedor WhatsApp/SMS configurado. A primeira entrega de MVP-068 é uma central interna em `/painel/notificacoes`. O portal privado continua sendo o canal de consulta do cliente, tanto para reservas por código quanto para as antigas por email. Esta entrega não envia mensagens nem modifica a outbox de autenticação.

Os avisos derivam de `booking_events`, gravados na mesma transação das operações. Cobrem solicitação, comprovante em conferência, confirmação, reagendamento, expiração, cancelamento, conclusão, falta, recebimento e devolução. `ASSISTED_REQUESTED` é informação complementar: a solicitação assistida já produz `REQUESTED`, portanto não gera um segundo aviso. Eventos históricos também aparecem; os nomes dos serviços vêm do snapshot da reserva e o horário exibido é explicitamente o horário atual.

V025 acrescenta `notification_reads`: leitura individual por administrador e evento, inserção idempotente, FKs compostas para evento e membership, FORCE RLS e permissões SELECT/INSERT. A API deriva tenant e usuário da sessão, exige ADMIN e CSRF para marcar leitura. Leitura e marcação ficam disponíveis após bloqueio da assinatura, sem reativar operações de negócio. As respostas são `no-store`; não contêm telefone, email, código privado ou chave PIX.

A listagem retorna 50 eventos por página, contagem de não lidas e cursor pelo par `(created_at,id)`, inclusive para timestamps iguais. O cursor é validado dentro do tenant. A interface consulta ao abrir, trocar filtro ou atualizar; não há push nem polling nesta etapa. Marcar leitura recarrega a primeira página. O botão Ver atendimento abre a reserva correspondente na Conferência, cuja autorização continua no backend.

Pendente para ampliar MVP-068: definir canal e consentimento para mensagens externas, contratar/configurar provedor, implementar entrega/retry e comprovação de falhas. Nenhum aviso interno equivale a mensagem entregue ao cliente. Não há recuperação/reemissão de código nesta entrega.
