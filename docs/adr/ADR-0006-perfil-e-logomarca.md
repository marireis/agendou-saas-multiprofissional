# ADR-0006 — Perfil profissional e logomarca

Data: 23/09/2026. Status: implementada.

## Perfil

V006 amplia `public_profiles` com contato comercial (email/telefone), modalidade presencial/online/híbrida e local. Rascunhos incompletos podem ser salvos. Campos novos omitidos/null preservam valores, para compatibilidade com clientes anteriores; string vazia limpa o campo. Slug permanece o reservado no cadastro.

O progresso é calculado no servidor: identidade/fuso, descrição, ao menos um contato e modalidade/local, 25% por grupo. Atendimento online dispensa endereço. Logomarca é opcional. Completar o perfil não publica a agenda nem ignora futuros requisitos de serviços, PIX/política e disponibilidade. Contato é informação comercial para publicação futura, não contato verificado de cliente nem critério antifraude de trial.

## Upload e persistência

- `PUT /admin/profile/logo` recebe bytes brutos (não multipart), com cookie e CSRF. Leitura limitada a 2 MiB + 1 byte, independentemente de Content-Length.
- Formato determinado pelo decodificador, sem confiar em extensão/MIME/nome. Aceita apenas JPEG/PNG decodificáveis; rejeita SVG/GIF, arquivo vazio e conteúdo inválido. Limites antes da decodificação: 4096 por lado e 16 milhões de pixels.
- Decodifica pixels e desenha em nova imagem RGBA, mantendo proporção e transparência, sem ampliar; lado máximo 512 pixels. Recodifica PNG sem metadados ou conteúdo anexado. Original e nome do arquivo são descartados.
- Variante saneada armazenada em BYTEA no perfil, com limite no banco e UUID de versão. Substituição e remoção são transacionais, sem objetos órfãos; ficam sujeitas à mesma RLS do perfil. Não há arquivo privado persistido no disco do container.
- Esta é uma exceção pequena à proposta inicial de S3 para arquivos: uma única imagem pequena por profissional, no mesmo backup do perfil. Comprovantes e futuros arquivos privados continuam previstos no S3. Antes de escalar, medir volume/backup e migrar variantes para object storage se necessário; não replicar esse padrão para comprovantes.
- `GET /admin/profile/logo` entrega só ao titular autenticado, PNG, `nosniff` e `Cache-Control: no-store`. A variante está preparada para uso público, mas sua exposição ficará condicionada à publicação em MVP-034/035. Não há rota pública antecipada nem URL com token.
- Assinatura bloqueada permite leitura, mas impede editar perfil, enviar, trocar ou remover logo. Lock do tenant em SubscriptionService coordena essas mutações com decisões de billing.

## Evidências e limites

Testes cobrem saneamento, proporção/transparência, formatos inválidos, tamanho/dimensões, persistência, CSRF, isolamento, preservação em falha, bloqueio após trial, progresso e compatibilidade de clientes anteriores. Build/tipagem verificam o frontend; homologação visual/acessibilidade e E2E de navegador permanecem necessários. Imagens JPEG com orientação EXIF devem ser exportadas já na orientação desejada; EXIF não é preservado. Atualização concorrente do perfil mantém a política atual de última gravação válida.
