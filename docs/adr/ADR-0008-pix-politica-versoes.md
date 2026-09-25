# ADR-0008 — PIX, política e versões imutáveis

Data: 25/09/2026. Status: implementada.

## Configuração e escopo

MVP-033 adiciona `/painel/pagamentos` (PIX e política), separado da assinatura do SaaS e do futuro financeiro. Recebe tipo/chave PIX, nome do recebedor, instruções de pagamento e política de cancelamento/reagendamento. Textos são escritos pelo profissional, sem modelo de regras legais ou promessa de devolução automática. Entrada permanece definida em cada serviço (50–100%).

O responsável confirma que conferiu os dados antes de salvar. Essa declaração não comprova titularidade. Desativar a configuração preserva as versões; o sinal enabled será um pré-requisito de futuras reservas/publicação. Configuração não publica página, cria reserva, gera cobrança, QR Code ou confirma pagamento.

## Validação

Formatos locais: email normalizado em minúsculas (até 77 caracteres), telefone internacional iniciado por +, UUID de chave aleatória, CPF com DV e CNPJ numérico/alfanumérico com DV. Normalização remove somente separadores aceitos, sem aproveitar caracteres arbitrários de uma chave inválida. Não há consulta ao DICT nem envio de dados a bancos. Uma chave com formato correto pode não estar registrada.

Fontes técnicas consultadas em 25/09/2026: [API DICT do Banco Central](https://www.bcb.gov.br/content/estabilidadefinanceira/pix/API-DICT.html), [changelog do DICT](https://www.bcb.gov.br/content/estabilidadefinanceira/pix/changelog.html) e [cálculo do DV alfanumérico da Receita Federal](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf). O exemplo alfanumérico oficial integra os testes. Validação documental não afirma que a chave já é aceita/registrada pelo banco do profissional.

## Persistência e auditoria

V008 cria payment_settings_versions com PK (tenant_id,version), RLS forçada e FK composta de ator/membership. Runtime tem apenas SELECT/INSERT. Cada mudança cria uma linha completa; a maior versão é a atual. Nenhuma versão é atualizada/apagada. Versões preparam referência composta e cópia de condições na futura reserva; snapshots por reserva não existem ainda.

Salvar exige CSRF e assinatura operacional, usando lock do tenant. Versão recebida deve coincidir com a atual (zero na primeira gravação). Concorrência produz um vencedor e um 409, sem sobrescrita. Salvar dados normalizados idênticos não cria revisão. Reenvio com versão antiga exige recarregar, sem repetir silenciosamente.

Cada revisão registra ator, instante UTC, motivo e correlation ID. Histórico paginado mostra apenas metadados e nomes dos campos alterados, sem chave, nome do recebedor, textos ou motivo livre. Configuração atual é entregue somente ao titular autenticado com no-store; não aparece no console do superadmin. Não há endpoint público nem consulta de versões antigas com dados completos.

As chaves ficam em colunas privadas no banco, sem cifragem adicional na aplicação nesta entrega. TLS, criptografia de volume/backup e acesso operacional restrito devem ser aplicados no deploy. Não registrar corpos, chaves ou textos em logs/telemetria; DTO de entrada possui toString redigido. Retenção/restrição de histórico com dados pessoais precisa ser definida antes do piloto.

## Próximo

Perfil público e critérios de publicação (MVP-034/035), mantendo reservas indisponíveis até a disponibilidade real. Integração bancária, conferência de comprovantes, snapshots de reserva e automação de cancelamento continuam nas respectivas etapas do backlog.
