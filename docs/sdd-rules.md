# Agendou - Regras SDD e decisoes obrigatorias

## 1. Invariantes do produto

- Cada profissional pertence a um tenant.
- Toda tabela de negocio possui `tenant_id` obrigatorio.
- A aplicacao nunca confia em `tenant_id` enviado no corpo da requisicao.
- Isolamento usa membership, RLS, FKs compostas e contexto local de transacao.
- Cliente final usa contato verificado, sem senha permanente no MVP.
- Upload de comprovante nunca confirma pagamento.
- Reserva confirmada exige entrada PIX validada manualmente e suficiente.
- Pagamento tardio nao ressuscita reserva expirada nem ocupa horario de terceiro.
- Email falho nao desfaz reserva confirmada nem pagamento validado.
- Arquivos privados nao ficam no disco do container.

## 2. Decisao comercial: trial Premium/Top

- Trial gratis existe somente no plano Premium/Top.
- O trial dura 7 dias corridos.
- Basico e Intermediario nao possuem trial gratis.
- Ao final dos 7 dias, se nao houver pagamento confirmado, o tenant e bloqueado.
- Bloqueio impede novas reservas e administracao operacional ativa.
- Bloqueio preserva pagina, servicos, disponibilidade, clientes, reservas, pagamentos, arquivos e auditoria.
- Pagamento confirmado reativa o tenant sem recriar pagina ou perder configuracao.
- Novo trial automatico para a mesma entidade e proibido sem override administrativo auditado.

## 3. Reserva e pagamento do cliente

- Preco, entrada e saldo sao calculados no servidor.
- Entrada minima e 50% e maxima 100%.
- Calculo em centavos usa arredondamento para cima: `(preco * percentual + 99) / 100`.
- Reserva temporaria tem prazo de pagamento configurado, inicialmente 30 minutos.
- Recebimento de comprovante muda para `EmConferencia`, nao para confirmado.
- Confirmacao exige conferencia fora do sistema e registro manual dentro do sistema.
- Referencia bancaria unica nao pode contabilizar duas reservas.
- Devolucao manual e registrada, mas o sistema nao afirma executar transferencia bancaria.

## 4. Agenda e disponibilidade

- Datas persistidas em UTC.
- Regras semanais usam fuso IANA do estabelecimento.
- Horizonte inicial de agenda: 60 dias.
- Antecedencia padrao: 24 horas.
- Grade padrao: 15 minutos.
- Bloqueios e pausas prevalecem sobre regra semanal.
- `CalendarAllocation` usa intervalo semiaberto.
- Constraint de exclusao impede sobreposicao ativa por tenant/recurso.
- Expiracao altera `active`, sem predicado com `now()` na constraint.

## 5. Assinatura e bloqueio

- Estados de assinatura devem ser independentes dos estados de reserva.
- Trial vencido bloqueia novas reservas publicas.
- Tenant bloqueado pode manter consultas essenciais e caminho de pagamento.
- Reservas confirmadas antes do bloqueio permanecem preservadas.
- Super admin pode suspender tenant por motivo operacional, com auditoria.
- Reativacao exige pagamento ou decisao administrativa explicita.

## 6. Privacidade e seguranca

- Super admin usa MFA.
- Nao ha impersonacao no MVP.
- Acesso excepcional a dados pessoais deve ser temporario, justificado e auditado.
- Logs nao registram token, contato sensivel completo, chave PIX privada ou comprovante.
- Exportacao de dados e executada sob solicitacao verificada e area privada temporaria.
- Dados reais nao entram em fixtures, testes locais ou screenshots publicas.

## 7. Regras de evolucao

- Nao criar telas vazias prometendo recursos futuros.
- Nao fingir integracao com PSP, PIX API, WhatsApp ou SMS.
- Nao adicionar microservicos, Kafka, Kubernetes ou cache distribuido sem gargalo medido.
- Nao otimizar com cache antes de corrigir query, indice, lock ou transacao.
- Toda mudanca que alterar regra SDD exige ADR.
