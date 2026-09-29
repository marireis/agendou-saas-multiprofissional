# ADR-0019 — Base PIX manual e prévia privada

Data: 29/09/2026. Status: aceita.

V016 cria payment_intents, payment_transactions, payment_evidence e payment_refunds, com FORCE RLS e FKs compostas. Uma intenção por reserva, criada junto à reserva/alocação/quota/outbox na mesma transação. Reservas existentes recebem intenção a partir do snapshot/prazo original, sem renovar prazo. Expiração altera a intenção para EXPIRED na mesma transação da reserva. Runtime só pode inserir intenção/alterar seu estado; tabelas de recebimento/comprovante/devolução têm apenas SELECT nesta etapa.

PaymentProvider tem implementação ManualPixProvider, consultada após validar sessão e propriedade. GET /public/{slug}/client/bookings/{id}/payment retorna total, entrada, saldo projetado após entrada, recebedor, chave, instruções e prazo. Dados PIX vêm do snapshot da reserva, não das configurações mais recentes do profissional. Sem chave em projeções públicas, listas ou recibos gerais; resposta no-store. Reserva vencida omite chave/recebedor/instruções. Leitura continua disponível com página retirada ou assinatura bloqueada.

Recibo e detalhe em Meus agendamentos oferecem prévia sob clique explícito, copiar chave com alternativa de seleção manual, prazo e ocultação dos dados. Poll de 15s e cálculo local removem dados ao expirar; erro de consulta remove a chave exibida. Saldo projetado não é saldo validado: nenhum recebimento foi registrado nesta entrega.

MVP-060 implementa a estrutura e a interface do provedor; MVP-061 implementa a tela em modo prévia. paymentAvailable permanece false enquanto comprovante/conferência não estiverem integrados (MVP-062 a 065). A interface instrui não transferir por esta etapa. Não existe endpoint para receber/validar valor, confirmar reserva ou registrar devolução, nem consulta bancária/transferência automática.

As tabelas futuras reservam referência bancária única por tenant, montantes positivos, auditoria de operador e vínculo ao intent; evidence tem referência de objeto privado, hash, tipo e limite de 5 MB. Não significa que upload, saneamento, armazenamento ou regras de devolução já existam. Normalização bancária, vínculo do operador ao tenant, limite líquido de devolução e conferência manual devem ser impostos nas operações futuras antes de conceder INSERT ao runtime nessas tabelas. Mudanças de estados entrarão com essas operações.

Próxima etapa: MVP-062/063, recebimento privado de comprovantes e estado EmConferencia, seguido da conferência manual/validação MVP-064/065. Upload nunca confirma pagamento automaticamente. Ajuste de identificação por telefone segue adiado.
