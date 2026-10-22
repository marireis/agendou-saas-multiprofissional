# ADR-0023 — Reagendamento do mesmo serviço

Data: 05/10/2026. Status: aceita. Continuação de MVP-067.

O profissional reagenda reservas AWAITING_PAYMENT, IN_REVIEW e CONFIRMED antes do início, com assinatura operacional, membership ADMIN, CSRF e lock do tenant. Página retirada ou serviço posteriormente inativado não impede honrar o atendimento contratado. Serviço, cliente, protocolo, preço, entrada, política, PIX e lançamentos permanecem os mesmos. Duração e buffers vêm da alocação original, não do catálogo editado depois da compra.

Consulta de horários reutiliza SlotGenerator com expediente/exceções atuais, fuso da reserva, antecedência de 24h, horizonte de 60 dias, intervalo e demais alocações. Exclui apenas a própria ocupação para permitir trocas parcialmente sobrepostas ao horário original. Consulta não reserva horário. No POST o servidor recalcula tudo sob lock; exclusão SQL continua impedindo sobreposição. Falha preserva o horário original.

V021 adiciona schedule_version e booking_reschedules, com horários anteriores/novos, motivo visível ao cliente, chave de repetição, operador/membership, data e correlation ID. RLS e FKs compostas; auditoria append-only para runtime. Versão rejeita edição desatualizada mesmo se a agenda tiver voltado ao horário anterior. Chave idempotente vinculada à reserva e conteúdo; repetição igual devolve estado atual sem executar novamente, conflito se conteúdo diferente.

Na mesma transação, muda os horários da reserva/alocação, faixas protegidas, versão, mês da quota e histórico/evento. Não troca status nem renova prazo de pagamento/conferência. Ao mudar de mês, valida limite do plano no destino contando HELD/COMMITTED e excluindo a própria reserva; preserva estado da quota. Transferir a quota não cobra nem cria uma nova reserva.

O profissional usa Conferência → Gerenciar reserva → Reagendar o mesmo serviço. A área privada do cliente e o profissional consultam os horários anterior/novo e motivo; calendário passa a consultar o novo horário. Não há alteração automática de preço/serviço, reagendamento pelo próprio cliente ou novo email nesta entrega. Notificações serão completadas em MVP-068.

Próximo recorte: concluir atendimento e marcar falta; depois reserva assistida e cadastro de clientes. Migração de identificação para telefone permanece adiada.
