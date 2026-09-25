# ADR-0010 — Configuração de horários

Data: 25/09/2026. Status: aceita.

Complemento: `schedule.intervalMinutes` configura tempo livre mínimo global entre atendimentos, de 0 a 240 minutos inteiros. Valor ausente/null em configurações antigas é interpretado como zero, sem migration. Vale também nas datas especiais. Não representa duração do serviço nem grade de início dos slots. O gerador MVP-041 deverá respeitar tanto esse mínimo quanto os buffers por serviço: a separação mínima entre fim/início de dois atendimentos será o maior valor entre intervalo global e a soma do buffer posterior do anterior com o buffer anterior do próximo. Não somar novamente o intervalo global aos buffers. A configuração pode ser salva agora; reservas permanecem indisponíveis nesta etapa.

MVP-040 entrega configuração privada; MVP-041 a 045 continuam pendentes. A aba Horários salva a semana e exceções em datas específicas. Dias ISO 1–7; até oito períodos por dia, precisão de minutos, início anterior ao fim e sem sobreposição. Períodos adjacentes são permitidos. Pausas são lacunas entre períodos. Exceção substitui o dia inteiro; lista vazia representa feriado/bloqueio total. Bloqueios parciais são os intervalos removidos do expediente excepcional. Datas não podem se repetir; máximo de 366 exceções por configuração.

São regras locais no fuso IANA atual do perfil, não instantes de reservas. Mudar o fuso reinterpreta as mesmas horas locais; a interface informa esse comportamento. A conversão para UTC, transições de horário de verão, duração/buffers, antecedência, horizonte e grade serão tratadas no gerador MVP-041. Não existem slots nem reservas liberadas nesta entrega. Não prometer proteção contra dupla reserva antes de CalendarAllocation/GiST e locks de MVP-042/043/045.

V010 persiste uma configuração JSONB por tenant, protegida por FORCE RLS. O schema da aplicação valida a estrutura completa e os limites; o banco exige objeto JSON. A versão e o lock de tenant compartilhado com a assinatura impedem duas gravações concorrentes de sobrescrever alterações. O runtime não pode excluir a linha. PUT substitui todo o documento e incrementa a versão; conflitos 409 exigem recarga. Limites evitam documentos ilimitados.

Mutação exige assinatura operacional e CSRF; leitura permanece após bloqueio. Observações de exceções são privadas. Reservas futuras devem usar uma ordem de locks compatível e verificar compromissos já existentes antes de aceitar alterações de expediente; isso será implementado junto da alocação real.
