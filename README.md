# Contador industrial de parafusos

Back-end Java 17 / Spring Boot 4.0.8 para operadores, analistas e administradores, com MySQL 8.0, JWT, integração ESP32 e relatórios PDF. Implementa os requisitos de `Prompt CODEX.pdf`.

## 1. Executar no Windows com servidor local

Instale JDK 17 ou superior e MySQL 8.0 local (o MySQL Workbench pode ser usado para administrar o banco). Não é necessário instalar Maven: o projeto inclui Maven Wrapper.

No MySQL Workbench, conectado como administrador do MySQL, execute:

```sql
CREATE DATABASE production CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE USER 'production'@'localhost' IDENTIFIED BY 'SUBSTITUA_POR_UMA_SENHA_FORTE';
GRANT ALL PRIVILEGES ON production.* TO 'production'@'localhost';
```

Use MySQL 8.0.16 ou superior para que as constraints CHECK sejam aplicadas. A instalação encontrada neste Windows é MySQL 8.0.43.

Não é necessário criar as tabelas manualmente: Flyway cria o esquema na primeira inicialização. Se o usuário/banco já existe, aproveite-o e configure DB_USER/DB_URL de acordo.

No PowerShell, dentro da pasta do projeto:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Preencha DB_PASSWORD com a senha do usuário MySQL 8.0 e BOOTSTRAP_ADMIN_PASSWORD com a senha inicial do administrador da aplicação. São credenciais diferentes. Para gerar JWT_SECRET no Windows PowerShell:

```powershell
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

Copie o valor gerado para JWT_SECRET no .env. Em seguida:

```powershell
.\iniciar-local.cmd
```

O script carrega o .env, valida a configuração e inicia o Spring Boot diretamente no Windows. O MySQL 8.0 deve estar em execução em localhost:3306. As variáveis já definidas no PowerShell têm prioridade sobre o .env. O arquivo de configuração é ignorado pelo Git.

API: `http://localhost:8080`. Swagger: `http://localhost:8080/swagger-ui.html`. OpenAPI: `/v3/api-docs`. Para parar a aplicação, use Ctrl+C no terminal.

O administrador inicial é criado somente quando não há usuários. Após o primeiro cadastro, deixe BOOTSTRAP_ADMIN_EMAIL e BOOTSTRAP_ADMIN_PASSWORD vazios no .env.

## 2. Executar como JAR e conectar o ESP32

```powershell
.\iniciar-local.cmd -Package
```

Esse comando compila o projeto e inicia o JAR com a configuração local. SERVER_PORT altera a porta e CORS_ORIGINS recebe origens separadas por vírgula.

O servidor web já vem embutido no Spring Boot; não é necessário instalar Tomcat separado. Para o ESP32 na mesma rede, configure API_BASE com o IPv4 do computador, por exemplo `http://192.168.1.100:8080`. Consulte `ipconfig`. Na placa, localhost aponta para a própria placa. Se o Firewall bloquear o acesso, autorize a porta da API apenas na rede privada utilizada no projeto.

## 3. Arquitetura e arquivos

```text
pom.xml                     Dependências e build Java 17
mvnw / mvnw.cmd             Maven Wrapper
iniciar-local.cmd           Inicialização local no Windows
scripts/run-local.ps1       Carregamento e validação do .env
src/main/java/com/empresa/sistema/
  SistemaApplication.java  Inicialização Spring Boot
  config/                  Segurança, CORS, Swagger e primeiro administrador
  controller/              Endpoints HTTP; validação e autorização por perfil
  dto/                     Requests e Responses; entidades nunca são expostas
  entity/                  Usuários, dispositivos, tipos, lotes, eventos, pausas
  repository/              JPA e consultas com bloqueio pessimista
  security/                Tokens JWT e resolução do usuário autenticado
  service/                 Regras, transações, analytics SQL e PDF
  exception/               Erros JSON padronizados
src/main/resources/
  application.yml          Configuração por variáveis de ambiente
  db/migration/            Evolução do esquema por Flyway
src/test/                  Testes HTTP, concorrência, MySQL 8.0 e PDF
firmware/                  Projeto PlatformIO para ESP32
scripts/                   Simulador de dispositivo com fila persistente
examples/                  Requisições HTTP completas
docs/                     Contrato e decisões técnicas
```

Para listar cada arquivo: `Get-ChildItem src,firmware,scripts,docs,examples -Recurse -File`.

## 4. Fluxo de utilização

1. Faça login com o administrador inicial.
2. Cadastre operadores e analistas; cadastre os tipos de parafuso e dispositivos.
3. Guarde a API Key retornada no cadastro do dispositivo: ela só aparece na criação/rotação.
4. Operador ou administrador cria um lote com operação, meta, tipo e dispositivo.
5. Inicie o lote. A mesma máquina só pode ter um lote aberto, inclusive quando pausado.
6. ESP32 gera um UUID por peça, grava o evento em flash e envia à API.
7. A API autentica o dispositivo, bloqueia dispositivo e lote, verifica estado, horário e duplicidade, grava evento e atualiza contador na mesma transação.
8. A resposta só chega após confirmação da transação. Retransmissões não incrementam novamente.
9. Front-end consulta `GET /api/batches/{id}` a cada segundo para atualizar contagem, estado e progresso; consulta histórico com paginação.
10. Pause/retome conforme necessário. Antes de finalizar, pare o alimentador e sincronize toda a fila do ESP32. Finalizar impede novos eventos.
11. Analista/administrador desbloqueia Analytics com uma senha individual; usa o token elevado por até 10 minutos para métricas e PDF.

A meta não encerra o lote automaticamente: o contador pode ultrapassar 100%, preservando a produção física. `DELETE` de usuário, tipo e dispositivo desativa o cadastro e preserva históricos.

## 5. Segurança

JWT HS256 tem validade de uma hora e emissor verificado. A API consulta o usuário em cada autenticação, de modo que inativação e alteração de perfil valem também para tokens existentes. Senhas usam BCrypt custo 12; têm 12 a 72 caracteres e no máximo 72 bytes UTF-8. O login não revela se um e-mail existe.

Analytics exige perfil ANALISTA/ADMIN e reautenticação. Uma senha adicional individual pode ser definida em `analyticsPassword`; quando não configurada, a própria senha do usuário deve ser reapresentada. Não há uma senha compartilhada. Isso atende à confirmação adicional por senha; não constitui segundo fator de autenticação.

ESP32 usa uma chave aleatória de 256 bits enviada em `X-Device-Key`, validada contra SHA-256 armazenado. SHA-256 aqui protege uma chave de alta entropia gerada pelo servidor; senhas humanas usam BCrypt. O ESP32 não recebe um token de usuário.

Em produção, publique via HTTPS, configure CORS para a origem do front-end e limite tentativas de login no proxy. O firmware valida o certificado; não usa `setInsecure()`. O protótipo não inclui MFA nem refresh tokens. Tokens de usuários permanecem válidos após troca de senha até expirar, salvo inativação; para revogação imediata de todos os tokens, rotacione JWT_SECRET.

## 6. Banco e consistência

Veja [modelo e regras](docs/ARCHITECTURE.md). Flyway cria e versiona o esquema; Hibernate somente valida em execução normal. Nunca use `ddl-auto=update` para evoluir o banco.

`ProductionEvent.eventId` é UUID e chave primária global. Cada peça tem um registro individual. `UNIQUE(batch_id,count_after)` impede duas peças com a mesma posição no lote. O contador é atualizado junto com o evento. Bloqueios seguem sempre dispositivo -> lote, incluindo pausa/finalização; dois eventos simultâneos não perdem incrementos. Nenhum recurso é carregado antes do bloqueio do lote durante alterações, para evitar sobrescrever dados antigos.

## 7. Analytics e relatórios

Métricas usam horário de detecção, não de recebimento: eventos sincronizados depois entram no dia/hora em que ocorreram. O fuso é `America/Sao_Paulo`; o MySQL armazena valores UTC em DATETIME(6). A conexão JDBC e o Hibernate usam UTC. Períodos incluem `from` e `to`, com limite exclusivo no início do dia seguinte. Horas sem eventos equivalem a zero e podem ser preenchidas no gráfico pelo front-end.

`daily`/`summary`: contagem de eventos e de lotes com término no período. `hourly`: agrupamento das horas UTC no MySQL e conversão para São Paulo em Java. Não requer carregar tabelas de fusos no servidor MySQL. `batches`: lotes cujo intervalo cruza o período, com totais de cada lote e paginação. `current-batch` retorna uma lista porque pode haver várias máquinas.

PDFBox 3 gera relatórios paginados sem depender de navegador. Inclui período, totais, metas, cumprimento, horários, lotes e produção por hora. Os totais do lote podem incluir peças fora do período; o PDF identifica essa diferença. Usa uma fotografia transacional consistente com isolamento REPEATABLE_READ. Os textos do PDF são transliterados para a fonte Helvetica padrão; o restante da API mantém UTF-8.

## 8. ESP32 e perda de conexão

Veja [integração e limites físicos](firmware/README.md). A fila local persiste o payload e UUID antes do envio; a confirmação do servidor precede a exclusão. Uma resposta perdida provoca reenvio do mesmo evento e uma resposta `duplicate=true`.

Eventos anteriores à pausa podem ser enviados após retomada. Novos eventos enquanto o lote está pausado são recusados; eventos detectados no intervalo de uma pausa também são recusados depois da retomada. Lotes finalizados não aceitam backlog novo. Isso torna necessário esvaziar a fila antes de finalizar; eventos permanentemente rejeitados são preservados para conciliação, nunca contados silenciosamente.

## 9. API e exemplos

[Tabela de endpoints e payloads](docs/API.md), [requisições HTTP](examples/api.http) e [simulador](scripts/esp32_simulator.py). Swagger gera os schemas de request/response a partir dos DTOs.

## 10. Testes

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

A suíte H2 usa servidor HTTP real e testa login, validação, papéis, criação/início/pausa/retomada/finalização, eventos inválidos/duplicados e 24 requisições simultâneas. O teste PDF verifica conteúdo, acentos transliterados e múltiplas páginas.

`MySqlIntegrationTest` usa MySQL 8.0 local, migrations reais, analytics por hora e endpoint PDF. Crie um banco separado chamado production_test, com permissão para o usuário production. Os testes limpam apenas esse banco dedicado; nunca utilize o banco da aplicação.

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

Sem essas três variáveis, a suíte específica de MySQL 8.0 fica ignorada. Os 26 testes HTTP/H2, PDF e conversão de horários continuam executando normalmente. Não há dependência de Docker ou Testcontainers nos testes locais.

O projeto usa Spring Boot 4.0.8, uma versão publicada compatível com Java 17, em substituição à referência 5.4 do documento. Lombok foi dispensado para evitar processamento adicional de anotações e facilitar o estudo dos arquivos.

### Exemplo de simulação

```powershell
$env:DEVICE_API_KEY = 'CHAVE_RETORNADA_NO_CADASTRO'
python scripts/esp32_simulator.py --device-id ESP32-001 --operation-id OP-2026-001 --count 100 --rate 5
# Para reenviar somente a fila existente após uma interrupção:
python scripts/esp32_simulator.py --device-id ESP32-001 --operation-id OP-2026-001 --count 0
```

O lote deve estar iniciado e o relógio do computador correto. O simulador persiste cada evento antes do envio e preserva itens sem ACK.

### Exemplo cURL

```text
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@example.com\",\"password\":\"SUA_SENHA\"}"
curl http://localhost:8080/api/batches/1 -H "Authorization: Bearer SEU_TOKEN"
curl "http://localhost:8080/api/analytics/report/pdf?from=2026-10-07&to=2026-10-07" -H "Authorization: Bearer TOKEN_ELEVADO" -o report.pdf
```

No PowerShell, prefira `curl.exe` para evitar aliases e adapte a citação JSON ao shell; `examples/api.http` elimina essa diferença entre shells.

Veja [o resultado da validação e suas limitações](docs/VALIDATION.md).

Referência da versão escolhida: https://docs.spring.io/spring-boot/4.0/system-requirements.html (Java 17 ou superior).

A URL padrão usa MySQL na porta 3306:

```text
DB_URL=jdbc:mysql://localhost:3306/production?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

Se já criou .env na etapa anterior, atualize DB_URL, DB_USER e DB_PASSWORD nesse arquivo; o modelo .env.example não sobrescreve configurações existentes. O projeto cria suas tabelas em um banco MySQL novo; esta adaptação não transfere automaticamente dados de um banco PostgreSQL anterior.

## Executar pela IDE

Execute a classe com.empresa.sistema.SistemaApplication com o diretório de trabalho na raiz do projeto. O application.yml importa automaticamente o .env dessa pasta como arquivo de propriedades. Não é necessário iniciar pelo .cmd para carregar as credenciais. Variáveis já definidas na configuração de execução da IDE continuam tendo prioridade; remova valores antigos de DB_PASSWORD/JWT_SECRET dessa configuração, se houver.

Use uma única instância na porta 8080: interrompa a execução anterior antes de iniciar outra pela IDE.
