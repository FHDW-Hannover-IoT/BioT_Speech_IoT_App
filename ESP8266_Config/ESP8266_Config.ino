#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <Wire.h>
#include <MPU6050.h>
#include <math.h>

const char* WIFI_SSID     = "Kevin";
const char* WIFI_PASSWORD = "kevin123";
const char* MQTT_BROKER   = "10.219.112.171";
const int   MQTT_PORT     = 1883;
const char* CLIENT_ID     = "ESP8266_BioT";

// ── Pins ─────────────────────────────────────────────────────────
const int HALL_PIN = D5;  // GPIO14 — free pin, D1 is reserved for I2C SCL
// MPU-6050: SDA → D2 (GPIO4), SCL → D1 (GPIO5)

// ── Topics ───────────────────────────────────────────────────────
const char* TOPIC_ACCEL  = "Sensor/Bewegung";
const char* TOPIC_GYRO   = "Sensor/Gyro";
const char* TOPIC_MAGNET = "Sensor/Magnet";
const char* TOPIC_MODE   = "Control/Mode";

// ── Mode ─────────────────────────────────────────────────────────
#define MODE_STREAM  0
#define MODE_BURST   1   // DP-filtered burst
#define MODE_AVERAGE 2
int currentMode = MODE_STREAM;

// ── Timing ───────────────────────────────────────────────────────
const long SAMPLE_MS = 100;   // 10 Hz
const long WINDOW_MS = 5000;  // burst/average window

unsigned long lastSample = 0;
unsigned long lastWindow = 0;

// ── Burst buffers (stack-allocated, fixed size) ───────────────────
const int BURST_SIZE = 50;

float accelXBurst[BURST_SIZE], accelYBurst[BURST_SIZE], accelZBurst[BURST_SIZE];
int   accelBurstCount = 0;

float gyroXBurst[BURST_SIZE], gyroYBurst[BURST_SIZE], gyroZBurst[BURST_SIZE];
int   gyroBurstCount = 0;

// Hall: digital signal — track last sent value, only publish on change
int   lastHallSent = -1;

// Continuity anchors — last raw point of previous burst window
// Seeded into position 0 of each new window so DP spans window boundaries
float prevAccelX = 0, prevAccelY = 0, prevAccelZ = 0;
float prevGyroX  = 0, prevGyroY  = 0, prevGyroZ  = 0;
bool  hasPrevWindow = false;

// ── Average accumulators ──────────────────────────────────────────
float axSum = 0, aySum = 0, azSum = 0; int accelCount = 0;
float gxSum = 0, gySum = 0, gzSum = 0; int gyroCount  = 0;
long  hallSum = 0;                      int hallCount  = 0;

// ── Douglas-Peucker epsilons ──────────────────────────────────────
const float DP_EPSILON_ACCEL = 0.05f;   // g
const float DP_EPSILON_GYRO  = 1.0f;    // deg/s

// ── Calibration offsets (computed at startup) ─────────────────────
float offAX = 0, offAY = 0, offAZ = 0;
float offGX = 0, offGY = 0, offGZ = 0;

WiFiClient   wifiClient;
PubSubClient mqtt(wifiClient);
MPU6050      mpu;

// ─────────────────────────────────────────────────────────────────
// Douglas-Peucker (iterative — safe for ESP8266 small call stack)
// data[0..n-1]: one axis of readings, index = time axis
// mask[i] = true → keep this point
// Uses a fixed int stack[100] on the call stack (~400 bytes), freed on return
// ─────────────────────────────────────────────────────────────────
static float dpPointLineDist(float* data, int idx, int start, int end) {
    if (start == end) return fabsf(data[idx] - data[start]);
    float dx  = (float)(end - start);
    float dy  = data[end] - data[start];
    float len = sqrtf(dx * dx + dy * dy);
    if (len < 1e-6f) return 0.0f;
    return fabsf(dy * (float)(idx - start) - dx * (data[idx] - data[start])) / len;
}

// Recursive DP — depth is O(log n) per call chain (~6 frames for n=50)
// Each completed recursive branch frees its frame before the sibling runs
static void dpRecurse(float* data, float epsilon, bool* mask, int start, int end) {
    if (end - start < 2) return;

    float maxDist = 0.0f;
    int   maxIdx  = start;
    for (int i = start + 1; i < end; i++) {
        float d = dpPointLineDist(data, i, start, end);
        if (d > maxDist) { maxDist = d; maxIdx = i; }
    }

    if (maxDist > epsilon) {
        mask[maxIdx] = true;
        dpRecurse(data, epsilon, mask, start, maxIdx);
        dpRecurse(data, epsilon, mask, maxIdx, end);
    }
}

void dpReduce(float* data, int n, float epsilon, bool* mask) {
    for (int i = 0; i < n; i++) mask[i] = false;
    if (n <= 0) return;
    if (n <= 2) { for (int i = 0; i < n; i++) mask[i] = true; return; }
    mask[0] = true;
    mask[n - 1] = true;
    dpRecurse(data, epsilon, mask, 0, n - 1);
}

// ─────────────────────────────────────────────────────────────────
void setMode(int mode) {
    currentMode = mode;
    lastSample  = millis();
    lastWindow  = millis();

    accelBurstCount = gyroBurstCount = 0;
    axSum = aySum = azSum = 0; accelCount = 0;
    gxSum = gySum = gzSum = 0; gyroCount  = 0;
    hallSum = 0; hallCount = 0;
    lastHallSent = -1;
    hasPrevWindow = false;

    const char* name = (mode == MODE_STREAM) ? "STREAM" :
                       (mode == MODE_BURST)  ? "BURST"  : "AVERAGE";
    Serial.println("Mode -> " + String(name));
}

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
// Startup calibration — keep sensor still during setup()
// Samples N readings, computes bias. Accel Z bias excludes 1g so gravity is preserved.
// ─────────────────────────────────────────────────────────────────
void calibrate(int samples = 200) {
    Serial.print("Calibrating (keep still)");
    double sAX = 0, sAY = 0, sAZ = 0;
    double sGX = 0, sGY = 0, sGZ = 0;

    for (int i = 0; i < samples; i++) {
        int16_t ax, ay, az, gx, gy, gz;
        mpu.getAcceleration(&ax, &ay, &az);
        mpu.getRotation(&gx, &gy, &gz);
        sAX += ax / 16384.0f;
        sAY += ay / 16384.0f;
        sAZ += az / 16384.0f;
        sGX += gx / 131.0f;
        sGY += gy / 131.0f;
        sGZ += gz / 131.0f;
        if (i % 40 == 0) Serial.print(".");
        delay(5);
    }

    offAX = sAX / samples;           // remove X bias (target 0)
    offAY = sAY / samples;           // remove Y bias (target 0)
    offAZ = sAZ / samples - 1.0f;   // remove Z bias but keep 1g gravity
    offGX = sGX / samples;
    offGY = sGY / samples;
    offGZ = sGZ / samples;

    Serial.println(" done");
    Serial.println("Offsets A: " + String(offAX,3) + "," + String(offAY,3) + "," + String(offAZ,3) +
                   "  G: "       + String(offGX,3) + "," + String(offGY,3) + "," + String(offGZ,3));
}

void readAccel(float &x, float &y, float &z) {
    int16_t ax, ay, az;
    mpu.getAcceleration(&ax, &ay, &az);
    x = ax / 16384.0f - offAX;
    y = ay / 16384.0f - offAY;
    z = az / 16384.0f - offAZ;
}

void readGyro(float &x, float &y, float &z) {
    int16_t gx, gy, gz;
    mpu.getRotation(&gx, &gy, &gz);
    x = gx / 131.0f - offGX;
    y = gy / 131.0f - offGY;
    z = gz / 131.0f - offGZ;
}

void readHall(int &val) {
    val = !digitalRead(HALL_PIN);  // A3144 open-collector: LOW = magnet → invert so 1 = magnet detected
}

// ─────────────────────────────────────────────────────────────────
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
// DP burst send — runs DP on each axis, ORs keep-masks, publishes
// surviving points. All mask arrays are on the call stack (6 × 50 bytes
// = 300 bytes), freed on return. No heap involved.
// ─────────────────────────────────────────────────────────────────
void sendBurstDP() {
    // ── Accel ─────────────────────────────────────────────────────
    if (accelBurstCount >= 2) {
        bool mX[BURST_SIZE], mY[BURST_SIZE], mZ[BURST_SIZE];
        dpReduce(accelXBurst, accelBurstCount, DP_EPSILON_ACCEL, mX);
        dpReduce(accelYBurst, accelBurstCount, DP_EPSILON_ACCEL, mY);
        dpReduce(accelZBurst, accelBurstCount, DP_EPSILON_ACCEL, mZ);

        int kept = 0;
        for (int i = 0; i < accelBurstCount; i++) {
            if (mX[i] || mY[i] || mZ[i]) {
                if (i == 0 && hasPrevWindow) continue;  // carry-over anchor, already published
                publishAccel(accelXBurst[i], accelYBurst[i], accelZBurst[i]);
                kept++;
                mqtt.loop();
            }
        }
        Serial.println("DP accel: " + String(accelBurstCount) + " -> " + String(kept) + " pts");

        // Save last raw point as anchor for next window
        prevAccelX = accelXBurst[accelBurstCount - 1];
        prevAccelY = accelYBurst[accelBurstCount - 1];
        prevAccelZ = accelZBurst[accelBurstCount - 1];
    }

    // Re-seed position 0 with previous endpoint so next window's DP is continuous
    accelXBurst[0] = prevAccelX;
    accelYBurst[0] = prevAccelY;
    accelZBurst[0] = prevAccelZ;
    accelBurstCount = hasPrevWindow ? 1 : 0;

    // ── Gyro ──────────────────────────────────────────────────────
    if (gyroBurstCount >= 2) {
        bool mX[BURST_SIZE], mY[BURST_SIZE], mZ[BURST_SIZE];
        dpReduce(gyroXBurst, gyroBurstCount, DP_EPSILON_GYRO, mX);
        dpReduce(gyroYBurst, gyroBurstCount, DP_EPSILON_GYRO, mY);
        dpReduce(gyroZBurst, gyroBurstCount, DP_EPSILON_GYRO, mZ);

        int kept = 0;
        for (int i = 0; i < gyroBurstCount; i++) {
            if (mX[i] || mY[i] || mZ[i]) {
                if (i == 0 && hasPrevWindow) continue;  // carry-over anchor, already published
                publishGyro(gyroXBurst[i], gyroYBurst[i], gyroZBurst[i]);
                kept++;
                mqtt.loop();
            }
        }
        Serial.println("DP gyro:  " + String(gyroBurstCount) + " -> " + String(kept) + " pts");

        prevGyroX = gyroXBurst[gyroBurstCount - 1];
        prevGyroY = gyroYBurst[gyroBurstCount - 1];
        prevGyroZ = gyroZBurst[gyroBurstCount - 1];
    }

    gyroXBurst[0] = prevGyroX;
    gyroYBurst[0] = prevGyroY;
    gyroZBurst[0] = prevGyroZ;
    gyroBurstCount = hasPrevWindow ? 1 : 0;

    hasPrevWindow = true;
}

// ─────────────────────────────────────────────────────────────────
void sendAverage() {
    publishAccel(
        accelCount > 0 ? axSum / accelCount : 0,
        accelCount > 0 ? aySum / accelCount : 0,
        accelCount > 0 ? azSum / accelCount : 0
    );
    Serial.println("AVG accel n=" + String(accelCount));
    axSum = aySum = azSum = 0; accelCount = 0;

    publishGyro(
        gyroCount > 0 ? gxSum / gyroCount : 0,
        gyroCount > 0 ? gySum / gyroCount : 0,
        gyroCount > 0 ? gzSum / gyroCount : 0
    );
    Serial.println("AVG gyro n=" + String(gyroCount));
    gxSum = gySum = gzSum = 0; gyroCount = 0;

    publishHall(hallCount > 0 ? (int)(hallSum / hallCount) : 0);
    Serial.println("AVG hall n=" + String(hallCount));
    hallSum = 0; hallCount = 0;
}

// ─────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    pinMode(HALL_PIN, INPUT_PULLUP);  // internal pull-up — no external resistor needed

    Wire.begin(D2, D1);
    mpu.initialize();
    Serial.println("MPU-6050: " + String(mpu.testConnection() ? "OK" : "FAIL"));
    calibrate();

    connectWiFi();
    mqtt.setServer(MQTT_BROKER, MQTT_PORT);
    mqtt.setCallback(onMessage);
    mqtt.setBufferSize(512);
    connectMQTT();

    lastSample = millis();
    lastWindow = millis();
}

// ─────────────────────────────────────────────────────────────────
void loop() {
    if (!mqtt.connected()) connectMQTT();
    mqtt.loop();

    unsigned long now = millis();

    if (now - lastSample >= SAMPLE_MS) {
        lastSample = now;

        float ax, ay, az, gx, gy, gz;
        int   hallVal;
        readAccel(ax, ay, az);
        readGyro(gx, gy, gz);
        readHall(hallVal);

        Serial.println("Accel: " + String(ax,2) + "," + String(ay,2) + "," + String(az,2) +
                       "  Gyro: " + String(gx,2) + "," + String(gy,2) + "," + String(gz,2) +
                       "  Hall: " + String(hallVal));

        if (currentMode == MODE_STREAM) {
            publishAccel(ax, ay, az);
            publishGyro(gx, gy, gz);
            // Hall: publish only on change
            if (hallVal != lastHallSent) {
                publishHall(hallVal);
                lastHallSent = hallVal;
            }

        } else if (currentMode == MODE_BURST) {
            if (accelBurstCount < BURST_SIZE) {
                accelXBurst[accelBurstCount] = ax;
                accelYBurst[accelBurstCount] = ay;
                accelZBurst[accelBurstCount++] = az;
            }
            if (gyroBurstCount < BURST_SIZE) {
                gyroXBurst[gyroBurstCount] = gx;
                gyroYBurst[gyroBurstCount] = gy;
                gyroZBurst[gyroBurstCount++] = gz;
            }
            // Hall: always track on change regardless of mode
            if (hallVal != lastHallSent) {
                publishHall(hallVal);
                lastHallSent = hallVal;
            }

        } else {
            axSum += ax; aySum += ay; azSum += az; accelCount++;
            gxSum += gx; gySum += gy; gzSum += gz; gyroCount++;
            hallSum += hallVal; hallCount++;
        }
    }

    if (currentMode != MODE_STREAM && now - lastWindow >= WINDOW_MS) {
        mqtt.loop();
        if (currentMode == MODE_BURST)   sendBurstDP();
        if (currentMode == MODE_AVERAGE) sendAverage();
        lastWindow = now;
    }
}
