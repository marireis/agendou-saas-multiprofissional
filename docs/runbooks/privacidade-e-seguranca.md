# Privacidade e segurança — homologação

Atualizado em 22/10/2026. Este documento descreve procedimentos técnicos. Não define política de retenção nem prazo legal. MVP-071/072 permanecem parciais até validação operacional e decisões de produto.

## Atendimento de solicitações sobre dados

1. Registrar identificador da solicitação, tenant, escopo, responsável e resultado da verificação da identidade. Não guardar documento pessoal em logs, motivo de reemissão ou conversa de suporte. Nome/telefone sozinhos não autorizam entrega de dados.
2. Determinar as reservas/cadastro do solicitante no tenant correto. Alterar o cadastro não transfere a titularidade das reservas antigas nem substitui automaticamente os dados originais exigidos pelo código.
3. Correção cadastral: usar Clientes com controle de versão, verificar o histórico e o acesso do cliente depois. Correção da identidade original da reserva não possui fluxo implementado; encaminhar para análise, sem SQL improvisado.
4. Exportação: ainda não há recurso operacional validado. Antes de implementar, definir campos, filtragem por titular/tenant, formato, autenticação, prazo de acesso e auditoria. Excluir códigos, tokens, hashes, segredos MFA/PIX e dados de outros clientes. Não encaminhar backup integral como exportação de titular.
5. Exclusão: ainda não implementada. Identificar dependências de reserva, recebimento, devolução, comprovante e auditoria; definir retenção e aprovação aplicáveis antes de qualquer remoção/anonimização. Não apagar tabelas ou objetos manualmente para atender uma solicitação. Planejar também backups e restaurações para evitar reintrodução dos dados.
6. Entregar resultado por canal previamente verificado e registrar conclusão sem conteúdo sensível. Exportação/exclusão só serão consideradas prontas após teste com dois tenants e auditoria do resultado.

## Controles e limites atuais

- Sessões HTTP-only, CSRF em mutações, RLS e vínculo de tenant/ADMIN no backend. Secure cookie precisa estar ativo em HTTPS de produção; exceção local só para HTTP localhost.
- Código privado de reserva aleatório, hash no domínio, reemissão com confirmação profissional/versão/auditoria. A reemissão não recolhe arquivos já baixados.
- Comprovantes privados com autorização, attachment, no-store, nosniff e sandbox. Não tornar bucket público. Teste com MinIO real cobre upload/leitura/remoção e negação anônima.
- Logs de workers inspecionados usam IDs/contagens, sem conteúdo de email ou comprovante. Revisão não equivale a auditoria exaustiva de logs de proxy, APM e produção; não habilitar logging de corpos/tokens.
- Páginas recebem no-referrer, nosniff, DENY, restrição de câmera/microfone/geolocalização e CSP para base/object/frame/form. **CSP ainda parcial:** não restringe scripts/styles; política com nonce/hash depende de adequação do Next e do script de tema, teste de hidratação e rollout. Não foi adicionada uma política bloqueante de scripts sem essa validação.
- API tem no-referrer e DENY; cabeçalhos de arquivos privados permanecem específicos. Sem mudança de HSTS/TLS local; produção precisa HTTPS e configuração de ingress.
- Rate limit atual usa o IP da conexão; proxy Next pode concentrar usuários no mesmo endereço. X-Forwarded-For público não é confiável. Configurar rede/ingress confiáveis antes do piloto; não publicar expondo a API para contornar o limite.

## Verificação repetível

- Backend: `mvnw.cmd verify` em `apps/api`, com Docker e imagem local MinIO disponíveis. Abrange identidade, MFA, trial, RLS, agenda, reserva, arquivos, financeiro e recuperação. Testes de integração HTTP não substituem E2E do navegador.
- Frontend: `npm run build` em `apps/web`.
- Ambiente iniciado: `powershell -NoProfile -File infra/local/verify-http-security.ps1`. Verifica status/cabeçalhos de páginas e rejeição de consulta privada sem sessão, diretamente e pelo proxy. Não autentica, não altera registros e não comprova acessibilidade/fluxos visuais.
- Pendentes: navegação autenticada por teclado/leitor, avaliação de carga representativa, privacidade operacional, CSP completa, segredo/ingress de produção, backup/restore com RPO/RTO medidos e monitoração.
