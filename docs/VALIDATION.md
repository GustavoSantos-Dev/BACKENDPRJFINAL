# Validação MySQL 8.0 — 07/10/2026

## Resultado

Projeto compilado e empacotado para Java 17 / Spring Boot 4.0.8 com Connector/J e flyway-mysql. JAR: target/production-counter-1.0.0.jar.

| Suíte | Banco/escopo | Aprovados | Falhas | Ignorados |
|---|---|---:|---:|---:|
| ApiIntegrationTest | HTTP real e H2 modo MySQL | 23 | 0 | 0 |
| PdfReportServiceTest | Conteúdo e paginação PDF | 1 | 0 | 0 |
| AnalyticsServiceTest | Limite diário e horário de verão | 2 | 0 | 0 |
| MySqlIntegrationTest | MySQL 8.0.43 real | 24 | 0 | 0 |
| Total nas execuções de validação | | 50 | 0 | 0 |

As suítes foram executadas em chamadas separadas: testes gerais, testes MySQL reais e testes específicos de horário. Após a formatação dos arquivos alterados, o projeto foi recompilado e empacotado com sucesso.

## Banco real

Foi utilizado mysqld 8.0.43 instalado neste Windows, em uma instância temporária separada, vinculada a 127.0.0.1:33307 e com data directory em .tools/mysql-qa. A instância foi encerrada normalmente após os testes. O servidor e os dados locais do usuário não foram alterados.

Foram executados Flyway com a migration MySQL real e Hibernate com ddl-auto=validate. Passaram autenticação, permissões, estados dos lotes, eventos, reenvios, duas suítes com 24 requisições simultâneas, disputa entre finalização e eventos, analytics diário, agrupamento por hora e download do PDF.

A migration usa InnoDB, FKs explícitas, AUTO_INCREMENT, DATETIME(6) UTC, UUID em BINARY(16) e colunas geradas com UNIQUE para lote/pausa abertos. Não depende de índices parciais PostgreSQL.

## Repetir em MySQL local

Crie um banco dedicado production_test e dê permissão ao usuário de testes. Os testes apagam os registros somente desse banco; a URL é validada para recusar o banco de operação.

```sql
CREATE DATABASE production_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
GRANT ALL PRIVILEGES ON production_test.* TO 'production'@'localhost';
```

```powershell
$env:MYSQL_TEST_URL = 'jdbc:mysql://localhost:3306/production_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:MYSQL_TEST_USER = 'production'
$env:MYSQL_TEST_PASSWORD = 'SENHA_DO_USUARIO_MYSQL'
.\mvnw.cmd test
```

Sem essas variáveis, os 24 testes MySQL ficam ignorados; os outros 26 executam com H2 e testes unitários.

## PDF e firmware

O teste de layout gera seis páginas fictícias com nomes longos e dados de 45 lotes. Primeira e última páginas foram renderizadas e inspecionadas. O endpoint de relatório também passou com MySQL real.

O firmware não precisou de alterações no contrato HTTP e permanece compilado com PlatformIO: RAM 47.804 bytes e firmware 987.721 bytes. Não houve teste físico no ESP32 ou sensor.

O script de inicialização Windows carrega .env e inicia Java diretamente. O simulador Python usa fila SQLite; sua sintaxe foi validada. Credenciais reais do usuário ainda devem ser configuradas no .env antes da primeira execução local.
