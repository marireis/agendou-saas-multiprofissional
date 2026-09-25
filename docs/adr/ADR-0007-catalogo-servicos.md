# ADR-0007 — Catálogo de serviços e navegação profissional

Data: 23/09/2026. Status: implementada.

- V007 cria services com tenant obrigatório, RLS forçada e role runtime sem DELETE. Exclusão funcional é inativação; o registro permanece disponível para histórico e futuras referências de reservas.
- Duração inteira 5–480 minutos, preço inteiro 1–100.000.000 centavos, entrada inteira 50–100%. Intervalos antes/depois de 0–240 minutos cada, limite operacional adotado nesta entrega. API e constraints do banco validam os limites.
- Entrada calculada no servidor com arredondamento para cima: `(priceCents * depositPercent + 99) / 100`, usando long. A configuração representa uma exigência futura; não cobra nem confirma pagamento.
- JSON fracionário para campos inteiros é rejeitado, sem truncamento silencioso. Frontend converte valor decimal em centavos separando parte inteira/fracionária.
- Tenant vem da membership, nunca do corpo. Leitura permitida em assinatura bloqueada; criação/edição/inativação/reativação exigem assinatura operacional e lock do tenant.
- Cada edição inclui version. UPDATE incrementa versão; versão desatualizada produz 409 sem sobrescrever. Não existe DELETE público. Listagem paginada em 50 registros, com total e quantidade de ativos do próprio tenant.
- POST não é idempotente: interface bloqueia envio durante operação, sem repetição automática. Se houver falha de rede após cadastro, consultar a lista antes de repetir. Idempotência do fluxo de reservas permanece em MVP-053.
- Painel separado em Visão geral, Minha página, Serviços e Assinatura, com menu lateral desktop e menu adaptado mobile. Sem abas vazias de funcionalidades futuras, indicadores fictícios ou publicação implícita.

Próximo: PIX/política (MVP-033), seguido de página pública com requisitos reais. Serviços não ficam publicamente reserváveis nesta entrega. Snapshots e ocupação por duração/intervalos entram na reserva/disponibilidade.
