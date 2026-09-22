# Agendou - Passo a passo para desenvolvimento com IA

Use este arquivo como roteiro de execucao com GitHub Copilot em Agent Mode ou outro agente de codificacao homologado. Execute uma tarefa por vez, valide e registre evidencias antes de avancar.

## 1. Regras para trabalhar com IA

- Sempre fornecer ao agente o objetivo, arquivo alvo, criterio de aceite e teste esperado.
- Pedir mudancas pequenas e verificaveis.
- Nao aceitar codigo sem teste ou sem justificativa quando tocar seguranca, dinheiro, agenda ou tenant.
- Nunca pedir para remover teste para passar build.
- Nao permitir que a IA invente versoes, comandos ou servicos; ela deve descobrir no repositorio.
- Revisar manualmente migrations, RLS, permissoes e regras financeiras.
- Tratar PASS tecnico como evidencia, nao como validacao de negocio.

## 2. Prompt base para cada tarefa

```text
Voce esta no projeto Agendou. Implemente a tarefa <ID> do backlog.

Contexto obrigatorio:
- Leia mvp.md.
- Leia docs/sdd-rules.md.
- Leia a secao relevante de docs/backlog.md.
- Preserve trial gratis apenas no plano Premium/Top por 7 dias.
- Trial vencido bloqueia novas reservas, mas preserva configuracao.
- Pagamento confirmado reativa sem recriar pagina.

Escopo:
- <descrever exatamente o que deve mudar>

Fora de escopo:
- <listar o que nao deve ser alterado>

Criterios de aceite:
- <criterios objetivos>

Validacao esperada:
- <comandos de teste/lint/build ou verificacao manual>

Antes de editar, identifique o caminho de codigo controlador e a verificacao mais barata que pode falhar.
Depois de editar, execute a validacao focada e mostre evidencias.
```

## 3. Sequencia do primeiro dia

1. Criar repositorio com estrutura definida em `docs/architecture.md`.
2. Adicionar ADRs iniciais em `docs/adr/`:
   - stack e monolito modular;
   - PIX manual;
   - trial Premium/Top de 7 dias;
   - bloqueio com preservacao de dados;
   - sem PSP no MVP.
3. Inicializar API Spring Boot com Java 21 e Maven Wrapper.
4. Inicializar Next.js com TypeScript, Tailwind e ESLint.
5. Criar Compose local com PostgreSQL, Mailpit e S3 compativel.
6. Criar primeira migration com roles e extensoes necessarias.
7. Criar primeiro teste de isolamento entre dois tenants antes de CRUD.

## 4. Sequencia recomendada por fase

### F1 - Fundacao

Promptar a IA para criar primeiro entidades, migration e teste. Depois endpoints. Depois UI minima.

Checks minimos:
- `mvn test`
- teste de RLS com role real
- login/logout manual em ambiente local

### F2 - Trial e billing

Implementar modelo e regras antes da tela.

Checks minimos:
- trial criado somente para Premium/Top;
- expiracao por relogio injetavel;
- tenant bloqueado nao cria reserva;
- reativacao preserva dados.

### F3 a F6 - Produto principal

Implementar sempre em fatias verticais: migration, dominio, API, UI, teste e evidencia.

Ordem recomendada:
1. Perfil.
2. Servicos.
3. PIX.
4. Disponibilidade.
5. Reserva.
6. Comprovante.
7. Conferencia.
8. Operacao diaria.

### F7 e F8 - Homologacao e piloto

Pedir para a IA gerar runbooks, mas validar manualmente cada comando em ambiente real.

## 5. Checklist de revisao humana

- [ ] RLS usa role real da aplicacao.
- [ ] Nenhuma rota usa `tenant_id` do body como fonte de verdade.
- [ ] Bloqueio por trial e aplicado no backend, nao apenas na UI.
- [ ] Pagamento de assinatura reativa sem apagar configuracao.
- [ ] Upload de comprovante nao confirma reserva.
- [ ] Constraints do banco protegem concorrencia critica.
- [ ] Logs nao contem tokens, contatos completos ou comprovantes.
- [ ] Testes de concorrencia usam PostgreSQL real.

## 6. Formato de retorno do agente

```text
Tarefa: <ID>
Arquivos alterados:
- <arquivo>

Comportamento implementado:
- <resumo>

Validacao executada:
- <comando> -> <resultado>

Evidencias:
- <logs, testes, prints ou observacoes>

Riscos ou pendencias:
- <lista objetiva>
```
