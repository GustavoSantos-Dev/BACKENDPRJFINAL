# ESP32: sensor, persistência e sincronização

## 1. Compilar e configurar

Instale PlatformIO, copie `include/secrets.example.h` para `include/secrets.h` e configure Wi-Fi, URL da API, deviceId, API Key retornada no cadastro e número da operação. O arquivo local não é versionado. O sensor deve produzir pulsos digitais de 3,3 V no GPIO 27; GPIO 26 habilita um controlador externo, não alimenta diretamente motores. Ajuste pinos e debounce conforme o sensor.

```text
pio run
pio run --target upload
pio device monitor
```

O firmware exige LittleFS já inicializado. Para uma placa nova, crie uma pasta `data` vazia e use `pio run --target uploadfs` uma única vez, antes da operação. Isso apaga a fila existente: nunca faça uploadfs em uma máquina com eventos pendentes. `LittleFS.begin(false)` não formata automaticamente um dispositivo com erro de montagem.

## 2. Fluxo

O motor fica desabilitado até haver Wi-Fi inicial, relógio NTP sincronizado e lote configurado em execução. A interrupção do sensor põe o instante monotônico em uma fila FreeRTOS. Uma tarefa separada cria UUID v4 e payload, grava um arquivo temporário, faz flush e renomeia para a outbox. A tarefa de rede não bloqueia a captura do sensor.

Cada evento persistido é enviado em ordem de sequência. O arquivo só é removido após 200, `accepted=true` e UUID correspondente. Uma queda depois do commit do servidor e antes da remoção local provoca retransmissão idempotente. O relógio monotônico usa um offset inicial UTC, evitando que ajustes NTP alterem timestamps desta sessão.

Arquivos temporários completos são recuperados no boot. Arquivos incompletos ficam em `/rejected`, acionando uma falha. Eventos permanentemente recusados também ficam em `/rejected`. Examine e concilie esses arquivos antes de voltar a operar; o protótipo não os importa como contagens válidas.

## 3. Durante perda de rede

Após a sincronização inicial, a máquina mantém o último estado conhecido e continua persistindo eventos mesmo sem comunicação. São reenviados quando a rede volta, usando os UUIDs originais. Durante o offline, o ESP32 não recebe uma pausa feita remotamente; ao sincronizar, o servidor rejeita eventos detectados no intervalo dessa pausa. Para evitar produção física inválida, use parada local/intertravamento ou uma política de parar a máquina ao perder comunicação, conforme o processo industrial.

A fila possui capacidade limitada e gera falha ao aproximar-se do limite. Em erro de armazenamento ou overflow, o alimentador é desabilitado. Não é possível garantir perda zero durante corte de energia entre a detecção física e a gravação em flash, fila cheia, falha de flash ou peças que continuem por inércia depois da parada. Para produção: dimensionar buffer, garantir alimentação de retenção/UPS ou FRAM e validar tempo de parada, volume, desgaste da flash e ruído do sensor. Esses limites precisam ser medidos na máquina real.

## 4. Pausar, retomar e encerrar

A consulta de estado é feita aproximadamente a cada segundo, além do tempo de rede. Não é um comando de parada em tempo real garantido. A pausa local/motor deve ser ligada ao processo físico. Os eventos anteriores à pausa podem ser enviados depois da retomada; peças durante a pausa não entram na contagem.

Antes de encerrar um lote: interrompa alimentação, espere escoar as peças, confirme que a outbox está vazia e então finalize pela API. Não altere OPERATION_ID nem reprograme a placa com fila pendente sem uma conciliação. O evento persistido contém sua operação original.

## 5. HTTPS e credenciais

Para HTTPS, preencha SERVER_CA_PEM com a CA do servidor. A biblioteca valida certificado e relógio; não se ignora TLS. HTTP é disponibilizado apenas para desenvolvimento em rede local. Em produção, proteger credenciais da flash com recursos de secure boot/flash encryption e provisionamento individual.

A chave não é JWT: é um segredo aleatório por dispositivo. Se comprometida, rotacione pelo endpoint administrativo e atualize a placa. O firmware deve registrar erros sem imprimir a chave.

## 6. Simulador

`scripts/esp32_simulator.py` simula eventos e persistência SQLite em um computador, sem placa física. Serve para validar o back-end e cenários de queda de rede. O firmware deve ser testado no sensor real antes de uso industrial; compilação não comprova integridade elétrica ou taxa máxima de captura.
