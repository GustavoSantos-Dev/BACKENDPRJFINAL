#pragma once
#define WIFI_SSID "CONFIGURE_WIFI"
#define WIFI_PASSWORD "CONFIGURE_PASSWORD"
#define API_BASE "http://192.168.1.100:8080"
#define DEVICE_ID "ESP32-001"
#define DEVICE_API_KEY "CONFIGURE_KEY_RETURNED_BY_API"
#define OPERATION_ID "OP-2026-001"
// Para HTTPS, configure o certificado da autoridade que assina o servidor.
static const char SERVER_CA_PEM[] = "";
// Entrada do sensor e saída de habilitação para o controlador da máquina.
#define SENSOR_PIN 27
#define MACHINE_ENABLE_PIN 26
