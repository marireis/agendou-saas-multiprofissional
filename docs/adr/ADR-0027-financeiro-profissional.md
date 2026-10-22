# ADR-0027 — Financeiro do profissional

Data: 13/10/2026.

A aba Financeiro (MVP-069) é uma projeção dos lançamentos manuais existentes, independente da assinatura do SaaS. Não cria recebimentos/devoluções, não transfere dinheiro e não representa saldo bancário, lucro ou contabilidade de despesas. Alterações seguem na Conferência, com as regras e auditoria existentes.

`GET /admin/finance` exige ADMIN do tenant da sessão e retorna no-store. Consulta disponível após bloqueio da assinatura. Datas `from`/`to` inclusivas, até 366 dias, interpretadas no fuso atual do perfil; SQL usa intervalo fechado no início e aberto no início do dia seguinte. A interface inicia no mês do dispositivo e mostra período/fuso aplicados. Transação REPEATABLE READ mantém resumo e página no mesmo snapshot.

Movimentações: recebimentos por `received_at`, devoluções por `refunded_at`, mesmo que o atendimento ou recebimento original estejam fora do período. Líquido do período é recebimentos menos devoluções e pode ser negativo. Cada lançamento aparece uma vez; não há produto cartesiano entre recebimentos e devoluções.

Pendências: seleciona atendimentos pelo horário atual `starts_at` no período e considera todos os lançamentos da reserva, inclusive fora do período. Total restante é max(preço − recebido líquido, 0) para reservas não canceladas/expiradas. Inclui valores previstos de solicitações provisórias; não é receita garantida. Entrada pendente é parte desse total, apenas enquanto aguardando pagamento/conferência. Conclusão/falta não quitam saldo. Reservas provisórias vencidas são projetadas como expiradas sem mutação, mesmo antes da execução do worker.

Canceladas: não geram saldo a receber; devolução pendente é max(recebido líquido − valor retido na decisão, 0). Expiradas com crédito líquido e excedentes de reservas não canceladas aparecem como valores a avaliar, sem criar decisão de devolução automaticamente. Somas da seção Pendências não devem ser somadas às movimentações.

Listas paginadas em 50 itens com ordenação estável por data/id; totais abrangem todo o filtro. Paginação por offset pode mudar se houver novos registros entre consultas, e Atualizar/Aplicar período retorna ao início. Valores em centavos, sem soma de ponto flutuante no backend. Não há migration nesta entrega; tabelas e RLS existentes são reutilizadas.
