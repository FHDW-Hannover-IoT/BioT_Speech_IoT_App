#include <ESP8266WiFi.h>
#include <PubSubClient.h>

const char* WIFI_SSID     = "";
const char* WIFI_PASSWORD = "";
const char* MQTT_BROKER   = "";
const int   MQTT_PORT     = ;
const char* CLIENT_ID     = "ESP8266_KY037";

const int MIC_PIN = A0;

const char* TOPIC_MIC  = "Sensor/Mic";
const char* TOPIC_MODE = "Control/Mode";
const char* TOPIC_ACK  = "Control/Mode/Ack";

#define MODE_STREAM  0
#define MODE_BURST   1
#define MODE_AVERAGE 2

int currentMode = MODE_STREAM;

// Burst
const int BURST_SIZE = 50;
int burstBuffer[BURST_SIZE];
int burstCount = 0;

// Average
long averageSum = 0;
int  averageCount = 0;

// Timing
unsigned long lastSample  = 0;
unsigned long lastWindow  = 0;
unsigned long lastLoop    = 0;
const long SAMPLE_MS  = 100;   // sample every 100ms in all modes
const long WINDOW_MS  = 5000;  // burst/average send every 5s

WiFiClient   wifiClient;
PubSubClient mqtt(wifiClient);

void setMode(int mode) {
    currentMode = mode;
    burstCount    = 0;
    averageSum    = 0;
    averageCount  = 0;
    lastWindow    = millis();
    lastSample    = millis();

    const char* ack = (mode == MODE_STREAM)  ? "STREAM"  :
                      (mode == MODE_BURST)   ? "BURST"   : "AVERAGE";
    mqtt.publish(TOPIC_ACK, ack, true);
    Serial.println("Mode → " + String(ack));
}

void onMessage(char* topic, byte* payload, unsigned int length) {
    String msg = "";
    for (unsigned int i = 0; i < length; i++) msg += (char)payload[i];
    msg.trim();
    Serial.println("CMD: " + String(topic) + " = " + msg);

    if (String(topic) == TOPIC_MODE) {
        if      (msg == "STREAM")  setMode(MODE_STREAM);
        else if (msg == "BURST")   setMode(MODE_BURST);
        else if (msg == "AVERAGE") setMode(MODE_AVERAGE);
    }
}

void connectWiFi() {
    Serial.print("WiFi");
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
    Serial.println(" OK " + WiFi.localIP().toString());
}

void connectMQTT() {
    mqtt.setKeepAlive(120);  // tell broker we ping every 120s max
    while (!mqtt.connected()) {
        Serial.print("MQTT...");
        if (mqtt.connect(CLIENT_ID)) {
            Serial.println("connected");
            mqtt.subscribe(TOPIC_MODE, 1);
            // re-apply current mode so ACK is retained on broker after reconnect
            setMode(currentMode);
        } else {
            Serial.print("fail rc="); Serial.println(mqtt.state());
            delay(2000);
        }
    }
}

void setup() {
    Serial.begin(115200);
    connectWiFi();
    mqtt.setServer(MQTT_BROKER, MQTT_PORT);
    mqtt.setCallback(onMessage);
    mqtt.setBufferSize(512);
    connectMQTT();
    lastWindow = millis();
    lastSample = millis();
}

void loop() {
    // Always maintain connection first
    if (!mqtt.connected()) {
        connectMQTT();
    }
    mqtt.loop();  // processes incoming messages + sends keep-alive pings

    unsigned long now = millis();

    // Only sample on the 100ms tick
    if (now - lastSample < SAMPLE_MS) return;
    lastSample = now;

    int micValue = analogRead(MIC_PIN);

    if (currentMode == MODE_STREAM) {
        mqtt.publish(TOPIC_MIC, String(micValue).c_str());

    } else if (currentMode == MODE_BURST) {
        if (burstCount < BURST_SIZE) {
            burstBuffer[burstCount++] = micValue;
        }
        if (now - lastWindow >= WINDOW_MS) {
            String payload = "";
            for (int i = 0; i < burstCount; i++) {
                payload += String(burstBuffer[i]);
                if (i < burstCount - 1) payload += ",";
            }
            mqtt.loop();  // keep alive right before big publish
            mqtt.publish(TOPIC_MIC, payload.c_str());
            Serial.println("BURST: " + String(burstCount) + " readings");
            burstCount = 0;
            lastWindow = now;
        }

    } else if (currentMode == MODE_AVERAGE) {
        averageSum += micValue;
        averageCount++;
        if (now - lastWindow >= WINDOW_MS) {
            int avg = averageCount > 0 ? averageSum / averageCount : 0;
            mqtt.loop();  // keep alive right before publish
            mqtt.publish(TOPIC_MIC, String(avg).c_str());
            Serial.println("AVG: " + String(avg) + " n=" + String(averageCount));
            averageSum   = 0;
            averageCount = 0;
            lastWindow   = now;
        }
    }
}