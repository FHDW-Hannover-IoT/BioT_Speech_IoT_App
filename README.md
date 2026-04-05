# BioT Speech IoT App — How to Run

> Parts used: ESP8266 NodeMCU + KY-037 + MPU-6050 + A3144 | MQTT + Android | FHDW Hannover IoT
> I misunderstood the assignment and added a microphone to the ESP8266, but you can ignore this part.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Mosquitto Setup](#2-mosquitto-setup)
3. [Windows Firewall](#3-windows-firewall)
4. [IP Address Configuration](#4-ip-address-configuration)
5. [Running the Application](#5-running-the-application)
6. [App Features](#6-app-features)
7. [Troubleshooting](#7-troubleshooting)
8. [Security Notes](#8-security-notes)
9. [Hardware Reference](#9-hardware-reference)

---

## 1. Prerequisites

Make sure you have the following installed on your PC before starting:

| Tool | Purpose | Download |
|------|---------|---------|
| Mosquitto 2.x | MQTT broker — message relay between ESP8266 and app | mosquitto.org/download |
| Android Studio | Build and run the Android app | developer.android.com/studio |
| Arduino IDE | Flash firmware to the NodeMCU ESP8266 | arduino.cc/en/software |
| Java 17+ | Required by Android Studio | Bundled with Android Studio |

---

## 2. Mosquitto Setup

### 2.1 Install Mosquitto

Download and install from [mosquitto.org/download](https://mosquitto.org/download) using default settings.

### 2.2 Configure Mosquitto

Open the config file **as Administrator** in Notepad:

```
C:\Program Files\mosquitto\mosquitto.conf
```

Scroll to the bottom and add exactly these two lines:

```
listener 1883
allow_anonymous true
```

> ⚠️ **Warning:** `allow_anonymous true` means anyone on your local network can connect without a password. This is fine for development — see [Section 8](#8-security-notes) for what to do on public networks.

### Port Selection

This project uses **port 1883** — the standard unencrypted MQTT port. If 1883 is already in use on your machine or you want to use a different port, change the number in the config and update it in both `MainActivity.java` and the Arduino sketch to match.

| Port | Protocol | Use case |
|------|----------|---------|
| **1883** | MQTT (unencrypted) | ✅ Used in this project — standard default for local development |
| 8883 | MQTT over TLS/SSL | Production use — requires certificates, encrypted connection |
| 1884 | MQTT (unencrypted) | Common alternative if 1883 is taken by another service |
| 8884 | MQTT over TLS/SSL | Alternative encrypted port |
| 8080 | MQTT over WebSocket | Browser-based MQTT clients |
| 8443 | MQTT over WebSocket + TLS | Secure browser-based clients |

To switch to a different port, update the config:

```
listener 1884
allow_anonymous true
```
This part explained futhurer in [IP Address Configuration](#4-ip-address-configuration):
Then update `MainActivity.java`:

```java
private static final String PHONE_BROKER = "tcp://192.168.x.x:1884";
private static final String EMULATOR_BROKER = "tcp://10.0.2.2:1884";
```

And the Arduino sketch:

```cpp
const int MQTT_PORT = 1884;
```

Save the file and close Notepad.

### 2.3 Start and Stop Mosquitto

Open **Command Prompt as Administrator** and use these commands:

| Action | Command |
|--------|---------|
| Start Mosquitto | `net start mosquitto` |
| Stop Mosquitto | `net stop mosquitto` |
| Check if running | `sc query mosquitto` |
| Verify port is listening | `netstat -an \| findstr 1883` |
| Run with verbose logs (debug) | `"C:\Program Files\mosquitto\mosquitto.exe" -v -c "C:\Program Files\mosquitto\mosquitto.conf"` |

After starting, `netstat` should show:

```
TCP    0.0.0.0:1883    0.0.0.0:0    LISTENING
TCP    [::]:1883       [::]:0       LISTENING
```

If you see nothing, Mosquitto is not running — check [Section 7](#7-troubleshooting).

---

## 3. Windows Firewall

### 3.1 Allow Mosquitto through the firewall

Run this **once** in Command Prompt as Administrator:

```cmd
netsh advfirewall firewall add rule name="Mosquitto" dir=in action=allow protocol=TCP localport=1883
```

### 3.2 Verify the rule was added

```cmd
netsh advfirewall firewall show rule name="Mosquitto"
```

You should see `Enabled: Yes` in the output.

---

## 4. IP Address Configuration

### 4.1 Find your PC's LAN IP

```cmd
ipconfig
```

Look for `IPv4 Address` under your Wi-Fi adapter — it will look like `192.168.x.x`.

### 4.2 Update the Android app

Open `MainActivity.java` and find this line near the top:

```java
private static final String PHONE_BROKER = "tcp://192.168.x.x:1883";
```

Replace `192.168.x.x` with your PC's actual LAN IP.

> **Note:** The emulator IP (`10.0.2.2`) never needs changing — the app automatically detects whether it is running on an emulator or a real device and picks the correct broker URL.

> ⚠️ Your IP may change every time you reconnect to Wi-Fi. If the app stops working after a network change, run `ipconfig` again and update `PHONE_BROKER`.

### 4.3 Update the Arduino sketch

In the ESP8266 firmware, find and update:

```cpp
const char* WIFI_SSID     = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
const char* MQTT_BROKER   = "YOUR_PC_LAN_IP";
```

After changing these values, re-upload the sketch to the NodeMCU via Arduino IDE.

---

## 5. Running the Application

### 5.1 Full startup sequence

Follow this order every time you start a development session:

1. Start Mosquitto: `net start mosquitto`
2. Verify it is listening: `netstat -an | findstr 1883`
3. Plug in your NodeMCU via USB and open Arduino IDE Serial Monitor (115200 baud)
4. Confirm the ESP8266 shows `WiFi OK` and `MQTT connected` in Serial Monitor
5. Open Android Studio and run the app on the emulator or real device
6. You should see a green **MQTT verbunden** toast on the app

### 5.2 Running on Emulator vs Real Phone

| | Android Emulator | Real Android Phone |
|--|--|--|
| Broker IP used | `10.0.2.2` (automatic) | Your PC LAN IP (set in `PHONE_BROKER`) |
| Network setup | None required | Phone and PC must be on same Wi-Fi |
| Detection | Automatic | Automatic |
| Mic for STT | Laptop mic via emulator | Phone mic directly |
| Best for | Quick testing without hardware | Full hardware testing with ESP8266 |

---

## 6. App Features

### 6.1 MQTT Topics

| Topic | Direction | Format | Description |
|-------|-----------|--------|-------------|
| `Sensor/Mic` | ESP8266 → App | integer or CSV | KY-037 microphone sound level |
| `Sensor/Bewegung` | ESP8266 → App | `x,y,z` floats | MPU-6050 accelerometer data |
| `Sensor/Gyro` | ESP8266 → App | `x,y,z` floats | MPU-6050 gyroscope data |
| `Sensor/Magnet` | ESP8266 → App | `x,y,z` floats | A3144 hall effect sensor |
| `Control/Mode` | App → ESP8266 | `STREAM` / `BURST` / `AVERAGE` | Sets the transmission mode |
| `Control/Mode/Ack` | ESP8266 → App | `STREAM` / `BURST` / `AVERAGE` | ESP8266 confirms mode change |

### 6.2 Data Transmission Modes

The three buttons on the home screen control how the ESP8266 sends sensor data:

| Mode | Mic Rate | Other Sensors Rate | How it works |
|------|----------|--------------------|--------------|
| **Stream** | Every 20ms (50Hz) | Every 100ms (10Hz) | Every reading published immediately |
| **Burst** | Every 20ms, sent every 5s | Every 100ms, sent every 5s | Collects 5s of readings then sends all at once |
| **Average** | Averaged every 5s | Averaged every 5s | Computes the mean over 5s and sends one value |

The selected mode persists across reconnects — both the app and ESP8266 remember the last mode via a retained MQTT message on `Control/Mode/Ack`.

### 6.3 Navigation

| Screen | How to access | What it shows |
|--------|--------------|---------------|
| Home (Sensordaten) | App launch screen | Live values for all sensors + mic mode buttons |
| Accel | Accel button | Accelerometer X/Y/Z line charts with date filter |
| Gyroskop | Gyroskop button | Gyroscope X/Y/Z line charts with date filter |
| Magnetfeld | Magnetfeld button | Magnetometer X/Y/Z line charts with date filter |
| Graphenansicht | Zur Graphenansicht button | All three sensors combined on one chart |
| Ereignisse | Bell icon top right | Event log — sensor threshold alerts |
| Settings | Gear icon top left | App configuration |

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| App shows "MQTT Fehler" toast | Mosquitto not running or wrong IP | Run `net start mosquitto`, check `PHONE_BROKER` IP matches `ipconfig` output |
| Sensor values show `%1$.2f` | No MQTT data arriving yet | Normal on first launch — wait for ESP8266 to connect and publish |
| ESP8266 shows `rc=-2` in Serial | Cannot reach Mosquitto | Check `MQTT_BROKER` IP in sketch, check firewall rule, check Mosquitto is listening on 1883 |
| ESP8266 connects then disconnects | Keep-alive timeout | Handled by `mqtt.setKeepAlive(120)` in sketch — if persists, restart Mosquitto |
| Mode buttons do not highlight | Variable shadowing bug | Ensure `Button` type keyword is not used when binding `btnStream`, `btnBurst`, `btnAverage` in `onCreate` |
| `netstat` shows `TIME_WAIT` on 1883 | Previous connection still closing | Wait 30 seconds then restart Mosquitto |
| Mosquitto starts then stops immediately | Config file error | Run `mosquitto.exe -v -c mosquitto.conf` directly to see the error |
| No data on real phone but emulator works | Phone not on same Wi-Fi as PC | Connect phone to same router — not mobile data, not guest Wi-Fi, no VPN |
| Mosquitto error: Invalid bridge configuration | Wrong config option | Remove everything except `listener 1883` and `allow_anonymous true` from config |

---

## 8. Security Notes

> ⚠️ The current Mosquitto config uses `allow_anonymous true` which means anyone on your local network can connect without authentication. This is intentional for development only.

When working on a public or shared network:

```cmd
net stop mosquitto
```

To disable auto-start permanently:

```cmd
sc config mosquitto start= disabled
```

To re-enable when needed:

```cmd
sc config mosquitto start= auto
net start mosquitto
```

For a production deployment, add username/password authentication to `mosquitto.conf` and update both the Android app and Arduino sketch accordingly.

---

## 9. Hardware Reference

| Sensor | Module | Connection | MQTT Topic | Status |
|--------|--------|-----------|------------|--------|
| Microphone | KY-037 | `A → A0`, `G → GND`, `+ → 3V3` | `Sensor/Mic` | ✅ Active |
| Accelerometer + Gyro | MPU-6050 | `SDA → D2`, `SCL → D1`, `VCC → 3V3`, `GND → GND` | `Sensor/Bewegung` + `Sensor/Gyro` | 🔧 Stub ready — awaiting hardware |
| Hall Effect / Magnet | A3144 | `OUT → D5`, `VCC → 3V3`, `GND → GND` | `Sensor/Magnet` | 🔧 Stub ready — awaiting hardware |

---

*BioT Speech IoT App — Developer Guide | FHDW Hannover IoT 2025-26*