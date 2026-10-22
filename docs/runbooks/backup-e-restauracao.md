# Ensaio de backup e restauração

O teste `BackupRestoreIT` usa exclusivamente dados sintéticos e quatro contêineres temporários (dois PostgreSQL 17 e dois MinIO). Não usa os volumes, portas fixas, credenciais ou dados do ambiente da aplicação. Os contêineres são fechados ao terminar; o JUnit remove os arquivos temporários.

## Executar

Com Docker Desktop ativo e a imagem local `agendou-minio-local:7aac2a2` disponível, na pasta `apps/api`:

```powershell
$env:JAVA_HOME='C:\Users\maryn\.jdks\corretto-21.0.5'
.\mvnw.cmd '-Dit.test=BackupRestoreIT' verify
```

Esse comando também executa os testes unitários. Resultado do ensaio: `apps/api/target/backup-restore-report.txt`; resultado JUnit: `apps/api/target/failsafe-reports`. Um relatório antigo não comprova a execução atual: conferir data, teste e BUILD SUCCESS.

## O que é verificado

- Todas as migrations aplicadas na origem, dump custom do PostgreSQL, restauração em banco vazio com `pg_restore --exit-on-error`, preservando proprietários e permissões.
- Role runtime recriada antes da restauração, pois `pg_dump` não inclui roles globais. A senha usada é exclusiva do ensaio.
- Dois tenants e um cliente sintético restaurados; validação Flyway de checksums, políticas RLS, FORCE RLS e funções SECURITY DEFINER preservadas em quantidade.
- Conexão runtime sem tenant não vê tenants; com tenant vê somente seus dados; outro tenant não vê o cliente restaurado.
- PNG sintético copiado do armazenamento para backup e restaurado em outro MinIO com chave e tipo conhecidos. SHA-256 do dump e arquivo conferidos; conteúdo restaurado conferido e acesso anônimo negado.
- Uma gravação posterior ao backup fica de fora, demonstrando o limite da recuperação por snapshot.

O tempo medido de restauração inclui inicialização dos destinos e verificações, mas não o reinício da API ou frontend. A idade do snapshot no incidente simulado é um indicador do intervalo de perda nesta execução, não um RPO contratado. O teste pequeno não determina RTO/RPO de produção. O manifesto é sintético; ainda não há ferramenta operacional para enumerar/restaurar todos os comprovantes.

## Antes de usar em produção

1. Definir RPO/RTO, retenção, responsáveis, armazenamento externo criptografado e alertas. Testar com volume representativo e falha realista.
2. Criar uma janela consistente: suspender gravações, uploads, exclusões e workers enquanto captura banco e arquivos, ou implementar outra estratégia de consistência comprovada. O ensaio não possui escritas concorrentes durante o backup.
3. Guardar dump, inventário de objetos (chave, tamanho, tipo e SHA-256), roles/configuração e versão/digest da aplicação. Não usar `--no-owner` para contornar erros de restauração. Provisionar roles sem superuser/BYPASSRLS para runtime.
4. Manter segredos fora do dump/repositório, em cofre separado. A chave MFA local protegida por DPAPI depende do usuário/máquina Windows; copiar apenas esse arquivo não assegura recuperação em outro host. Um procedimento de custódia/recuperação da chave ainda precisa ser implantado e testado.
5. Restaurar primeiro em destino vazio isolado, com bucket privado, emails e workers bloqueados. Validar checksums, contagens, vínculos de cada comprovante com pagamento/reserva, RLS e autenticação/MFA antes de liberar tráfego.
6. Realizar uma jornada completa na API restaurada, inclusive recibo/código, comprovante e conferência. Registrar tempo total até serviço utilizável e ponto efetivamente recuperado. Somente depois planejar troca de tráfego e rollback.

**Pendente:** backup automatizado operacional, inventário em escala real, recuperação de segredos, cópia externa, carga representativa e recuperação de produção. O vínculo reserva/comprovante e a jornada da API restaurada são cobertos pelo ensaio sintético da segunda etapa abaixo. MVP-073 permanece parcial.


## Segunda etapa: reserva e comprovante na API restaurada

`BookingRecoveryIT` complementa o ensaio de infraestrutura. Cria dois profissionais sintéticos, publica uma página, reserva sem email e envia PNG por HTTP para MinIO real. Encerra a aplicação de origem antes do snapshot. O inventário JSON vem de `payment_evidence`, com chave, tipo, tamanho e SHA-256, e é relido do disco para restauração. Testa detecção de ausência/corrupção antes de aceitar o arquivo.

Após `pg_restore`, inicia outra aplicação apontando para banco e MinIO restaurados. Um cliente HTTP novo obtém CSRF e sessão, tem código incorreto recusado, autentica com o código original, consulta a reserva e baixa o comprovante vinculado. Download anônimo e consulta por outro profissional são recusados. O registro de recebimento pelo profissional confirma a reserva e compromete a quota, com consulta posterior do pagamento por HTTP. As ações administrativas usam MockMvc e identidade sintética; não se trata de teste de interface nem transferência real.

Executar na pasta `apps/api`: `.\mvnw.cmd '-Dit.test=BookingRecoveryIT' verify`. O relatório `target/booking-recovery-report.txt` mede backup e recuperação incluindo inicialização da aplicação e verificações. Credenciais, nomes e contatos são sintéticos; os volumes locais continuam intocados. Não comprova restauração de segredos/MFA ou RPO/RTO de produção.
