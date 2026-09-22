# 08 - Riscos e pendencias

## Riscos

| Risco | Impacto | Tratamento recomendado |
|---|---|---|
| SDD original nao esta no workspace | Rastreabilidade parcial | Trazer `SDD_SaaS_Agendamento.md` para revisao futura. |
| Trial/billing implementado tardiamente | Retrabalho e brechas de acesso | Manter F2 cedo no backlog. |
| Bloqueio apenas na UI | Usuario contorna via API | Aplicar bloqueio no backend e testar. |
| RLS testada como owner | Falso positivo de isolamento | Testar com role real da aplicacao. |
| PIX manual sem auditoria | Risco financeiro e operacional | Registrar referencia, valor, conta, data e operador. |
| Sem restore testado | Risco operacional no piloto | Executar restore antes de aceitar reservas reais. |

## Pendencias

- Confirmar valores comerciais finais dos planos.
- Confirmar quando o trial inicia: criacao do tenant ou primeira publicacao.
- Confirmar politica de carencia para plano pago vencido, distinta do trial.
- Confirmar provedor e processo de pagamento da assinatura no MVP.
- Validar disponibilidade de versoes exatas antes de lockfile.
