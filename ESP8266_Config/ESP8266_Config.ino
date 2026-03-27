#include <ESP8266WiFi.h>
#include <PubSubClient.h>

// ── Config ───────────────────────────────────────────
const char* WIFI_SSID     = "";
const char* WIFI_PASSWORD = "";
const char* MQTT_BROKER   = "";  // your PC's LAN IP
const int   MQTT_PORT     = ;
const char* CLIENT_ID     = "ESP8266_KY037";

// ── Pins ─────────────────────────────────────────────
const int MIC_PIN = A0;

// ── Topics ───────────────────────────────────────────
const char* TOPIC_MIC = "Sensor/Bewegung";

// ── Timing ───────────────────────────────────────────
unsigned long lastPublish = 0;
const long INTERVAL_MS = 10;

WiFiClient   wifiClient;
PubSubClient mqtt(wifiClient);

void connectWiFi() {
  Serial.print("Connecting to Wi-Fi");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nConnected! IP: " + WiFi.localIP().toString());
}

void connectMQTT() {
  while (!mqtt.connected()) {
    Serial.print("Connecting to MQTT...");
    if (mqtt.connect(CLIENT_ID)) {
      Serial.println(" connected!");
    } else {
      Serial.print(" failed, rc=");
      Serial.print(mqtt.state());
      Serial.println(" retrying in 2s");
      delay(2000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  connectWiFi();
  mqtt.setServer(MQTT_BROKER, MQTT_PORT);
}

void loop() {
  if (!mqtt.connected()) connectMQTT();
  mqtt.loop();

  unsigned long now = millis();
  if (now - lastPublish >= INTERVAL_MS) {
    lastPublish = now;

    int micValue = analogRead(MIC_PIN);

    // Format as x,y,z CSV to match the Android app's parser
    String payload = String(micValue) + ",0,0";

    mqtt.publish(TOPIC_MIC, payload.c_str());
    Serial.println("Published: " + payload);
  }
}