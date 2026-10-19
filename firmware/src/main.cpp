#include <Arduino.h>
#include <ArduinoJson.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <LittleFS.h>
#include <time.h>
#include <vector>
#include <esp_timer.h>
#include "secrets.h"

struct Pulse { int64_t monotonicMicros; };
QueueHandle_t pulses;
SemaphoreHandle_t storageMutex;
volatile bool overflowFault=false;
volatile bool machineRunning=false;
volatile bool fault=false;
int64_t epochOffset=0;
uint64_t nextSequence=1;
constexpr uint32_t MAX_QUEUE=1000;
constexpr uint32_t DEBOUNCE_US=2000;
volatile int64_t lastPulse=0;

void IRAM_ATTR onPulse() {
 int64_t now=esp_timer_get_time();
 if(now-lastPulse<DEBOUNCE_US) return;
 lastPulse=now;
 if(!machineRunning) return;
 Pulse p{now}; BaseType_t wake=pdFALSE;
 if(xQueueSendFromISR(pulses,&p,&wake)!=pdTRUE) overflowFault=true;
 if(wake) portYIELD_FROM_ISR();
}
String uuid() {
 uint8_t b[16]; for(int i=0;i<16;i++) b[i]=(uint8_t)esp_random();
 b[6]=(b[6]&0x0f)|0x40; b[8]=(b[8]&0x3f)|0x80;
 char value[37]; snprintf(value,sizeof(value),"%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15]); return String(value);
}
String isoTime(int64_t epochMicros) {
 time_t seconds=epochMicros/1000000; tm utc; gmtime_r(&seconds,&utc);
 char date[24]; strftime(date,sizeof(date),"%Y-%m-%dT%H:%M:%S",&utc);
 char out[40]; snprintf(out,sizeof(out),"%s.%06ldZ",date,(long)(epochMicros%1000000)); return String(out);
}
String oldest(uint32_t &count) {
 count=0; uint64_t smallest=UINT64_MAX; String result;
 File dir=LittleFS.open("/outbox"); File f=dir.openNextFile();
 while(f) { String path=f.path(); if(path.endsWith(".json")) {
 count++; int slash=path.lastIndexOf('/'); uint64_t sequence=strtoull(path.substring(slash+1).c_str(),nullptr,10);
 if(sequence<smallest) { smallest=sequence; result=path; } nextSequence=max(nextSequence,sequence+1);
 } f=dir.openNextFile(); } return result;
}
void stopFault(const char* reason) { fault=true; machineRunning=false; digitalWrite(MACHINE_ENABLE_PIN,LOW); Serial.printf("FAULT: %s; revise a fila antes de reiniciar\n",reason); }
void captureTask(void*) {
 Pulse pulse;
 while(true) {
 if(xQueueReceive(pulses,&pulse,pdMS_TO_TICKS(20))==pdTRUE) {
 xSemaphoreTake(storageMutex,portMAX_DELAY); uint32_t count; oldest(count);
 if(count>=MAX_QUEUE-128) stopFault("limite preventivo da fila; aguarde sincronizacao");
 if(count>=MAX_QUEUE) { xSemaphoreGive(storageMutex); stopFault("fila cheia"); continue; }
 JsonDocument event; event["deviceId"]=DEVICE_ID; event["operationId"]=OPERATION_ID; event["eventId"]=uuid(); event["timestamp"]=isoTime(epochOffset+pulse.monotonicMicros);
 String path="/outbox/"+String((unsigned long long)nextSequence++)+".json";
 String temporary=path+".tmp"; File f=LittleFS.open(temporary,"w"); bool ok=false;
 if(f) { size_t expected=measureJson(event); size_t written=serializeJson(event,f); f.flush(); f.close(); ok=written==expected && LittleFS.rename(temporary,path); }
 xSemaphoreGive(storageMutex); if(!ok) stopFault("falha ao persistir evento");
 }
 if(overflowFault) stopFault("overflow do buffer do sensor");
 }
}
int call(const String &path,const String &payload,String &response) {
 if(WiFi.status()!=WL_CONNECTED) return -1;
 HTTPClient http; WiFiClient plain; WiFiClientSecure tls;
 String url=String(API_BASE)+path;
 if(url.startsWith("https://")) { if(strlen(SERVER_CA_PEM)==0) return -2; tls.setCACert(SERVER_CA_PEM); if(!http.begin(tls,url)) return -1; }
 else if(!http.begin(plain,url)) return -1;
 http.setConnectTimeout(3000); http.setTimeout(3000); http.addHeader("X-Device-Key",DEVICE_API_KEY); http.addHeader("X-Device-Id",DEVICE_ID);
 int code;
 if(payload.isEmpty()) code=http.GET(); else { http.addHeader("Content-Type","application/json"); code=http.POST(payload); }
 if(code>0) response=http.getString(); http.end(); return code;
}
void recover() {
 // Um arquivo temporário completo pode ser recuperado após uma interrupção.
 File dir=LittleFS.open("/outbox"); File f=dir.openNextFile();
 std::vector<String> temporary;
 while(f) { if(String(f.path()).endsWith(".tmp")) temporary.push_back(f.path()); f=dir.openNextFile(); }
 for(auto path:temporary) { File file=LittleFS.open(path,"r"); JsonDocument doc; auto error=deserializeJson(doc,file); file.close();
 if(!error && doc["eventId"].is<String>() && doc["timestamp"].is<String>()) LittleFS.rename(path,path.substring(0,path.length()-4));
 else { LittleFS.rename(path,"/rejected/"+path.substring(path.lastIndexOf('/')+1)); fault=true; }
 }
 uint32_t count; oldest(count);
 File rejected=LittleFS.open("/rejected"); File entry=rejected.openNextFile(); if(entry) fault=true;
}
void setup() {
 Serial.begin(115200); pinMode(MACHINE_ENABLE_PIN,OUTPUT); digitalWrite(MACHINE_ENABLE_PIN,LOW);
 pinMode(SENSOR_PIN,INPUT_PULLUP); storageMutex=xSemaphoreCreateMutex(); pulses=xQueueCreate(128,sizeof(Pulse));
 if(!LittleFS.begin(false)) { stopFault("LittleFS indisponível; inicialize a partição sem apagar dados existentes"); return; }
 LittleFS.mkdir("/outbox"); LittleFS.mkdir("/rejected"); recover();
 WiFi.mode(WIFI_STA); WiFi.begin(WIFI_SSID,WIFI_PASSWORD);
 configTime(0,0,"pool.ntp.org","time.nist.gov");
 time_t seconds=time(nullptr); while(seconds<1700000000) { delay(250); seconds=time(nullptr); }
 timeval now; gettimeofday(&now,nullptr); epochOffset=(int64_t)now.tv_sec*1000000+now.tv_usec-esp_timer_get_time();
 // A fila persistida tem timestamps absolutos; relógio monotônico evita saltos NTP nesta sessão.
 xTaskCreatePinnedToCore(captureTask,"capture",6144,nullptr,3,nullptr,1);
 attachInterrupt(digitalPinToInterrupt(SENSOR_PIN),onPulse,FALLING);
}
void loop() {
 if(!pulses || !storageMutex || !LittleFS.exists("/outbox")) { delay(1000); return; }
 static uint32_t lastState=0,lastSend=0,backoff=250;
 if(WiFi.status()!=WL_CONNECTED) { WiFi.reconnect(); delay(250); return; }
 if(millis()-lastState>=1000) {
 lastState=millis(); String response; int code=call("/api/production/device-state","",response);
 if(code==200) { JsonDocument state; bool running=false;
 if(!deserializeJson(state,response)) for(JsonObject b:state.as<JsonArray>()) if(b["operationId"]==OPERATION_ID && b["status"]=="EM_EXECUCAO") running=true;
 machineRunning=running && !fault; digitalWrite(MACHINE_ENABLE_PIN,machineRunning?HIGH:LOW);
 } else if(code==401 || code==403) stopFault("credencial inválida");
 // Durante queda de rede, a máquina mantém o último estado conhecido e a captura continua.
 }
 if(millis()-lastSend<backoff) { delay(5); return; } lastSend=millis();
 xSemaphoreTake(storageMutex,portMAX_DELAY); uint32_t count; String path=oldest(count); String payload;
 if(!path.isEmpty()) { File f=LittleFS.open(path,"r"); payload=f.readString(); f.close(); }
 xSemaphoreGive(storageMutex); if(path.isEmpty()) { delay(20); return; }
 String response; int code=call("/api/production/events",payload,response); JsonDocument reply; JsonDocument sent;
 deserializeJson(sent,payload); bool parsed=!deserializeJson(reply,response);
 if(code==200 && parsed && reply["accepted"]==true && reply["eventId"].as<String>()==sent["eventId"].as<String>()) {
 xSemaphoreTake(storageMutex,portMAX_DELAY); bool removed=LittleFS.remove(path); xSemaphoreGive(storageMutex); if(!removed) stopFault("falha ao confirmar ACK local"); backoff=250;
 } else if(code==409 && parsed && (reply["code"]=="BATCH_FINALIZADO" || reply["code"]=="EVENT_DURING_PAUSE" || reply["code"]=="INVALID_EVENT_TIME" || reply["code"]=="EVENT_ID_REUSED")) {
 xSemaphoreTake(storageMutex,portMAX_DELAY); LittleFS.rename(path,"/rejected/"+path.substring(path.lastIndexOf('/')+1)); xSemaphoreGive(storageMutex); stopFault("evento rejeitado permanentemente; arquivo preservado");
 } else { backoff=min((uint32_t)10000,backoff*2); if(code==401 || code==403 || code==400) stopFault("requisição não autorizada ou inválida"); }
 delay(5);
}
