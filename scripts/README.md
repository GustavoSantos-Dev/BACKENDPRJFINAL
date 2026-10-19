# Simulador

Python 3.10+; sem dependências externas. Configure DEVICE_API_KEY no ambiente, inicie o lote e execute:

```text
python scripts/esp32_simulator.py --device-id ESP32-001 --operation-id OP-2026-001 --count 100 --rate 5
```

Para drenar somente pendências, use `--count 0`. `--outbox` escolhe o arquivo SQLite. Eventos são persistidos individualmente antes da chamada de rede; reiniciar não troca UUIDs. Falhas temporárias mantêm a fila. Uma resposta permanentemente inválida interrompe o envio com código 2, preservando o payload para conciliação. Ctrl+C retorna 130 sem apagar a fila.

Não execute dois simuladores usando a mesma outbox simultaneamente. Não apague o SQLite ou arquivos WAL com eventos pendentes.
