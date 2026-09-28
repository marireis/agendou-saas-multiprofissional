# ADR-0013 — Calendário administrativo diário/semanal

Data: 28/09/2026. Status: aceita.

MVP-044 adiciona `/painel/calendario`, separado da configuração em Horários. Desktop abre na semana (segunda a domingo), celular no dia selecionado; usuário pode alternar Dia/Semana, escolher data, navegar e voltar a Hoje. Sem drag-and-drop. Expediente atual, folgas/exceções e ocupações reais aparecem por dia, com legenda textual. HOLD é explicitamente temporário e não uma reserva confirmada; não se inventam clientes ou serviços agendados.

GET `/admin/calendar?from=YYYY-MM-DD&days=1|7` usa membership, RLS e transação REPEATABLE READ. Omissão de from usa hoje no fuso do perfil. Consulta delimitada em até sete dias e ano inicial 2000–2100. Limites são inícios de dias civis no fuso IANA, nunca acréscimo fixo de 24 horas; dias com transição de offset podem ter 23/25h. Respostas no-store.

Uma alocação entra se protected_start < fim exclusivo e protected_end > início, incluindo buffers e faixas que começaram antes do intervalo. active deve ser true, e expires_at ausente ou futuro no instante da consulta. HOLDs vencidos e registros liberados não aparecem. Um bloqueio que atravessa dias aparece em cada dia atingido, com início/fim originais e mesma identidade. Expediente de datas passadas é a configuração atual; a interface informa que não é histórico de versões. Não se deduz disponibilidade de reserva somente pelo expediente visualizado.

Criação/liberação reutilizam as rotas e restrições existentes. Mutação atualiza calendário e lista; navegação descarta respostas antigas por contador de requisições. Atualização periódica a cada minuto e botão manual reduzem defasagem das ocupações temporárias. A consulta permanece acessível após bloqueio da assinatura, enquanto mutações seguem impedidas. Datas, motivos e ocupações são privados.

Sem migration. A futura inclusão de Booking precisará representar estados, relacionamentos e autorização próprios; este calendário não antecipa confirmação de pagamento ou atendimento. Próxima etapa: substituir o bloqueio fixo de publicação por requisitos reais, mantendo CTA de reserva indisponível até MVP-050+.

## Revisão em 28/09 — grade mensal e indicadores

Substitui a apresentação inicial acima: Mês é a visualização padrão em 42 células (segunda a domingo), Semana usa sete células e Dia uma lista. A API aceita 1/7/42 dias. Cada dia retorna status e occupiedCount. UNAVAILABLE/vermelho tem prioridade se nenhum serviço ativo tem candidatos no SlotGenerator, incluindo horizonte, antecedência, buffers e ocupações. Havendo disponibilidade, OCCUPIED/amarelo indica HOLD vigente cujo atendimento cruza o dia; FREE/verde indica ausência dessas ocupações. BLOCK não é atendimento. Tudo é calculado no mesmo snapshot com RLS. A integração cobre disponibilidade, lotação, expiração e bloqueio integral.

Aparência global clara/escura via botão sol/lua; localStorage guarda apenas a preferência agendou-theme. Script inicial evita flash, eventos storage sincronizam abas. Não altera conta, autorização ou dados de agendamento.
