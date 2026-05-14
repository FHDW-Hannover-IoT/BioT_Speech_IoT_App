"""
logFileCompare.py — Vergleicht das Mockup-Logfile mit dem BioT-Logfile.

Ablauf:
  1. Beide Logfiles werden geparst.
  2. Aus dem Mockup-Log werden alle publizierten Nachrichten extrahiert.
  3. Aus dem BioT-Log werden alle empfangenen MQTT-Nachrichten extrahiert.
  4. Die Nachrichten werden nach Topic zugeordnet und paarweise verglichen.

Aufruf:
  python logFileCompare.py [mockup.log] [biot.log]

Standardwerte:
  mockup.log  →  mockup.log
  biot.log    →  biot.log
"""

import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional


# ─────────────────────────────────────────────────────────────────────────────
# DATENSTRUKTUREN
# ─────────────────────────────────────────────────────────────────────────────
@dataclass
class SensorMessage:
    timestamp: datetime
    topic:     str
    payload:   str          # roher CSV-String, z. B. "0.0064,0.0062,9.8066"
    values:    list[float]
    source:    str          # "mockup" | "biot"

    def __repr__(self):
        return f"[{self.source}] {self.timestamp.strftime('%H:%M:%S')} | {self.topic} = '{self.payload}'"


# ─────────────────────────────────────────────────────────────────────────────
# PARSER
# ─────────────────────────────────────────────────────────────────────────────
# Format beider Logs:
#   YYYY-MM-DD HH:MM:SS | LEVEL    | module | message
TS_RE      = r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})"
TS_FMT     = "%Y-%m-%d %H:%M:%S"

# Mockup-Log-Zeile:
#   2026-05-14 16:53:40 | DEBUG    | mockup.publisher | MQTT publish: Sensor/Bewegung = '0.0064,0.0062,9.8066'
MOCKUP_RE  = re.compile(
    TS_RE + r"\s*\|\s*\S+\s*\|\s*mockup\.publisher\s*\|\s*"
    r"MQTT publish:\s+([^\s=]+)\s*=\s*'([^']+)'"
)

# BioT-Log-Zeile:
#   2026-05-14 16:53:40 | DEBUG    | biot.mcp_server.mqtt_subscriber | MQTT message: Sensor/Bewegung = '0.0064,0.0062,9.8066'
BIOT_RE    = re.compile(
    TS_RE + r"\s*\|\s*\S+\s*\|\s*biot\.mcp_server\.mqtt_subscriber\s*\|\s*"
    r"MQTT message:\s+([^\s=]+)\s*=\s*'([^']+)'"
)


def _parse_values(payload: str) -> list[float]:
    try:
        return [float(v) for v in payload.split(",")]
    except ValueError:
        return []


def parse_log(path: Path, pattern: re.Pattern, source: str) -> list[SensorMessage]:
    messages: list[SensorMessage] = []
    missing_lines = 0
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = pattern.search(line)
            if m:
                ts_str, topic, payload = m.group(1), m.group(2), m.group(3)
                try:
                    ts = datetime.strptime(ts_str, TS_FMT)
                except ValueError:
                    missing_lines += 1
                    continue
                messages.append(SensorMessage(
                    timestamp = ts,
                    topic     = topic.strip(),
                    payload   = payload.strip(),
                    values    = _parse_values(payload.strip()),
                    source    = source,
                ))
            # Andere Zeilen (INFO, ERROR, db-inserts …) werden ignoriert
    if missing_lines:
        print(f"  ⚠  {missing_lines} Zeile(n) in '{path.name}' konnten nicht geparst werden.")
    return messages


# ─────────────────────────────────────────────────────────────────────────────
# VERGLEICH
# ─────────────────────────────────────────────────────────────────────────────
def values_close(a: list[float], b: list[float], tol: float = 1e-3) -> bool:
    """True wenn alle Werte paarweise innerhalb der Toleranz liegen."""
    if len(a) != len(b):
        return False
    return all(abs(x - y) <= tol for x, y in zip(a, b))


def match_messages(
    sent:     list[SensorMessage],
    received: list[SensorMessage],
    topic:    str,
    tol:      float = 1e-3,
) -> dict:
    """
    Ordnet gesendete Nachrichten des Mockups den empfangenen BioT-Nachrichten zu.

    Strategie: FIFO-Greedy — jede empfangene Nachricht wird mit der zeitlich
    nächsten, noch nicht zugeordneten gesendeten Nachricht gematcht,
    wenn Payload und Topic übereinstimmen (innerhalb `tol`).
    """
    sent_q    = list(sent)          # Kopie; wir poppen aus der Front
    matched   = []
    unmatched_recv  = []

    for recv in received:
        found = False
        for i, s in enumerate(sent_q):
            if values_close(s.values, recv.values, tol):
                latency_ms = (recv.timestamp - s.timestamp).total_seconds() * 1000
                matched.append({
                    "sent":       s,
                    "received":   recv,
                    "latency_ms": latency_ms,
                    "payload_ok": s.payload == recv.payload,
                })
                sent_q.pop(i)
                found = True
                break
        if not found:
            unmatched_recv.append(recv)

    return {
        "topic":           topic,
        "matched":         matched,
        "unmatched_sent":  sent_q,          # gesendet, aber nie empfangen
        "unmatched_recv":  unmatched_recv,  # empfangen, aber kein Pendant im Mockup
    }


# ─────────────────────────────────────────────────────────────────────────────
# REPORT
# ─────────────────────────────────────────────────────────────────────────────
def print_separator(char="─", width=78):
    print(char * width)


def format_latency(ms: Optional[float]) -> str:
    if ms is None:
        return "n/a"
    sign = "+" if ms >= 0 else ""
    return f"{sign}{ms:.0f} ms"


def report(results: list[dict], tol: float):
    print_separator("═")
    print("  BioT Logfile-Vergleich — Mockup vs. Empfangene MQTT-Nachrichten")
    print_separator("═")

    total_matched         = 0
    total_unmatched_sent  = 0
    total_unmatched_recv  = 0
    total_payload_mismatch = 0

    for r in results:
        topic             = r["topic"]
        matched           = r["matched"]
        unmatched_sent    = r["unmatched_sent"]
        unmatched_recv    = r["unmatched_recv"]

        n_match           = len(matched)
        n_us              = len(unmatched_sent)
        n_ur              = len(unmatched_recv)
        n_pm              = sum(1 for m in matched if not m["payload_ok"])

        total_matched          += n_match
        total_unmatched_sent   += n_us
        total_unmatched_recv   += n_ur
        total_payload_mismatch += n_pm

        latencies = [m["latency_ms"] for m in matched]
        avg_lat   = sum(latencies) / len(latencies) if latencies else None
        min_lat   = min(latencies) if latencies else None
        max_lat   = max(latencies) if latencies else None

        print_separator()
        print(f"  Topic : {topic}")
        print(f"  Gesendet (Mockup)    : {n_match + n_us:>5}")
        print(f"  Empfangen (BioT)     : {n_match + n_ur:>5}")
        print(f"  Gematcht             : {n_match:>5}")
        print(f"  Nur gesendet         : {n_us:>5}  ← im BioT-Log nicht gefunden")
        print(f"  Nur empfangen        : {n_ur:>5}  ← kein Mockup-Pendant")
        print(f"  Payload-Abweichungen : {n_pm:>5}  (Werte ähnlich, aber nicht exakt)")
        if latencies:
            print(f"  Latenz (∅ / min / max): "
                  f"{format_latency(avg_lat)} / {format_latency(min_lat)} / {format_latency(max_lat)}")

        # Detailausgabe für Mismatches (max. 10 pro Kategorie)
        if n_pm:
            print(f"\n  ── Payload-Abweichungen (max. 10) ──")
            shown = 0
            for m in matched:
                if not m["payload_ok"] and shown < 10:
                    print(f"    Sent : {m['sent']}")
                    print(f"    Recv : {m['received']}")
                    shown += 1

        if n_us:
            print(f"\n  ── Nur gesendet, nie empfangen (max. 10) ──")
            for msg in unmatched_sent[:10]:
                print(f"    {msg}")

        if n_ur:
            print(f"\n  ── Nur empfangen, ohne Mockup-Pendant (max. 10) ──")
            for msg in unmatched_recv[:10]:
                print(f"    {msg}")

    # ── Gesamtstatistik ───────────────────────────────────────────────────────
    print_separator("═")
    print("  GESAMT")
    print_separator()
    print(f"  Gematcht             : {total_matched}")
    print(f"  Nur gesendet         : {total_unmatched_sent}")
    print(f"  Nur empfangen        : {total_unmatched_recv}")
    print(f"  Payload-Abweichungen : {total_payload_mismatch}")

    rate = (total_matched / (total_matched + total_unmatched_sent + total_unmatched_recv) * 100
            if (total_matched + total_unmatched_sent + total_unmatched_recv) > 0 else 0)
    print(f"  Match-Rate           : {rate:.1f} %")
    print(f"  Toleranz             : ± {tol}")
    print_separator("═")


# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────
def main():
    mockup_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("stressTest/mockup.log")
    biot_path   = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("stressTest/biot.log")
    tol         = float(sys.argv[3]) if len(sys.argv) > 3 else 1e-3

    for p in (mockup_path, biot_path):
        if not p.exists():
            print(f"FEHLER: Datei nicht gefunden: {p}")
            sys.exit(1)

    print(f"\nMockup-Log : {mockup_path}")
    print(f"BioT-Log   : {biot_path}")
    print(f"Toleranz   : ± {tol}\n")

    print("Parsing …")
    sent_msgs = parse_log(mockup_path, MOCKUP_RE, "mockup")
    recv_msgs = parse_log(biot_path,   BIOT_RE,   "biot")

    print(f"  Mockup  → {len(sent_msgs)} Nachrichten gefunden")
    print(f"  BioT    → {len(recv_msgs)} Nachrichten gefunden\n")

    if not sent_msgs:
        print("Keine Mockup-Nachrichten gefunden. "
              "Läuft sensor_mockup.py mit aktiviertem File-Logging?")
        sys.exit(1)
    if not recv_msgs:
        print("Keine BioT-MQTT-Nachrichten gefunden. "
              "Stimmt das Log-Format (biot.mcp_server.mqtt_subscriber)?")
        sys.exit(1)

    # Alle vorkommenden Topics ermitteln
    all_topics = sorted({m.topic for m in sent_msgs} | {m.topic for m in recv_msgs})

    results = []
    for topic in all_topics:
        s = [m for m in sent_msgs if m.topic == topic]
        r = [m for m in recv_msgs if m.topic == topic]
        results.append(match_messages(s, r, topic, tol))

    report(results, tol)


if __name__ == "__main__":
    main()