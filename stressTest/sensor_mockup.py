"""
sensor_mockup.py — BioT Sensor Mockup (configurable)
Publishes fake sensor readings to the Mosquitto broker.
Writes a log file in the same format as biot.log for comparison.

Supported modes per axis:
  "sine"       — pure sine wave, amplitude & frequency configurable
  "gauss"      — gaussian noise around a center value
  "sine+gauss" — sine wave with gaussian noise on top
"""

import logging
import math
import random
import threading
import time
import paho.mqtt.client as mqtt

# ─────────────────────────────────────────────────────────────────────────────
# LOGGING SETUP  — writes mockup.log in the same pipe-separated format as biot.log
# ─────────────────────────────────────────────────────────────────────────────
LOG_FILE = "mockup.log"

class PipeSeparatedFormatter(logging.Formatter):
    """Formats log records as:  YYYY-MM-DD HH:MM:SS | LEVEL | logger | message"""
    def format(self, record):
        ts   = self.formatTime(record, "%Y-%m-%d %H:%M:%S")
        lvl  = record.levelname.ljust(8)
        name = record.name
        return f"{ts} | {lvl} | {name} | {record.getMessage()}"

_handler = logging.FileHandler(LOG_FILE, encoding="utf-8")
_handler.setFormatter(PipeSeparatedFormatter())

logging.basicConfig(level=logging.DEBUG, handlers=[_handler,
                                                    logging.StreamHandler()])

pub_log = logging.getLogger("mockup.publisher")

# ─────────────────────────────────────────────────────────────────────────────
# BROKER
# ─────────────────────────────────────────────────────────────────────────────
BROKER_HOST      = "192.168.178.27"
BROKER_PORT      = 1883
PUBLISH_INTERVAL = 0.02   # seconds between publish cycles

# ─────────────────────────────────────────────────────────────────────────────
# SENSOR CONFIGURATION
#
# Each entry in SENSORS defines one logical sensor:
#
#   "topic"  : MQTT topic to publish on
#   "qos"    : MQTT QoS level — 0, 1, or 2 (default 1)
#   "event"  : optional burst config (triggered via keyboard during operation)
#     "duration"   : how long the burst lasts in seconds (default 1.0)
#     "multiplier" : factor applied to amplitude & std during the burst (default 5.0)
#   "axes"   : list of axis definitions, one per value in the CSV payload
#
# Each axis definition:
#   "name"   : label (used in console output only)
#   "mode"   : "sine" | "gauss" | "sine+gauss"
#
#   For "sine" and "sine+gauss":
#     "center"    : baseline offset added to the sine value (default 0)
#     "amplitude" : peak deviation from center (default 1)
#     "freq"      : oscillations per second (default 0.1)
#
#   For "gauss" and "sine+gauss":
#     "center"    : mean of the distribution
#     "std"       : standard deviation (default 0.1)
#
#   For all modes:
#     "min" / "max" : optional hard clamp applied after generation
#     "round"       : decimal places to round to (default 4) 
# ─────────────────────────────────────────────────────────────────────────────
SENSORS = [
    {
        "topic": "Sensor/Bewegung",
        "qos": 0,
        "event": {"duration": 1.5, "multiplier": 10.0},
        "axes": [
            {"name": "ax", "mode": "sine+gauss", "center": 0.0,  "amplitude": 0.02, "freq": 0.2, "std": 0.005},
            {"name": "ay", "mode": "sine+gauss", "center": 0.0,  "amplitude": 0.01, "freq": 0.2, "std": 0.005},
            {"name": "az", "mode": "gauss",       "center": 9.81,                                 "std": 0.01 },
        ],
    },
    {
        "topic": "Sensor/Gyro",
        "qos": 0,
        "event": {"duration": 1.0, "multiplier": 8.0},
        "axes": [
            {"name": "gx", "mode": "gauss", "center": 0.0, "std": 0.5},
            {"name": "gy", "mode": "gauss", "center": 0.0, "std": 0.5},
            {"name": "gz", "mode": "gauss", "center": 0.0, "std": 0.5},
        ],
    },
    {
        "topic": "Sensor/Magnet",
        "qos": 0,
        "event": {"duration": 2.0, "multiplier": 6.0},
        "axes": [
            {"name": "mx", "mode": "gauss", "center":  20.0, "std": 1.0},
            {"name": "my", "mode": "gauss", "center":  -5.0, "std": 1.0},
            {"name": "mz", "mode": "gauss", "center":  40.0, "std": 1.0},
        ],
    },
        # ── Example: add more sensors below ──────────────────────────────────────
    # {
    #     "topic": "Sensor/Temperature",
    #     "qos": 0,
    #     "axes": [
    #         {"name": "temp", "mode": "sine+gauss", "center": 22.0,
    #          "amplitude": 2.0, "freq": 0.05, "std": 0.2,
    #          "min": 15.0, "max": 35.0},
    #     ],
    # }, 
]


# ─────────────────────────────────────────────────────────────────────────────
# VALUE GENERATOR
# ─────────────────────────────────────────────────────────────────────────────
def generate_value(axis: dict, t: float, multiplier: float = 1.0) -> float:
    mode      = axis.get("mode", "gauss")
    center    = axis.get("center", 0.0)
    amplitude = axis.get("amplitude", 1.0) * multiplier
    freq      = axis.get("freq", 0.1)
    std       = axis.get("std", 0.1) * multiplier
    decimals  = axis.get("round", 4)

    if mode == "sine":
        value = center + amplitude * math.sin(2 * math.pi * freq * t)
    elif mode == "gauss":
        value = random.gauss(center, std)
    elif mode == "sine+gauss":
        sine_part  = amplitude * math.sin(2 * math.pi * freq * t)
        noise_part = random.gauss(0.0, std)
        value      = center + sine_part + noise_part
    else:
        raise ValueError(f"Unknown mode '{mode}' for axis '{axis.get('name')}'")

    # Optional hard clamp 
    if "min" in axis:
        value = max(value, axis["min"])
    if "max" in axis:
        value = min(value, axis["max"])

    return round(value, decimals)


# ─────────────────────────────────────────────────────────────────────────────
# EVENT SYSTEM
# active_events: sensor_index → unix timestamp when the burst ends 
# ─────────────────────────────────────────────────────────────────────────────
active_events: dict[int, float] = {}

def input_loop():
    keys = "  ".join(f"[{i+1}] {s['topic']}" for i, s in enumerate(SENSORS))
    print(f"Event keys:  {keys}\n")
    while True:
        try:
            raw = input()
        except EOFError:
            break
        try:
            idx = int(raw.strip()) - 1
            if 0 <= idx < len(SENSORS):
                sensor   = SENSORS[idx]
                cfg      = sensor.get("event", {})
                duration = cfg.get("duration", 1.0)
                active_events[idx] = time.time() + duration
                print(f"  ⚡ Event → {sensor['topic']}  ({duration}s)")
            else:
                print(f"  Unknown sensor index '{raw.strip()}' — use 1–{len(SENSORS)}")
        except ValueError:
            pass

threading.Thread(target=input_loop, daemon=True).start()

# ─────────────────────────────────────────────────────────────────────────────
# MQTT SETUP
# ─────────────────────────────────────────────────────────────────────────────
client = mqtt.Client(client_id="biot-sensor-mockup", protocol=mqtt.MQTTv5)
client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
client.loop_start()

print(f"Sensor mockup running → {BROKER_HOST}:{BROKER_PORT}")
print(f"Publishing {len(SENSORS)} sensor(s) every {PUBLISH_INTERVAL}s — Ctrl+C to stop\n")
print(f"Writing log to: {LOG_FILE}\n")

# ─────────────────────────────────────────────────────────────────────────────
# MAIN LOOP
# ─────────────────────────────────────────────────────────────────────────────
t = 0.0
try:
    while True:
        t += PUBLISH_INTERVAL
        log_parts = []

        for idx, sensor in enumerate(SENSORS):
            cfg        = sensor.get("event", {})
            multiplier = 1.0
            if idx in active_events:
                if time.time() < active_events[idx]:
                    multiplier = cfg.get("multiplier", 5.0)
                else:
                    del active_events[idx]

            values  = [generate_value(axis, t, multiplier) for axis in sensor["axes"]]
            payload = ",".join(str(v) for v in values)

            client.publish(sensor["topic"], payload, qos=sensor.get("qos", 1))

            # ── Log in the same format that biot.log uses for received messages ──
            pub_log.debug("MQTT publish: %s = '%s'", sensor["topic"], payload)

            names = [axis["name"] for axis in sensor["axes"]]
            pairs = "  ".join(f"{n}={v}" for n, v in zip(names, values))
            log_parts.append(f"[{sensor['topic']}]  {pairs}")

        print("\n".join(log_parts))
        print()
        time.sleep(PUBLISH_INTERVAL)

except KeyboardInterrupt:
    print("\nStopping mockup.")
    client.loop_stop()
    client.disconnect()