# Android IoT Sensor App — Full System Documentation

This document describes:

- How to set up the **MQTT environment** (Mosquitto broker on PC)
- How to run the **Android app on physical phone or emulator**
- What the **application does**
- Architecture and responsibilities of:
  - `MainActivity`
  - `MqttHandler`

This README is intended for development, debugging, and submission documentation.

---

# 1. MQTT Broker Setup (Required Before Running App)

The Android app connects to an **MQTT broker running on your PC**.  
Follow these steps **exactly**.

---

## 1.1 Install Mosquitto Broker (Windows)

Download from the official website:

https://mosquitto.org/download/

Install with default settings.

---

## 1.2 Configure Mosquitto

Edit (or create):
```console
C:\Program Files\mosquitto\mosquitto.conf
```
Add:

```conf
listener 1883
allow_anonymous true
```
This allows your Android phone to connect without username/password.

Restart Mosquitto after saving the file.

## 1.3 Allow Mosquitto in Windows Firewall

Open Windows Firewall → “Allow an app” → enable:

- `mosquitto.exe`
- Port **1883/TCP**

If this is blocked, the app will fail with:
```console
java.net.SocketTimeoutException: failed to connect
```

## 1.4 Find Your PC's LAN IP

Run this in PowerShell or CMD:
```console
ipconfig
```

Look for:
```console
IPv4 Address . . . : 192.168.xxx.xxx
```

Copy this IP.

You must put this IP in your Android code:
```java
private static final String PHONE_BROKER = "tcp://192.168.xxx.xxx:1883";
```

This IP must match your PC’s Wi-Fi network.

## 1.5 Phone and PC Must Be on the Same Network

Your Android phone must be:

- On Wi-Fi (same router as PC)
- NOT on mobile data
- NOT on guest Wi-Fi
- NOT behind VPN

If networks differ → MQTT cannot communicate.

# 2. Running the Android App

The Android app behaves differently depending on where it's running.

## 2.1 Running on an Emulator (Android Studio)

No network setup required.

The app automatically uses:
```console
tcp://10.0.2.2:1883
```

Because the emulator cannot access your PC directly — ```10.0.2.2``` is the gateway.

MQTT will work immediately if Mosquitto is running on your PC.

## 2.2 Running on a Real Physical Phone

Requirements:
1. Mosquitto must be running on PC

2. Port 1883 must be open

3. Phone + PC on same Wi-Fi

4. Correct LAN IP set in PHONE_BROKER

5. No VPN, guest network, or firewall blocking

After launching the app:
- It will connect to the broker

- Subscribe to sensor topics

- Publish test messages

- Display incoming sensor data

- Save it to local Room database

If connection fails, you will see a Toast:
```
“MQTT Fehler: …”
```
And the log may show:
```console
SocketTimeoutException: failed to connect
```
Which always indicates network/firewall/IP mismatch.
## 🧠 3. Application Overview

This IoT application listens for incoming MQTT sensor messages and displays them in real time inside the Android UI.

It uses:

- **MQTT** (Eclipse Paho) for sensor communication  
- **Room Database** for storing incoming sensor values  
- **Android TextViews** for live output of movement, gyro, and time

## Sensor Topics Used

| Topic               | Meaning             | Data Format        |
|---------------------|---------------------|--------------------|
| `Sensor/Bewegung`   | Accelerometer data  | `x,y,z`            |
| `Sensor/Gyro`       | Gyroscope data      | `x,y,z`            |
| `Sensor/Zeit`       | Timestamp string    | `YYYY-MM-DD...`    |

These are the topics that the application automatically subscribes to after a successful MQTT connection.


## 4. MainActivity — Responsibilities

`MainActivity` is the central controller of the application. It manages:

- UI initialization  

- MQTT client initialization  

- Subscribing to all sensor topics  

- Parsing and handling incoming MQTT messages  

- Updating TextViews in real time  

- Writing sensor data into the Room database  

- Publishing retained test values after connecting  

---

## ✔ 4.2 MQTT Connection Workflow

### **Startup Sequence**
1. Generate a unique MQTT client ID  

2. Select the correct broker URL (emulator vs. real device)  

3. Instantiate `MqttHandler`  

4. Register the message listener callback  

5. Connect asynchronously to the broker  

### **On Successful Connection**
- Subscribe to all three sensor topics:
  - `Sensor/Bewegung`
  - `Sensor/Gyro`
  - `Sensor/Zeit`
- Publish retained initial values for testing
- Load historical database entries and log the count

---

## ✔ 4.3 Processing Incoming Messages

Each topic is processed independently:

### **`Sensor/Bewegung`**
- Payload: `x,y,z`  

- Parse into float values (X/Y/Z) 

- Update accelerometer TextView  

- Insert values into the database  

### **`Sensor/Gyro`**
- Payload: `x,y,z`  

- Parse into float values (X/Y/Z)  

- Update gyro TextView  

- Insert values into the database  

### **`Sensor/Zeit`**
- Payload: timestamp string

- Update time TextView  

- Store timestamp in DB  

**All UI modifications run on the main thread using `runOnUiThread()`.**

---

## ✔ 4.4 Database Integration

Two helper methods handle all Room database operations:

### **`storeSensor(ValueSensor sensor)`**
- Runs in a background thread  

- Inserts a new row into the Room DB  

- Logs warnings on failure  

### **`loadDatabaseValues()`**
- Runs in a background thread  

- Fetches all stored `ValueSensor` rows  

- Logs the total number of entries  

---

## 🔌 5. MqttHandler — Responsibilities

`MqttHandler` is a wrapper for the Eclipse Paho asynchronous MQTT client.

It manages:

- Connecting 

- Automatic reconnecting  

- Subscribing  

- Publishing 

- Disconnecting  

- Forwarding incoming messages  

---

## ✔ 5.1 Asynchronous Connect

Features:

- Fully non-blocking  

- `automaticReconnect = true`  

- `cleanSession = true`  

Connection results are delivered via:

- `onConnected()`  

- `onFailure(Throwable)`  

---

## ✔ 5.2 Subscription

- Uses **QoS 1** (“at least once”) 

- Logs both success and failure  

- Safe to call multiple times  

---

## ✔ 5.3 Publishing

Supports:

- Normal transient messages 

- Retained messages (`retained = true`)  

Publishing is performed in a **background thread** to avoid blocking the UI.

---

## ✔ 5.4 Disconnecting

- Gracefully closes the MQTT connection 

- Updates internal `connected` state  

- Runs on a background thread 

- Prevents crashes when the activity is destroyed  

---

## ✔ 5.5 Receiving Messages

Every MQTT message triggers:
```java
onMessageReceived(topic, payload)
```
`MainActivity` uses this callback to:

- Update UI fields  

- Parse sensor payloads  

- Insert data into Room DB  

This is the core mechanism through which MQTT → Android UI → Database communication happens.

## 6. Testing

This section explains how to verify that your MQTT broker, phone, and Android application all communicate correctly.



### 6.1 Publish From PC (Manual Test)

Use `mosquitto_pub` from your computer to send test messages directly into the broker.

Example:

```console
mosquitto_pub -h 192.168.xxx.xxx -t Sensor/Bewegung -m "1.2,5.6,9.0"
```
Expected behavior:

- The app updates the Bewegung TextView

- A new row is inserted into the Room database

- Logcat shows the incoming message

- No crashes or MQTT disconnects occur

Repeat for the other topics:
```console
mosquitto_pub -h 192.168.xxx.xxx -t Sensor/Gyro -m "0.5,1.2,3.4"
mosquitto_pub -h 192.168.xxx.xxx -t Sensor/Zeit -m "2025-01-01T12:00:00Z"
```

### 6.2 Check If the Phone Can Reach the Broker

To verify that your physical Android device can connect to the MQTT broker running on your PC, install any MQTT client app on your phone, such as:

- **MQTT Dash**

- **MQTT Client**

- **MyMQTT**


### 6.3 Test Steps

1. Open the MQTT client app on your phone.  

2. Create a new connection using your PC’s **LAN IP address** (the same one used in `PHONE_BROKER`).  

3. Set the port to: **1883**  

4. Choose protocol: **MQTT (TCP)**  

5. Subscribe to the following topics:
   - `Sensor/Bewegung`
   - `Sensor/Gyro`
   - `Sensor/Zeit`
   
6. Publish test values from the phone app to confirm two-way communication.

Example test messages:

- `Sensor/Bewegung` → `"1.2,4.5,6.7"`
- `Sensor/Gyro` → `"0.1,0.2,0.3"`
- `Sensor/Zeit` → `"2025-01-01T12:00:00Z"`

---

### 6.4 Expected Result

If the phone MQTT app can:

- Connect successfully 

- Subscribe to topics 

- Receive messages  

- Publish to the broker  

**Your network, IP configuration, and Mosquitto setup are correct.**

This also confirms:

- If the phone app connects but the Android application does not,  the issue is inside the Android app code — not the network or broker.


## Extra Debugging Tips

- Make sure your PC firewall allows Inbound TCP 1883

- Ensure your phone and PC are on the same Wi-Fi network

- Test ping:
```console
ping 192.168.xxx.xxx
```

- Restart Mosquitto:
```console
net stop mosquitto
net start mosquitto
```

- If using a laptop, disable VPNs (they rewrite routes)