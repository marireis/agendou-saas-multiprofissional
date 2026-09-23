# Agendou - UI, UX e design responsivo

## 1. Direcao visual

Agendou deve parecer um produto SaaS moderno, direto e confiavel para pequenos negocios de atendimento. A interface deve ser clara para uso diario, com densidade moderada, bons estados vazios e telas mobile-first.

## 2. Marca

- Nome: **agendou** em caixa baixa.
- Conceito: agenda + confirmacao + movimento fluido.
- Logo: `assets/agendou-logo.svg`.
- Tom: profissional, simples, humano e sem excesso de decoracao.

## 3. Paleta sugerida

| Token | Cor | Uso |
|---|---|---|
| `--color-ink` | `#17212B` | Texto principal. |
| `--color-muted` | `#5B6673` | Texto secundario. |
| `--color-bg` | `#F7F3EC` | Fundo quente e leve. |
| `--color-surface` | `#FFFFFF` | Paineis e formularios. |
| `--color-primary` | `#0E7C7B` | Acoes principais. |
| `--color-primary-dark` | `#075E5D` | Hover e estados ativos. |
| `--color-accent` | `#F2B84B` | Destaques e avisos amigaveis. |
| `--color-danger` | `#B42318` | Erros e bloqueios. |
| `--color-success` | `#177245` | Confirmacoes. |

## 4. Tipografia

- Interface: `Plus Jakarta Sans` ou `Aptos` se a fonte externa nao estiver disponivel.
- Numeros e valores: usar tabular numbers onde houver listas, calendario e dinheiro.
- Tamanhos compactos em paineis; sem hero exagerado dentro do app.

## 5. Telas obrigatorias

### Publicas

- Landing simples com foco em criar conta e entender o trial Premium/Top de 7 dias.
- Cadastro do profissional.
- Verificacao de email.
- Pagina publica `/a/{slug}`.
- Fluxo de reserva.
- Pagamento PIX e envio de comprovante.
- Consulta de reserva do cliente.

### Painel profissional

- Home com status de trial/assinatura, proximas reservas e pendencias.
- Onboarding guiado.
- Perfil e publicacao.
- Servicos.
- PIX e politica de pagamento.
- Disponibilidade e bloqueios.
- Agenda semanal desktop e lista diaria mobile.
- Reservas e detalhe.
- Conferencia de PIX.
- Clientes.
- Financeiro do profissional (recebimentos, saldos e devolucoes, independente da assinatura do SaaS).
- Compartilhar pagina publicada: copiar link e editar mensagem para abrir no WhatsApp, sem envio automatico.
- Assinatura e pagamento.

### Super admin

- Login com MFA.
- Tenants e status.
- Detalhe do tenant.
- Assinatura, bloqueio e reativacao.
- Auditoria operacional.

## 6. Trial e bloqueio na experiencia

- Durante o trial, mostrar contador discreto e acao clara para pagar.
- Nos ultimos 2 dias, elevar aviso para banner persistente.
- Trial vencido: bloquear operacao com tela objetiva, sem apagar nada.
- Mensagem recomendada: "Seu teste Premium terminou. Seus dados e pagina estao preservados. Para voltar a receber reservas, ative sua assinatura."
- Depois do pagamento, retornar ao painel no mesmo ponto de configuracao.

## 7. Componentes

- Botao primario, secundario e destrutivo.
- Campo de texto, dinheiro, percentual, data/hora e upload.
- Alertas de informacao, sucesso, aviso e erro.
- Badge de status de reserva e assinatura.
- Stepper de onboarding.
- Tabela responsiva com versao em cards no mobile.
- Calendario semanal desktop e agenda em lista no mobile.
- Modal de confirmacao para acoes destrutivas.
- Empty states acionaveis, sem prometer recursos fora do MVP.

## 8. Acessibilidade

- Navegacao por teclado em todos os fluxos.
- Foco visivel.
- Labels explicitos.
- Contraste adequado.
- Mensagens de erro associadas ao campo.
- Upload com alternativa textual.
- Calendario com lista equivalente no mobile.
