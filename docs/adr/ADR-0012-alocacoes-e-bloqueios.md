# ADR-0012 — Alocações, bloqueios e coordenação

Data: 25/09/2026. Status: aceita.

V011 cria calendar_allocations com recurso único por tenant (resource_id=tenant_id), RLS, FK composta ao serviço, instantes UTC, buffers imutáveis por operação e faixa protegida semiaberta. GiST exclui sobreposição ativa por tenant/recurso, incluindo buffers. Intervalo global entre atendimentos é verificado no gerador sob lock do tenant, pois depende de dois atendimentos; não é somado cegamente aos buffers. SQL direto que contorne o serviço tem apenas a proteção GiST das faixas, não as regras comerciais de espaçamento.

Dois tipos iniciais: BLOCK para impedimento pessoal e HOLD para ocupação temporária interna. BLOCK não tem buffer nem expiração; usa início/fim locais no fuso do perfil e rejeita limites ambíguos/inexistentes, segundos, passado e duração acima de 366 dias. Pode atravessar datas. Uma faixa bloqueada não exige intervalo de descanso de cliente, mas respeita buffers de atendimento. HOLD copia duração/buffers do serviço, valida candidato e assinatura, expira em 30 minutos. Não existe endpoint de HOLD: identidade do cliente, quota, idempotência, snapshots de preço/PIX, confirmação e cancelamento ainda dependem de MVP-050+.

Ordem de mutação: tenant (mesmo lock de assinatura/expediente/perfil/serviço), desativação de HOLDs expirados desse tenant, leitura de configuração e ocupações, gravação. GiST é a segunda barreira e retorna 409 pela tradução padrão da API. Leituras ignoram HOLDs vencidos imediatamente; desativação física é preguiçosa, antes da próxima mutação de alocação. Não há now() no predicado da constraint e não é necessário worker para liberar disponibilidade observada. Futuras mudanças de status de reservas devem respeitar a mesma ordem de locks.

Prévia passa a consultar ocupações persistidas, mantendo snapshot consistente e sem confirmar reservas. Pausas/exceções continuam sendo regras de expediente, enquanto BLOCK permanece até liberação explícita, mesmo após mudar o expediente. GET lista até 50 bloqueios ativos futuros/em andamento. DELETE desativa sem apagar histórico; pertence ao tenant autenticado e exige CSRF/assinatura operacional.

Estratégia conservadora nesta etapa: qualquer HOLD futuro não expirado impede salvar expediente/intervalo ou mudar o fuso, com 409. Isso evita invalidar ocupações existentes sem fluxo de reagendamento. Edições de serviço não reescrevem buffers/duração de HOLD existente. Bloqueios reais são verificados pela GiST contra a faixa protegida. Após implementar reservas confirmadas, ampliar a regra para esses estados e oferecer decisões de reagendamento; não marcar a integração de reservas como concluída agora.

Não publicar nem cobrar automaticamente. Próxima entrega: calendário administrativo diário/semanal (MVP-044), seguido de integração de reservas com essa base de alocação.
