# ADR-0017 — Acesso privado a Meus agendamentos

Data: 29/09/2026. Status: aceita.

A área /a/{slug}/meus-agendamentos funciona independentemente da publicação da página ou da assinatura operacional. O cliente existente solicita link com o mesmo email da reserva. Resposta 202 genérica também para slug/email inexistentes; limites por email/conexão e CSRF reutilizam a infraestrutura de identidade. A área não permite novas reservas quando a operação do profissional está bloqueada.

V014 adiciona customer_portal_tokens com FORCE RLS, hash SHA-256 de segredo aleatório de 256 bits e validade de 15 minutos. Um token por tenant/email; reenvio revoga anterior e cancela email ainda pendente. Consumo exige correspondência de slug, email, hash e validade e elimina o token atomicamente. Token/email trafegam no fragmento do link, removido da URL pela tela; consumo somente após clique explícito. Outbox CUSTOMER_PORTAL utiliza o dispatcher e limpeza existentes. Funções mínimas de vigência/limpeza incluem o novo tipo de token.

O consumo rotaciona o ID da sessão e cria acesso serializável com slug/email e validade absoluta de uma hora. Criar uma reserva com email já verificado também abre esse acesso. Não concede Authentication administrativa. Sair remove os acessos de cliente, recibo e revisão da sessão, preservando eventual login do profissional. Consultas exigem sessão vigente, slug correspondente e propriedade por email, com tenant/RLS derivados pelo servidor.

Lista paginada (20, offset até 10000) por início decrescente e ID, com hasMore. Detalhe inclui protocolo, estado, prazo, fuso, valores, modalidade/local, política aceita e eventos. Campos vêm dos snapshots; chave PIX/instruções privadas e contatos de terceiros não são expostos. Expiração síncrona precede leitura sob lock do tenant. No-store em todas as respostas privadas; frontend atualiza a cada 30 segundos, recalcula prazo e encerra exibição de dados ao receber 401. Política de robots impede indexação da tela.

O portal consulta os estados atuais AWAITING_PAYMENT/EXPIRED. Não acrescenta pagamento, cancelamento, reagendamento ou confirmação fictícios. Em seguida, MVP-056 completa a operação de expiração; futuros estados entram junto ao fluxo de PIX/conferência. Limites comerciais de planos permanecem uma decisão separada.
