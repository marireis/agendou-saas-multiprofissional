# ADR-0025 — Clientes, reserva assistida e acesso por código

Data: 06/10/2026. Decisão solicitada e confirmada pela responsável.

O agendamento público exige nome, sobrenome e telefone brasileiro com DDD, sem conta, login ou email. Serviço/data/horário precedem contato e revisão explícita de valores/política. O código privado é entregue após criação, conforme opção escolhida pela responsável, sem integração WhatsApp/SMS.

Cada reserva tem um segredo aleatório de 32 bytes (43 caracteres base64url). O banco de domínio guarda somente SHA-256. O código em claro fica temporariamente na sessão privada do servidor para recuperar o recibo por uma hora; não entra na URL, logs nem na resposta idempotente persistida. O cliente deve copiar e guardar o código. Não há recuperação automática nem reemissão nesta entrega; o profissional pode consultar e informar o atendimento após verificar o solicitante por seu procedimento operacional.

Meus agendamentos valida nome completo normalizado, telefone normalizado e código. A sessão dura uma hora e só autoriza a reserva daquele código, inclusive pagamento/comprovantes. Mesmo nome/telefone não autoriza outros agendamentos. O acesso continua quando a página é retirada ou a assinatura bloqueada. Sair remove também revisão/recibo temporários. CSRF, no-store, limites por conexão e por telefone permanecem obrigatórios.

Clientes sem email são agrupados por tenant, telefone e nome normalizado para o cadastro administrativo; isso não comprova identidade nem concede leitura do histórico. Nomes são normalizados em caixa/acentos/espaços para consulta; telefones aceitam formatação e prefixo brasileiro +55. Reservas antigas por email mantêm o acesso por link numa opção separada. Não são mescladas automaticamente com registros sem email. Email passa a aceitar NULL (V024). Os dados de acesso ficam preservados por reserva, de modo que editar contato no cadastro não transfere o acesso histórico.

A aba Clientes (V023) permite cadastro, busca paginada, edição com versão e histórico. Email de cadastro com reservas permanece imutável para proteger o acesso legado. Telefone é obrigatório quando não há email. Operações administrativas exigem ADMIN/assinatura operacional; leitura preservada após bloqueio. Mudanças cadastrais e autoria da reserva assistida têm auditoria append-only, FKs compostas e FORCE RLS.

A reserva assistida compartilha disponibilidade, snapshots, quota, intenção PIX e idempotência com a criação pública, sob lock de tenant. Pode operar com página não publicada. Para cadastro sem email, entrega código ao profissional, que deve repassá-lo ao cliente. A reserva permanece AWAITING_PAYMENT por 30 minutos até a conferência; não há confirmação nem envio de mensagem automático. Repetições não duplicam reserva; falha no registro de código/auditoria desfaz todas as gravações.

Notificações MVP-068 precisam ser revistas: clientes sem email não receberão emails. WhatsApp/SMS não foram contratados/configurados; o portal é o canal de consulta atual. Limites comerciais mensais seguem sem definição, com mecanismo de quota existente preservado.
