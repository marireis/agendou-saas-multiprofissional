# ADR-0018 — Expiração coordenada em lotes

Data: 29/09/2026. Status: aceita.

MVP-056 substitui a varredura de memberships por uma fila de coordenação por tenant. V015 cria booking_expiration_jobs, sem acesso direto do runtime, com RLS e funções mínimas SECURITY DEFINER. Inicializa tenants existentes e usa trigger para novos tenants. A descoberta retorna somente tenant/token de trabalho, nunca dados de clientes, e seleciona apenas tenants com AWAITING_PAYMENT vencida e tentativa liberada.

Claim usa FOR UPDATE SKIP LOCKED, grava UUID aleatório e lease de dois minutos em transação curta. Cada ciclo, inicialmente a cada 60 segundos, admite até 20 lotes de até 100 reservas. Uma instância reivindica um lote por vez. Ordenação por próxima tentativa/tenant e atualização da data após cada lote permitem atender outros tenants pendentes. Falha de processo deixa lease recuperável após dois minutos.

Processamento abre outra transação, aplica contexto/RLS, bloqueia tenant e depois valida/bloqueia o registro do lease pelo token e prazo. Uma execução atrasada não pode processar nem finalizar um token substituído. O lock do registro permanece até commit; SKIP LOCKED evita outra reivindicação durante uma transação ativa mesmo se o tempo nominal do lease terminar. lock_timeout de 5 segundos e statement_timeout de 30 segundos limitam esperas por comando.

A transação altera reserva para EXPIRED, libera alocação e quota HELD, registra evento EXPIRED e finaliza o lease. Falha desfaz tudo. Fora da transação falha, nova transação registra tentativa e espera progressiva de 30 segundos até 15 minutos; token antigo não pode alterar a tentativa de um sucessor. Falha ao registrar a tentativa deixa recuperação pelo prazo do lease. Não há descarte terminal de trabalho; logs registram tenant/quantidade ou código de falha, sem conteúdo pessoal nem mensagem SQL.

Limpeza síncrona antes de criar/consultar reservas continua completa sob lock do tenant, garantindo quota correta mesmo com worker atrasado. Compartilha a transição idempotente com o worker; eventos não são duplicados. Reservas ainda vigentes são preservadas. Nenhum histórico ou snapshot é removido.

Escopo atual: AWAITING_PAYMENT → EXPIRED. Futuro estado EmConferencia e tratamento de pagamento tardio serão integrados no fluxo PIX; comprovante não implica confirmação automática. Alertas, painel operacional e dimensionamento de produção continuam na homologação.
