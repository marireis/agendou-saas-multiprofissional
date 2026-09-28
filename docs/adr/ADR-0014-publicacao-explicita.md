# ADR-0014 — Publicação explícita com disponibilidade real

Data: 28/09/2026. Status: aceita.

GET /admin/publication calcula os requisitos do próprio tenant: perfil completo (logo opcional), assinatura operacional, pelo menos um serviço ativo, última versão PIX/política habilitada e ao menos um candidato real entre hoje e hoje+59 no fuso do perfil. Usa SlotGenerator, incluindo antecedência de 24h, duração, buffers, intervalo global, exceções, bloqueios e HOLDs não expirados. Expediente cadastrado sem horário utilizável não basta.

A decisão obtém primeiro o lock do tenant via SubscriptionService.current. Configurações e alocações usam esse mesmo lock; POST revalida dentro da transação antes de marcar published=true. Uma prévia positiva não autoriza publicar caso os requisitos tenham mudado. DELETE usa a mesma coordenação, funciona com assinatura bloqueada e mantém os dados. Repetições não duplicam efeitos. Membership, RLS e CSRF permanecem obrigatórios.

A interface informa quais dados serão públicos e exige clicar em Publicar página. Não publica automaticamente ao concluir configuração, ao reativar assinatura ou ao executar esta implementação. Após publicar, oferece o link e a retirada. Modificações salvas de perfil/serviços são refletidas na projeção pública existente; não há snapshot de conteúdo publicado nesta etapa.

Publicação é da apresentação comercial, não do fluxo de Booking. bookingAvailable continua false, sem chave PIX, motivos privados ou identificadores de tenant na projeção anônima. A elegibilidade é verificada ao publicar, não a cada leitura pública: lotação posterior não retira a página automaticamente. Suspensão/retirada administrativa mantém o comportamento existente. GET mostra requisitos atuais mesmo para página publicada.

Sem migration, reutiliza published e funções de projeção da V009. Próxima entrega: compartilhamento de link/mensagem WhatsApp (MVP-036); fluxo de reservas MVP-050+ habilitará o CTA de agendamento depois.
