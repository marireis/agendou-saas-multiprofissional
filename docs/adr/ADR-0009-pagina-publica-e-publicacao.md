# ADR-0009 — Página pública e publicação condicionada

Data: 25/09/2026. Status: aceita.

MVP-035 exige perfil completo, serviço ativo, PIX/política e disponibilidade real. A disponibilidade não existe nesta entrega. Portanto, a página `/a/{slug}` e sua projeção estão preparadas, mas nenhuma conta é publicada automaticamente e POST de publicação retorna 409 (403 para assinatura bloqueada). O painel oferece prévia privada em Publicação e requisitos calculados. A liberação será concluída com MVP-040 a 045; não introduzir um booleano de disponibilidade configurável pelo cliente.

V009 adiciona `published=false` ao perfil sob a RLS existente. Dois leitores SQL SECURITY DEFINER expõem somente a projeção pública e a variante saneada da logo de perfis publicados. O proprietário é a role de migração com acesso às tabelas, nunca o runtime. Nomes de tabelas qualificados, search_path fixado e EXECUTE revogado de PUBLIC; somente runtime recebe EXECUTE. O runtime continua sem acesso direto a outros tenants. Antes de alterar a role de migração em produção, validar permissões desses leitores e FORCE RLS.

Não há parâmetros de tenant no acesso anônimo nem mudança de contexto RLS a partir do slug. A projeção omite PIX, instruções/políticas privadas, identidade de usuário e tenant. Serviços inativos não aparecem. Rascunho e slug inexistente retornam o mesmo 404, incluindo logo. Respostas e fetch público não usam cache para que retirada e mudanças sejam imediatas.

Reservas continuam indisponíveis em todos os estados. Uma futura página publicada pode permanecer visível durante bloqueio de assinatura, sem liberar reservas. DELETE autenticado com CSRF permite retirar somente a própria página mesmo após bloqueio: é uma ação de privacidade, não uma operação comercial. Não apaga perfil ou histórico.

Testes de projeção publicada usam dados sintéticos e ativação direta pelo proprietário apenas no banco descartável. Isso não constitui liberação de publicação na aplicação. Próxima entrega: calendário, regras semanais, pausas, exceções e bloqueios; em seguida concluir o gate real de publicação e compartilhamento.
