"""
logFileCompare.py — Vergleicht das Mockup-Logfile mit dem BioT-Logfile.

Ergebnisse werden in compare_results.log gesichert (eine kompakte Zeile pro
Topic und Lauf, Datei wird im Append-Modus geschrieben).

Aufruf:
  python logFileCompare.py [mockup.log] [biot.log] [toleranz]

Standardwerte:
  mockup.log   ->  <skript-ordner>/mockup.log
  biot.log     ->  <skript-ordner>/biot.log
  toleranz     ->  0.001
"""

import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional


# ─────────────────────────────────────────────────────────────────────────────
# AUSGABE-DATEI
# ─────────────────────────────────────────────────────────────────────────────
SCRIPT_DIR   = Path(__file__).parent
RESULTS_FILE = SCRIPT_DIR / "compare_results.log"

HEADER = (
    "# run_time            | topic                | "
    "sent  | recv  | matched | lost | extra | mismatch | "
    "match%  | lat_avg_ms | lat_min_ms | lat_max_ms | "
    "drate_log | cfg_rate | qos\n"
)


# ─────────────────────────────────────────────────────────────────────────────
# DATENSTRUKTUREN
# ─────────────────────────────────────────────────────────────────────────────
@dataclass
class SensorMessage:
    timestamp: datetime
    topic:     str
    payload:   str
    values:    list
    source:    str          # "mockup" | "biot"

    def __repr__(self):
        return (f"[{self.source}] {self.timestamp.strftime('%H:%M:%S')} "
                f"| {self.topic} = '{self.payload}'")


# ─────────────────────────────────────────────────────────────────────────────
# QoS AUS sensor_mockup.py LESEN
# ─────────────────────────────────────────────────────────────────────────────
def read_mockup_config(mockup_script: Path) -> tuple:
    """
    Liest aus sensor_mockup.py:
      - QoS je Topic  -> {topic: qos_str}
      - PUBLISH_INTERVAL -> float oder None

    Gibt (qos_map, publish_interval) zurueck.
    PUBLISH_INTERVAL entspricht der konfigurierten Veroeffentlichungsrate;
    die tatsaechliche Datenrate je Topic wird zusaetzlich aus dem Log berechnet.
    """
    qos_map          = {}
    publish_interval = None

    if not mockup_script.exists():
        return qos_map, publish_interval

    try:
        txt = mockup_script.read_text(encoding="utf-8", errors="replace")

        # PUBLISH_INTERVAL = <float>
        pi_match = re.search(r'PUBLISH_INTERVAL\s*=\s*([0-9]*\.?[0-9]+)', txt)
        if pi_match:
            publish_interval = float(pi_match.group(1))

        # "topic": "...", ... "qos": <int>  innerhalb jedes Sensor-Blocks
        block_re = re.compile(
            r'"topic"\s*:\s*"([^"]+)".*?"qos"\s*:\s*(\d+)',
            re.DOTALL,
        )
        for m in block_re.finditer(txt):
            qos_map[m.group(1)] = m.group(2)
    except Exception:
        pass

    return qos_map, publish_interval


# Rueckwaertskompatibilitaet
def read_qos_map(mockup_script: Path) -> dict:
    qos_map, _ = read_mockup_config(mockup_script)
    return qos_map


# ─────────────────────────────────────────────────────────────────────────────
# LOG-PARSER
# ─────────────────────────────────────────────────────────────────────────────
TS_RE  = r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})"
TS_FMT = "%Y-%m-%d %H:%M:%S"

MOCKUP_RE = re.compile(
    TS_RE + r"\s*\|\s*\S+\s*\|\s*mockup\.publisher\s*\|\s*"
    r"MQTT publish:\s+([^\s=]+)\s*=\s*'([^']+)'"
)
BIOT_RE = re.compile(
    TS_RE + r"\s*\|\s*\S+\s*\|\s*biot\.mcp_server\.mqtt_subscriber\s*\|\s*"
    r"MQTT message:\s+([^\s=]+)\s*=\s*'([^']+)'"
)


def _parse_values(payload: str) -> list:
    try:
        return [float(v) for v in payload.split(",")]
    except ValueError:
        return []


def parse_log(path: Path, pattern: re.Pattern, source: str) -> list:
    messages = []
    bad = 0
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = pattern.search(line)
            if not m:
                continue
            ts_str, topic, payload = m.group(1), m.group(2), m.group(3)
            try:
                ts = datetime.strptime(ts_str, TS_FMT)
            except ValueError:
                bad += 1
                continue
            messages.append(SensorMessage(
                timestamp=ts,
                topic=topic.strip(),
                payload=payload.strip(),
                values=_parse_values(payload.strip()),
                source=source,
            ))
    if bad:
        print(f"  !  {bad} Zeile(n) in '{path.name}' uebersprungen.")
    return messages


def parse_log_files(paths: list, pattern: re.Pattern, source: str) -> list:
    """Parst mehrere Log-Dateien und gibt alle Nachrichten chronologisch sortiert zurueck."""
    all_messages = []
    for p in paths:
        msgs = parse_log(p, pattern, source)
        all_messages.extend(msgs)
        print(f"    {p.name:<20}  ->  {len(msgs)} Nachrichten")
    # Chronologisch sortieren (wichtig bei Log-Rotation)
    all_messages.sort(key=lambda m: m.timestamp)
    return all_messages


# ─────────────────────────────────────────────────────────────────────────────
# LOG-ROTATION: alle biot.log* Dateien einsammeln
# ─────────────────────────────────────────────────────────────────────────────
def collect_rotated_logs(base_path: Path) -> list:
    """
    Gibt alle vorhandenen Log-Dateien fuer base_path zurueck,
    chronologisch sortiert (aelteste zuerst).

    Beispiel fuer base_path = /pfad/biot.log:
      biot.log.3  (aelteste)
      biot.log.2
      biot.log.1
      biot.log    (aktuellste)
    """
    parent = base_path.parent
    name   = base_path.name          # z.B. "biot.log"

    # Alle Dateien mit diesem Basisnamen finden
    rotated = []
    for f in parent.iterdir():
        fname = f.name
        if fname == name:
            rotated.append((0, f))   # aktuelle Datei hat Index 0
        elif fname.startswith(name + "."):
            suffix = fname[len(name) + 1:]
            try:
                rotated.append((int(suffix), f))   # .1 .2 .3 ...
            except ValueError:
                pass                 # z.B. biot.log.gz ignorieren

    # Hoehere Nummer = aeltere Datei -> absteigend sortieren = aelteste zuerst
    rotated.sort(key=lambda x: x[0], reverse=True)
    return [f for _, f in rotated]


# ─────────────────────────────────────────────────────────────────────────────
# DATENRATE BERECHNEN  (Nachrichten/Sekunde, pro Topic, aus Mockup-Log)
# ─────────────────────────────────────────────────────────────────────────────
def calc_data_rate(messages: list, topic: str) -> Optional[float]:
    msgs = [m for m in messages if m.topic == topic]
    if len(msgs) < 2:
        return None
    span = (msgs[-1].timestamp - msgs[0].timestamp).total_seconds()
    return len(msgs) / span if span > 0 else None


# ─────────────────────────────────────────────────────────────────────────────
# NACHRICHTEN-MATCHING  (FIFO-Greedy innerhalb Toleranz)
# ─────────────────────────────────────────────────────────────────────────────
def values_close(a: list, b: list, tol: float) -> bool:
    if len(a) != len(b):
        return False
    return all(abs(x - y) <= tol for x, y in zip(a, b))


def match_messages(sent: list, received: list, topic: str, tol: float) -> dict:
    sent_q         = list(sent)
    matched        = []
    unmatched_recv = []

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
        "topic":          topic,
        "matched":        matched,
        "unmatched_sent": sent_q,
        "unmatched_recv": unmatched_recv,
    }


# ─────────────────────────────────────────────────────────────────────────────
# HILFS-FUNKTIONEN
# ─────────────────────────────────────────────────────────────────────────────
def _sep(char="-", width=78):
    print(char * width)


def _fv(v: Optional[float], fmt=".1f") -> str:
    return f"{v:{fmt}}" if v is not None else "?"


def _topic_stats(r: dict, sent_all: list, qos_map: dict, publish_interval: Optional[float] = None):
    """Berechnet alle Kennzahlen fuer ein Topic-Ergebnis."""
    topic   = r["topic"]
    matched = r["matched"]
    n_m     = len(matched)
    n_us    = len(r["unmatched_sent"])
    n_ur    = len(r["unmatched_recv"])
    n_pm    = sum(1 for m in matched if not m["payload_ok"])
    total   = n_m + n_us + n_ur
    match_r = n_m / total * 100 if total > 0 else 0.0

    lats    = [m["latency_ms"] for m in matched]
    avg_lat = sum(lats) / len(lats) if lats else None
    min_lat = min(lats) if lats else None
    max_lat = max(lats) if lats else None

    drate   = calc_data_rate(sent_all, topic)
    qos     = qos_map.get(topic, "?")

    cfg_rate = (1.0 / publish_interval) if publish_interval else None
    return dict(
        topic=topic, n_m=n_m, n_us=n_us, n_ur=n_ur, n_pm=n_pm,
        total=total, match_r=match_r,
        avg_lat=avg_lat, min_lat=min_lat, max_lat=max_lat,
        drate=drate, cfg_rate=cfg_rate, qos=qos,
        sent_n=n_m + n_us, recv_n=n_m + n_ur,
        matched_list=matched,
        unmatched_sent=r["unmatched_sent"],
        unmatched_recv=r["unmatched_recv"],
    )


# ─────────────────────────────────────────────────────────────────────────────
# KONSOLEN-REPORT
# ─────────────────────────────────────────────────────────────────────────────
def console_report(stats_list: list, tol: float):
    _sep("=")
    print("  BioT Logfile-Vergleich  --  Mockup vs. Empfangene MQTT-Nachrichten")
    _sep("=")

    total_m = total_us = total_ur = total_pm = 0

    for s in stats_list:
        total_m  += s["n_m"];  total_us += s["n_us"]
        total_ur += s["n_ur"]; total_pm += s["n_pm"]

        _sep()
        print(f"  Topic                : {s['topic']}")
        print(f"  QoS                  : {s['qos']}")
        cfg_str = f"{_fv(s['cfg_rate'], '.1f')} msg/s  (1 / PUBLISH_INTERVAL)" if s['cfg_rate'] else 'n/a'
        log_str = f"{_fv(s['drate'], '.1f')} msg/s  (aus Log gemessen)" if s['drate'] else 'n/a'
        print(f"  Datenrate konfiguriert: {cfg_str}")
        print(f"  Datenrate gemessen    : {log_str}")
        print(f"  Gesendet (Mockup)    : {s['sent_n']:>6}")
        print(f"  Empfangen (BioT)     : {s['recv_n']:>6}")
        print(f"  Gematcht             : {s['n_m']:>6}")
        print(f"  Nur gesendet (lost)  : {s['n_us']:>6}  <- im BioT-Log nicht gefunden")
        print(f"  Nur empfangen (extra): {s['n_ur']:>6}  <- kein Mockup-Pendant")
        print(f"  Payload-Abweichungen : {s['n_pm']:>6}")
        print(f"  Match-Rate           : {s['match_r']:>6.1f} %")
        if s["avg_lat"] is not None:
            print(f"  Latenz avg/min/max   : "
                  f"{s['avg_lat']:+.0f} / {s['min_lat']:+.0f} / {s['max_lat']:+.0f}  ms")

        if s["n_pm"]:
            print(f"\n  -- Payload-Abweichungen (max. 10) --")
            shown = 0
            for m in s["matched_list"]:
                if not m["payload_ok"] and shown < 10:
                    print(f"    Sent : {m['sent']}")
                    print(f"    Recv : {m['received']}")
                    shown += 1
        if s["n_us"]:
            print(f"\n  -- Nur gesendet (max. 10) --")
            for msg in s["unmatched_sent"][:10]:
                print(f"    {msg}")
        if s["n_ur"]:
            print(f"\n  -- Nur empfangen (max. 10) --")
            for msg in s["unmatched_recv"][:10]:
                print(f"    {msg}")

    total  = total_m + total_us + total_ur
    rate   = total_m / total * 100 if total > 0 else 0.0
    _sep("=")
    print("  GESAMT")
    _sep()
    print(f"  Gematcht             : {total_m}")
    print(f"  Nur gesendet (lost)  : {total_us}")
    print(f"  Nur empfangen (extra): {total_ur}")
    print(f"  Payload-Abweichungen : {total_pm}")
    print(f"  Match-Rate           : {rate:.1f} %")
    print(f"  Toleranz             : +/- {tol}")
    _sep("=")


# ─────────────────────────────────────────────────────────────────────────────
# DATEI-AUSGABE  (kompakte Zeilen, Append-Modus)
# ─────────────────────────────────────────────────────────────────────────────
def save_results(stats_list: list, tol: float, run_time: datetime):
    """
    Haengt fuer jeden Topic eine kompakte Zeile an compare_results.log an.

    Spalten (pipe-getrennt):
      run_time | topic | sent | recv | matched | lost | extra | mismatch |
      match% | lat_avg_ms | lat_min_ms | lat_max_ms | drate_msg/s | qos
    """
    write_header = not RESULTS_FILE.exists() or RESULTS_FILE.stat().st_size == 0

    with open(RESULTS_FILE, "a", encoding="utf-8") as fh:
        if write_header:
            fh.write(HEADER)

        run_str = run_time.strftime("%Y-%m-%d %H:%M:%S")

        for s in stats_list:
            line = (
                f"{run_str:<20} | "
                f"{s['topic']:<20} | "
                f"{s['sent_n']:>5} | "
                f"{s['recv_n']:>5} | "
                f"{s['n_m']:>7} | "
                f"{s['n_us']:>4} | "
                f"{s['n_ur']:>5} | "
                f"{s['n_pm']:>8} | "
                f"{s['match_r']:>6.1f}% | "
                f"{_fv(s['avg_lat'], '.0f'):>10} | "
                f"{_fv(s['min_lat'], '.0f'):>10} | "
                f"{_fv(s['max_lat'], '.0f'):>10} | "
                f"{_fv(s['drate'], '.1f'):>9} | "
                f"{_fv(s['cfg_rate'], '.1f'):>8} | "
                f"{s['qos']}\n"
            )
            fh.write(line)

        fh.write(f"# tol={tol}\n")

    print(f"\n  OK  Ergebnisse gesichert -> {RESULTS_FILE}")


# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────
def main():
    script_dir  = Path(__file__).parent
    mockup_path = Path(sys.argv[1]) if len(sys.argv) > 1 else script_dir / "mockup.log"
    biot_path   = Path(sys.argv[2]) if len(sys.argv) > 2 else script_dir / "biot.log"
    tol         = float(sys.argv[3]) if len(sys.argv) > 3 else 1e-3

    # sensor_mockup.py im gleichen Ordner wie die Logs suchen
    mockup_script = mockup_path.parent / "sensor_mockup.py"

    for p in (mockup_path, biot_path):
        if not p.exists():
            print(f"FEHLER: Datei nicht gefunden: {p}")
            sys.exit(1)

    run_time = datetime.now()

    print(f"\nMockup-Log    : {mockup_path}")
    print(f"BioT-Log      : {biot_path}")
    print(f"Mockup-Skript : {mockup_script} "
          f"({'gefunden' if mockup_script.exists() else 'nicht gefunden -- QoS=?'})")
    print(f"Ergebnisdatei : {RESULTS_FILE}")
    print(f"Toleranz      : +/- {tol}\n")

    qos_map, publish_interval = read_mockup_config(mockup_script)
    if qos_map:
        print(f"QoS-Map          : {qos_map}")
    else:
        print("QoS-Map          : nicht ermittelbar -> '?' (bitte manuell ersetzen)")
    if publish_interval is not None:
        print(f"PUBLISH_INTERVAL : {publish_interval} s  -> {1/publish_interval:.1f} msg/s")
    else:
        print("PUBLISH_INTERVAL : nicht ermittelbar")

    print("\nParsing ...")

    # Mockup-Log (keine Rotation erwartet)
    print(f"  Mockup-Log:")
    sent_msgs = parse_log_files([mockup_path], MOCKUP_RE, "mockup")

    # BioT-Log inkl. rotierter Dateien (biot.log.1, .2, .3 ...)
    biot_files = collect_rotated_logs(biot_path)
    if len(biot_files) > 1:
        print(f"  BioT-Log  ({len(biot_files)} Dateien, inkl. Log-Rotation):")
    else:
        print(f"  BioT-Log:")
    recv_msgs = parse_log_files(biot_files, BIOT_RE, "biot")

    print(f"\n  Gesamt Mockup  -> {len(sent_msgs)} Nachrichten")
    print(f"  Gesamt BioT    -> {len(recv_msgs)} Nachrichten\n")

    if not sent_msgs:
        print("Keine Mockup-Nachrichten gefunden -- laeuft sensor_mockup.py mit File-Logging?")
        sys.exit(1)
    if not recv_msgs:
        print("Keine BioT-MQTT-Nachrichten gefunden -- stimmt das Log-Format?")
        sys.exit(1)

    all_topics = sorted({m.topic for m in sent_msgs} | {m.topic for m in recv_msgs})

    results = [
        match_messages(
            [m for m in sent_msgs if m.topic == t],
            [m for m in recv_msgs if m.topic == t],
            t, tol,
        )
        for t in all_topics
    ]

    stats_list = [_topic_stats(r, sent_msgs, qos_map, publish_interval) for r in results]

    console_report(stats_list, tol)
    save_results(stats_list, tol, run_time)


if __name__ == "__main__":
    main()