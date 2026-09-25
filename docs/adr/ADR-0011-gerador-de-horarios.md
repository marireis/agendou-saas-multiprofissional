# ADR-0011 — Gerador de candidatos e prévia privada

Data: 25/09/2026. Status: aceita.

MVP-041 calcula candidatos, sem criar reservas ou alocações. A prévia autenticada usa o próprio tenant, serviço ativo, configuração salva e fuso atual do perfil em snapshot REPEATABLE READ. Nenhum identificador de tenant vem do cliente. Leitura segue disponível após bloqueio da assinatura; resultado sempre bookingAvailable=false. Publicação continua bloqueada até completar os mecanismos de reserva.

Data consultada deve estar no intervalo de hoje até hoje+59, no fuso do estabelecimento. Início deve ser pelo menos 24 horas após o instante atual, inclusivo. A grade de 15 minutos é ancorada à meia-noite local (09:07 começa a oferecer 09:15). Duração é tempo decorrido; buffer anterior e posterior precisam caber no mesmo período de expediente. Exceção substitui integralmente o dia; vazia fecha o dia. Nenhum serviço cruza pausa ou limite do período.

Conversão explícita para UTC por regras IANA. Por segurança, períodos com extremidades ambíguas/inexistentes ou que cruzem uma transição de offset são omitidos nesta versão. Outros períodos daquele dia continuam disponíveis. Não deslocar silenciosamente um horário inexistente nem escolher um dos offsets sem informar. A interface explica a ausência nestes casos. Ampliação para períodos que cruzam transições exige regra adicional e testes.

Candidatos são alternativas de início e podem se sobrepor. Uma segunda lista oferece uma sequência ilustrativa, escolhendo os primeiros candidatos compatíveis; ela não é uma reserva nem uma otimização de capacidade. A separação entre atendimentos respeita max(intervalo global, buffer posterior anterior + buffer anterior seguinte), inclusive na direção inversa. Limites são semiabertos; igualdade exata é permitida. O núcleo aceita ocupações e testa conflitos, mas a API ainda passa lista vazia porque CalendarAllocation não existe. Não declarar horários livres de compromissos antes de MVP-042/043/045.

Painel exige salvar edições antes da prévia e invalida resultados ao alterar serviço/data/configuração. Não há liberação pública, confirmação, armazenamento de prévia ou migration. Próxima entrega: tabela de alocações com GiST, ordem de locks e tratamento transacional 409; depois calendário de compromissos e reserva.
