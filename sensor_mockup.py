"""
sensor_mockup.py — BioT Sensor Mockup
Publishes fake MPU-6050 + A3144 readings to the Mosquitto broker.
"""

import math
import random
import time
import paho.mqtt.client as mqtt

BROKER_HOST = "172.21.152.161"   # ← Laptop A IP
BROKER_PORT = 1883
PUBLISH_INTERVAL = 0.5         # seconds between publishes

client = mqtt.Client(client_id="biot-sensor-mockup", protocol=mqtt.MQTTv5)
client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
client.loop_start()

print(f"Sensor mockup running → {BROKER_HOST}:{BROKER_PORT}")
print("Ctrl+C to stop\n")

t = 0.0
try:
    while True:
        t += PUBLISH_INTERVAL

        # Accelerometer: gentle sine wave + noise (units: g)
        ax = round(0.02 * math.sin(t) + random.gauss(0, 0.005), 4)
        ay = round(0.01 * math.cos(t) + random.gauss(0, 0.005), 4)
        az = round(9.81 + random.gauss(0, 0.01), 4)
        client.publish("Sensor/Bewegung", f"{ax},{ay},{az}", qos=1)

        # Gyroscope: slow drift + noise (units: deg/s)
        gx = round(random.gauss(0, 0.5), 4)
        gy = round(random.gauss(0, 0.5), 4)
        gz = round(random.gauss(0, 0.5), 4)
        client.publish("Sensor/Gyro", f"{gx},{gy},{gz}", qos=1)

        # Magnetometer (arbitrary units)
        mx = round(random.gauss(20.0, 1.0), 4)
        my = round(random.gauss(-5.0, 1.0), 4)
        mz = round(random.gauss(40.0, 1.0), 4)
        client.publish("Sensor/Magnet", f"{mx},{my},{mz}", qos=1)

        # Microphone (display-only, not persisted by backend)
        mic = random.randint(100, 400)
        client.publish("Sensor/Mic", str(mic), qos=0)

        print(f"  Accel: {ax},{ay},{az}  Gyro: {gx},{gy},{gz}  Mic: {mic}")
        time.sleep(PUBLISH_INTERVAL)

except KeyboardInterrupt:
    print("\nStopping mockup.")
    client.loop_stop()
    client.disconnect()