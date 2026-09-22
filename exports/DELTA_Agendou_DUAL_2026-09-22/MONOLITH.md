# DELTA Agendou - Exportacao Monolitica DUAL

**DELTA:** DLT-AGENDOU-MVP-2026-09-22  
**Snapshot:** SNP-AGENDOU-DUAL-2026-09-22-001  
**Data:** 22/09/2026  
**Formato:** Monolith Markdown da exportacao DUAL  
**Obsidian:** nao incluido.

---

## 1. Identidade

O DELTA Agendou teve como objetivo transformar `Roadmap_MVP_Agendou.md` em um pacote executavel para construir um SaaS de agendamento com regras SDD, backlog, documentacao tecnica, design responsivo, logomarca SVG e passo a passo para desenvolvimento com IA.

Regra comercial critica confirmada pelo consumidor: teste gratis somente no plano Premium/Top por 7 dias. Se o usuario nao pagar, sera bloqueado. Se pagar depois, continuara com a pagina, configuracoes e historico ja criados.

---

## 2. Conversa disponivel

O consumidor pediu a criacao de `mvp.md` e todos os arquivos Markdown necessarios para construir a aplicacao, com stack moderna, design moderno, sistema responsivo, logomarca SVG para Agendou e roteiro passo a passo para desenvolvimento com IA.

Depois, solicitou: "xporte este DELTA completo no formato DUAL". A solicitacao foi interpretada como "Exporte este DELTA completo no formato DUAL".

---

## 3. Definicao

### Problema

O roadmap existente precisava virar um pacote executavel para construcao do SaaS, incorporando a nova politica de trial.

### Objetivo

Produzir artefatos de produto, arquitetura, backlog, regras, testes, UI/UX, marca e continuidade.

### Escopo

- `mvp.md` principal.
- Documentos auxiliares em `docs/`.
- Logo SVG em `assets/`.
- Exportacao DUAL.

### Fora de escopo

- Implementar codigo da aplicacao.
- Criar vault Obsidian.
- Integrar PSP ou pagamentos recorrentes automaticos.

---

## 4. Decisoes

| ID | Decisao | Estado |
|---|---|---|
| DEC-001 | Criar pacote documental a partir do roadmap. | CONFIRMED |
| DEC-002 | Trial gratis apenas no plano Premium/Top. | CONFIRMED |
| DEC-003 | Trial dura 7 dias. | CONFIRMED |
| DEC-004 | Usuario sem pagamento apos trial sera bloqueado. | CONFIRMED |
| DEC-005 | Pagamento posterior preserva pagina e configuracoes existentes. | CONFIRMED |
| DEC-006 | Exportar no formato DUAL. | CONFIRMED |
| DEC-007 | Manter Java 21, Spring Boot, PostgreSQL, Next.js, React, TypeScript e Tailwind. | PROPOSED a partir do roadmap |
| DEC-008 | Usar monolito modular com API e worker Java. | PROPOSED a partir do roadmap |
| DEC-009 | Manter PIX manual no MVP. | PROPOSED a partir do roadmap |

---

## 5. Artefatos

| Arquivo | Finalidade |
|---|---|
| `mvp.md` | Contrato principal do MVP. |
| `docs/backlog.md` | Backlog faseado. |
| `docs/sdd-rules.md` | Regras SDD e decisoes obrigatorias. |
| `docs/architecture.md` | Arquitetura e stack. |
| `docs/data-model.md` | Modelo de dados e estados. |
| `docs/api-contract.md` | Contrato inicial de API. |
| `docs/ui-ux-design.md` | UI responsiva e design moderno. |
| `docs/testing-and-acceptance.md` | Testes e gates. |
| `docs/ai-development-playbook.md` | Passo a passo com IA. |
| `docs/brand.md` | Marca e identidade visual. |
| `assets/agendou-logo.svg` | Logomarca SVG. |

---

## 6. Plano

1. F0 - Preparacao e contrato tecnico.
2. F1 - Fundacao, identidade e isolamento.
3. F2 - Trial Premium/Top, assinatura e bloqueio.
4. F3 - Perfil, marca, servicos e publicacao.
5. F4 - Disponibilidade e calendario.
6. F5 - Reserva publica, quota e consulta segura.
7. F6 - PIX manual e operacao diaria.
8. F7 - Homologacao, privacidade e operacao.
9. F8 - Producao e piloto.

---

## 7. Testes e evidencias

Validacoes executadas:

- Arquivos principais encontrados: PASS.
- Termos `Premium/Top`, `7 dias`, `TRIAL_EXPIRED_BLOCKED`, `reativacao` e `preservacao`: PASS.
- SVG carregado como XML: PASS.

Testes planejados para implementacao:

- Isolamento multi-tenant.
- Trial e bloqueio.
- Reativacao preservando configuracoes.
- Concorrencia de agenda.
- Validacao manual de PIX.
- Jornada mobile e acessibilidade.
- Restore.

---

## 8. Riscos e pendencias

Riscos principais:

- SDD original nao estava no workspace.
- Bloqueio por trial precisa estar no backend, nao apenas na UI.
- RLS deve ser testada com role real da aplicacao.
- PIX manual exige auditoria cuidadosa.

Pendencias:

- Confirmar valores comerciais finais.
- Confirmar quando o trial inicia.
- Confirmar politica de carencia para plano pago vencido.
- Confirmar processo de pagamento da assinatura no MVP.

---

## 9. Mensuracao do DELTA

Planejado: gerar pacote documental completo com ajuste de trial e logo.  
Realizado: `mvp.md`, docs auxiliares, SVG e exportacao DUAL.  
Evidenciado: arquivos presentes, termos centrais encontrados e SVG valido como XML.  
Limitacao: nao ha codigo de aplicacao para executar testes funcionais.

---

## 10. Prompt de retomada

```text
Retome o DELTA Agendou DLT-AGENDOU-MVP-2026-09-22.

Leia mvp.md, docs/sdd-rules.md, docs/backlog.md e docs/architecture.md.

Decisoes confirmadas:
- Trial gratis apenas no plano Premium/Top.
- Trial dura 7 dias.
- Trial vencido sem pagamento bloqueia o tenant.
- Pagamento posterior reativa mantendo pagina, configuracoes e historico.

Proximo passo recomendado:
- Iniciar execucao pelo docs/ai-development-playbook.md.
- Criar estrutura de repositorio, ADRs, wrappers, Compose, primeira migration e teste de isolamento multi-tenant.
```
