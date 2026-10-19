# Contrato REST

Todas as rotas usam JSON, exceto PDF. Usuários enviam `Authorization: Bearer <JWT>`. Dispositivos enviam `X-Device-Key: <chave>` e, para consultar estado, `X-Device-Id`. Datas são ISO-8601; timestamps de eventos devem incluir UTC (`Z`) ou offset.

| Método | Endpoint | Objetivo | Acesso | Request | Response |
|---|---|---|---|---|---|
| POST | /api/auth/login | Login | Público | Login | Token, 200 |
| GET | /api/auth/me | Perfil | Autenticado | - | UserView, 200 |
| POST | /api/auth/analytics/unlock | Confirmar senha | ANALISTA/ADMIN | AnalyticsUnlock | Token elevado, 200 |
| GET | /api/users | Listar usuários | ADMIN | page,size,sort | Page<UserView> |
| GET | /api/users/{id} | Consultar usuário | ADMIN | id | UserView |
| POST | /api/users | Cadastrar | ADMIN | UserCreate | UserView, 201 |
| PUT | /api/users/{id} | Atualizar | ADMIN | UserUpdate | UserView |
| DELETE | /api/users/{id} | Desativar | ADMIN | id | 204 |
| GET | /api/screws | Listar tipos | Autenticado | page,size,sort | Page<ScrewView> |
| GET | /api/screws/{id} | Consultar tipo | Autenticado | id | ScrewView |
| POST | /api/screws | Cadastrar tipo | ADMIN | Screw | ScrewView, 201 |
| PUT | /api/screws/{id} | Atualizar tipo | ADMIN | Screw | ScrewView |
| DELETE | /api/screws/{id} | Desativar tipo | ADMIN | id | 204 |
| GET | /api/devices | Listar dispositivos | ADMIN | - | DeviceView[] |
| POST | /api/devices | Cadastrar e gerar chave | ADMIN | DeviceCreate | DeviceKey, 201 |
| POST | /api/devices/{id}/rotate-key | Substituir chave | ADMIN | id | DeviceKey |
| DELETE | /api/devices/{id} | Desativar dispositivo | ADMIN | id | 204 |
| POST | /api/batches | Configurar lote | OPERADOR/ADMIN | BatchCreate | BatchView, 201 |
| GET | /api/batches | Histórico paginado | Autenticado | page,size,sort | Page<BatchView> |
| GET | /api/batches/{id} | Estado/contador | Autenticado | id | BatchView |
| GET | /api/batches/{id}/events | Histórico de peças | Autenticado | page,size | Page<EventView> |
| GET | /api/batches/{id}/pauses | Histórico de pausas | Autenticado | id | PauseView[] |
| POST | /api/batches/{id}/start | Iniciar | Responsável/ADMIN | id | BatchView |
| POST | /api/batches/{id}/pause | Pausar | Responsável/ADMIN | id | BatchView |
| POST | /api/batches/{id}/resume | Retomar | Responsável/ADMIN | id | BatchView |
| POST | /api/batches/{id}/finish | Encerrar | Responsável/ADMIN | id | BatchView |
| POST | /api/production/events | Receber uma peça | Dispositivo associado | Event | EventAccepted |
| GET | /api/production/device-state | Consultar lote/estado | Dispositivo | Headers | BatchView[] |
| GET | /api/analytics/daily | Total diário | Token elevado | date | Totais |
| GET | /api/analytics/summary | Totais no período | Token elevado | from,to | Totais |
| GET | /api/analytics/hourly | Produção por hora | Token elevado | from,to | hour,pieces[] |
| GET | /api/analytics/batches | Lotes no período | Token elevado | from,to,page,size | Dados dos lotes[] |
| GET | /api/analytics/current-batch | Lotes abertos | Token elevado | - | BatchView[] |
| GET | /api/analytics/report/pdf | Relatório | Token elevado | from,to | application/pdf |

`page` começa em zero. Listagens JPA limitam `size` a 200. Analytics também limita a 200; períodos têm no máximo 367 dias. As rotas de transição exigem perfil OPERADOR/ADMIN além da associação ao lote.

## Payloads

```json
{"email":"admin@example.com","password":"senha-do-usuario"}
```

UserCreate (analyticsPassword opcional):

```json
{"name":"Operador","email":"operator@example.com","password":"senha-de-12-ou-mais-caracteres","role":"OPERADOR"}
```

UserUpdate usa os mesmos campos de nome/e-mail/perfil e `active`. `password` e `analyticsPassword` são opcionais e, quando ausentes, mantêm os hashes existentes.

Screw (photoUrl e specifications opcionais):

```json
{"name":"M6","description":"Parafuso sextavado","photoUrl":"https://example.com/m6.jpg","specifications":"Aço; comprimento 20 mm"}
```

DeviceCreate:

```json
{"deviceId":"ESP32-001","name":"Contadora 1"}
```

BatchCreate:

```json
{"operationId":"OP-2026-001","target":10000,"screwTypeId":1,"deviceId":"ESP32-001"}
```

Event (uma requisição equivale a uma peça; não há campo de quantidade arbitrária):

```json
{"deviceId":"ESP32-001","operationId":"OP-2026-001","eventId":"11111111-1111-4111-8111-111111111111","timestamp":"2026-10-07T12:30:15Z"}
```

Resposta inicial e reenvio:

```json
{"accepted":true,"duplicate":false,"currentCount":1,"eventId":"11111111-1111-4111-8111-111111111111"}
```

```json
{"accepted":true,"duplicate":true,"currentCount":1,"eventId":"11111111-1111-4111-8111-111111111111"}
```

AnalyticsUnlock:

```json
{"password":"senha-individual-do-analytics-ou-reauth"}
```

Token:

```json
{"accessToken":"jwt","tokenType":"Bearer","expiresIn":3600}
```

Erro:

```json
{"timestamp":"2026-10-07T12:30:15Z","status":409,"code":"BATCH_PAUSADO","message":"Lote não está em execução","path":"/api/production/events","details":{}}
```

## Validações

E-mails válidos, nomes obrigatórios, senha mínima de 12 caracteres na criação, máximo BCrypt de 72 bytes, meta entre 1 e 1 bilhão, referências existentes/ativas, operação e deviceId com até 64 caracteres alfanuméricos/hífen/underscore, eventId UUID, foto em URL HTTP(S), timestamp com limite futuro e verificação de pausas. No login, a senha é limitada a 72 caracteres; o verificador também respeita o limite de bytes.

## Política de reenvio

- 200 accepted=true com eventId correspondente: remover da fila, inclusive duplicate=true.
- Falha de rede, timeout, 5xx ou RETRY_TRANSACTION: repetir o payload original com backoff.
- BATCH_PAUSADO: manter fila e tentar após retomada.
- BATCH_FINALIZADO, EVENT_DURING_PAUSE, INVALID_EVENT_TIME, EVENT_ID_REUSED: preservar para conciliação, interromper alimentação e solicitar análise.
- 400, 401 ou 403: corrigir payload/credencial/associação; não descartar eventos automaticamente.

Nunca gere um novo UUID para tentar novamente a mesma peça. A resposta `accepted=true` informa persistência no banco, não apenas recepção na memória da aplicação.
