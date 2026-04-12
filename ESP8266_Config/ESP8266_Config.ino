#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <Wire.h>
#include <MPU6050.h>

const char* WIFI_SSID     = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
const char* MQTT_BROKER   = "YOUR_PC_LAN_IP";
const int   MQTT_PORT     = 1883;
const char* CLIENT_ID     = "ESP8266_KY037";

// ── Pins ─────────────────────────────────────────────────────────
const int MIC_PIN  = A0;
const int HALL_PIN = D5;
// MPU-6050: SDA → D2 (GPIO4), SCL → D1 (GPIO5)

// ── Topics ───────────────────────────────────────────────────────
const char* TOPIC_MIC    = "Sensor/Mic";
const char* TOPIC_ACCEL  = "Sensor/Bewegung";
const char* TOPIC_GYRO   = "Sensor/Gyro";
const char* TOPIC_MAGNET = "Sensor/Magnet";
const char* TOPIC_MODE   = "Control/Mode";

// ── Mode ─────────────────────────────────────────────────────────
#define MODE_STREAM  0
#define MODE_BURST   1
#define MODE_AVERAGE 2
int currentMode = MODE_STREAM;

// ── Timing ───────────────────────────────────────────────────────
const long SAMPLE_MS_MIC  = 20;    // 50Hz for mic
const long SAMPLE_MS_SLOW = 100;   // 10Hz for accel, gyro, hall
const long WINDOW_MS      = 5000;  // burst/average send every 5s

unsigned long lastSampleMic  = 0;
unsigned long lastSampleSlow = 0;
unsigned long lastWindow     = 0;

// ── Burst buffers ─────────────────────────────────────────────────
const int BURST_SIZE_MIC  = 250;
const int BURST_SIZE_SLOW = 50;

int   micBurst[BURST_SIZE_MIC];
int   micBurstCount = 0;

float accelXBurst[BURST_SIZE_SLOW];
float accelYBurst[BURST_SIZE_SLOW];
float accelZBurst[BURST_SIZE_SLOW];
int   accelBurstCount = 0;

float gyroXBurst[BURST_SIZE_SLOW];
float gyroYBurst[BURST_SIZE_SLOW];
float gyroZBurst[BURST_SIZE_SLOW];
int   gyroBurstCount = 0;

int   hallBurst[BURST_SIZE_SLOW];
int   hallBurstCount = 0;

// ── Average accumulators ──────────────────────────────────────────
long  micSum = 0;  int micCount = 0;
float axSum  = 0,  aySum = 0,  azSum = 0;  int accelCount = 0;
float gxSum  = 0,  gySum = 0,  gzSum = 0;  int gyroCount  = 0;
long  hallSum = 0; int hallCount = 0;

WiFiClient   wifiClient;
PubSubClient mqtt(wifiClient);

MPU6050 mpu;

// ─────────────────────────────────────────────────────────────────
// Mode switching
// ─────────────────────────────────────────────────────────────────
void setMode(int mode) {
    currentMode = mode;

    lastSampleMic  = millis();
    lastSampleSlow = millis();
    lastWindow     = millis();

    micBurstCount   = 0;
    accelBurstCount = 0;
    gyroBurstCount  = 0;
    hallBurstCount  = 0;

    micSum  = 0; micCount  = 0;
    axSum   = 0; aySum  = 0; azSum  = 0; accelCount = 0;
    gxSum   = 0; gySum  = 0; gzSum  = 0; gyroCount  = 0;
    hallSum = 0; hallCount = 0;

    const char* name = (mode == MODE_STREAM)  ? "STREAM"  :
                       (mode == MODE_BURST)   ? "BURST"   : "AVERAGE";
    Serial.println("Mode → " + String(name));
}

// ─────────────────────────────────────────────────────────────────
// MQTT callback
// ─────────────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────
// Connection helpers
// ─────────────────────────────────────────────────────────────────
void connectWiFi() {
    Serial.print("WiFi");
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
    Serial.println(" OK " + WiFi.localIP().toString());
}

void connectMQTT() {
    mqtt.setKeepAlive(120);
    while (!mqtt.connected()) {
        Serial.print("MQTT...");
        if (mqtt.connect(CLIENT_ID)) {
            Serial.println("connected");
            mqtt.subscribe(TOPIC_MODE, 1);
        } else {
            Serial.print("fail rc="); Serial.println(mqtt.state());
            delay(2000);
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Sensor reads
// ─────────────────────────────────────────────────────────────────
void readMic(int &val) {
    val = analogRead(MIC_PIN);
}

void readAccel(float &x, float &y, float &z) {
    int16_t ax, ay, az;
    mpu.getAcceleration(&ax, &ay, &az);
    x = ax / 16384.0;
    y = ay / 16384.0;
    z = az / 16384.0;
    x = 0.0; y = 0.0; z = 0.0;
}

void readGyro(float &x, float &y, float &z) {
    int16_t gx, gy, gz;
    mpu.getRotation(&gx, &gy, &gz);
    x = gx / 131.0;
    y = gy / 131.0;
    z = gz / 131.0;
    x = 0.0; y = 0.0; z = 0.0;
}

void readHall(int &val) {
    // ── Uncomment when A3144 arrives ─────────────────────────────
    // val = digitalRead(HALL_PIN);
    val = 0;
}

// ─────────────────────────────────────────────────────────────────
// Publish helpers
// ─────────────────────────────────────────────────────────────────
void publishMic(int val) {
    mqtt.publish(TOPIC_MIC, String(val).c_str());
}

void publishAccel(float x, float y, float z) {
    String p = String(x, 2) + "," + String(y, 2) + "," + String(z, 2);
    mqtt.publish(TOPIC_ACCEL, p.c_str());
}

void publishGyro(float x, float y, float z) {
    String p = String(x, 2) + "," + String(y, 2) + "," + String(z, 2);
    mqtt.publish(TOPIC_GYRO, p.c_str());
}

void publishHall(int val) {
    String p = String(val) + ",0,0";
    mqtt.publish(TOPIC_MAGNET, p.c_str());
}

// ─────────────────────────────────────────────────────────────────
// Window sends
// ─────────────────────────────────────────────────────────────────
void sendBurst() {
    String micPayload = "";
    for (int i = 0; i < micBurstCount; i++) {
        micPayload += String(micBurst[i]);
        if (i < micBurstCount - 1) micPayload += ",";
    }
    mqtt.publish(TOPIC_MIC, micPayload.c_str());
    Serial.println("BURST mic: " + String(micBurstCount) + " readings");
    micBurstCount = 0;

    if (accelBurstCount > 0) {
        publishAccel(
            accelXBurst[accelBurstCount - 1],
            accelYBurst[accelBurstCount - 1],
            accelZBurst[accelBurstCount - 1]
        );
        Serial.println("BURST accel: " + String(accelBurstCount) + " readings");
        accelBurstCount = 0;
    }

    if (gyroBurstCount > 0) {
        publishGyro(
            gyroXBurst[gyroBurstCount - 1],
            gyroYBurst[gyroBurstCount - 1],
            gyroZBurst[gyroBurstCount - 1]
        );
        Serial.println("BURST gyro: " + String(gyroBurstCount) + " readings");
        gyroBurstCount = 0;
    }

    if (hallBurstCount > 0) {
        publishHall(hallBurst[hallBurstCount - 1]);
        Serial.println("BURST hall: " + String(hallBurstCount) + " readings");
        hallBurstCount = 0;
    }
}

void sendAverage() {
    int micAvg = micCount > 0 ? micSum / micCount : 0;
    mqtt.publish(TOPIC_MIC, String(micAvg).c_str());
    Serial.println("AVG mic: " + String(micAvg) + " n=" + String(micCount));
    micSum = 0; micCount = 0;

    float ax = accelCount > 0 ? axSum / accelCount : 0;
    float ay = accelCount > 0 ? aySum / accelCount : 0;
    float az = accelCount > 0 ? azSum / accelCount : 0;
    publishAccel(ax, ay, az);
    Serial.println("AVG accel n=" + String(accelCount));
    axSum = 0; aySum = 0; azSum = 0; accelCount = 0;

    float gx = gyroCount > 0 ? gxSum / gyroCount : 0;
    float gy = gyroCount > 0 ? gySum / gyroCount : 0;
    float gz = gyroCount > 0 ? gzSum / gyroCount : 0;
    publishGyro(gx, gy, gz);
    Serial.println("AVG gyro n=" + String(gyroCount));
    gxSum = 0; gySum = 0; gzSum = 0; gyroCount = 0;

    int hallAvg = hallCount > 0 ? hallSum / hallCount : 0;
    publishHall(hallAvg);
    Serial.println("AVG hall n=" + String(hallCount));
    hallSum = 0; hallCount = 0;
}

// ─────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    pinMode(HALL_PIN, INPUT);

    Wire.begin();
    mpu.initialize();
    Serial.println("MPU-6050: " + String(mpu.testConnection() ? "OK" : "FAIL"));

    connectWiFi();
    mqtt.setServer(MQTT_BROKER, MQTT_PORT);
    mqtt.setCallback(onMessage);
    mqtt.setBufferSize(512);
    connectMQTT();

    lastSampleMic  = millis();
    lastSampleSlow = millis();
    lastWindow     = millis();
}

// ─────────────────────────────────────────────────────────────────
void loop() {
    if (!mqtt.connected()) connectMQTT();
    mqtt.loop();

    unsigned long now = millis();

    // ── Fast tick: mic only (20ms / 50Hz) ────────────────────────
    if (now - lastSampleMic >= SAMPLE_MS_MIC) {
        lastSampleMic = now;

        int micVal;
        readMic(micVal);

        if (currentMode == MODE_STREAM) {
            publishMic(micVal);
        } else if (currentMode == MODE_BURST) {
            if (micBurstCount < BURST_SIZE_MIC) micBurst[micBurstCount++] = micVal;
        } else if (currentMode == MODE_AVERAGE) {
            micSum += micVal; micCount++;
        }
    }

    // ── Slow tick: accel, gyro, hall (100ms / 10Hz) ──────────────
    if (now - lastSampleSlow >= SAMPLE_MS_SLOW) {
        lastSampleSlow = now;

        float ax, ay, az, gx, gy, gz;
        int   hallVal;
        readAccel(ax, ay, az);
        readGyro(gx, gy, gz);
        readHall(hallVal);

        if (currentMode == MODE_STREAM) {
            publishAccel(ax, ay, az);
            publishGyro(gx, gy, gz);
            publishHall(hallVal);
        } else if (currentMode == MODE_BURST) {
            if (accelBurstCount < BURST_SIZE_SLOW) {
                accelXBurst[accelBurstCount] = ax;
                accelYBurst[accelBurstCount] = ay;
                accelZBurst[accelBurstCount] = az;
                accelBurstCount++;
            }
            if (gyroBurstCount < BURST_SIZE_SLOW) {
                gyroXBurst[gyroBurstCount] = gx;
                gyroYBurst[gyroBurstCount] = gy;
                gyroZBurst[gyroBurstCount] = gz;
                gyroBurstCount++;
            }
            if (hallBurstCount < BURST_SIZE_SLOW) hallBurst[hallBurstCount++] = hallVal;
        } else if (currentMode == MODE_AVERAGE) {
            axSum += ax; aySum += ay; azSum += az; accelCount++;
            gxSum += gx; gySum += gy; gzSum += gz; gyroCount++;
            hallSum += hallVal; hallCount++;
        }
    }

    // ── Window send: burst and average (every 5s) ─────────────────
    if (currentMode != MODE_STREAM && now - lastWindow >= WINDOW_MS) {
        mqtt.loop();
        if (currentMode == MODE_BURST)   sendBurst();
        if (currentMode == MODE_AVERAGE) sendAverage();
        lastWindow = now;
    }
}