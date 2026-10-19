"""Simulador ESP32 com outbox SQLite; biblioteca padrão do Python 3.10+."""
import argparse
import datetime as dt
import json
import os
import sqlite3
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

def connect(path):
    db = sqlite3.connect(path, timeout=30)
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA synchronous=FULL")
    db.execute("CREATE TABLE IF NOT EXISTS outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL)")
    db.commit()
    return db

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--operation-id", required=True)
    parser.add_argument("--key-env", default="DEVICE_API_KEY", help="Variável de ambiente com a API Key")
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--rate", type=float, default=5, help="Peças por segundo")
    parser.add_argument("--outbox", type=Path, default=Path("esp32-outbox.sqlite3"))
    args = parser.parse_args()
    if args.count < 0 or args.rate <= 0:
        parser.error("count deve ser não negativo e rate positivo")
    key = os.environ.get(args.key_env)
    if not key:
        parser.error("Configure a chave na variável " + args.key_env)
    done = threading.Event()
    stopped = threading.Event()
    producer_errors = []
    with connect(args.outbox):
        pass

    def produce():
        db = connect(args.outbox)
        try:
            for _ in range(args.count):
                if stopped.is_set():
                    break
                event = {"deviceId": args.device_id, "operationId": args.operation_id,
                         "eventId": str(uuid.uuid4()), "timestamp": dt.datetime.now(dt.timezone.utc).isoformat()}
                db.execute("INSERT INTO outbox(payload) VALUES (?)", (json.dumps(event),))
                db.commit()
                stopped.wait(1 / args.rate)
        except Exception as exc:
            producer_errors.append(str(exc))
        finally:
            db.close()
            done.set()

    thread = threading.Thread(target=produce, daemon=False)
    thread.start()
    db = connect(args.outbox)
    backoff = 0.25
    try:
        while True:
            row = db.execute("SELECT id,payload FROM outbox ORDER BY id LIMIT 1").fetchone()
            if row is None:
                if done.is_set():
                    break
                time.sleep(0.05)
                continue
            item_id, payload = row
            request = urllib.request.Request(args.base_url.rstrip("/") + "/api/production/events",
                data=payload.encode("utf-8"), headers={"Content-Type": "application/json", "X-Device-Key": key}, method="POST")
            try:
                with urllib.request.urlopen(request, timeout=5) as response:
                    answer = json.load(response)
                event = json.loads(payload)
                if not answer.get("accepted") or answer.get("eventId") != event["eventId"]:
                    raise ValueError("Servidor não confirmou o UUID enviado")
                db.execute("DELETE FROM outbox WHERE id=?", (item_id,))
                db.commit()
                print("ACK", answer["eventId"], "count=", answer.get("currentCount"), "duplicate=", answer.get("duplicate"))
                backoff = 0.25
            except urllib.error.HTTPError as exc:
                try:
                    body = json.loads(exc.read().decode("utf-8"))
                except (ValueError, UnicodeError):
                    body = {}
                code = body.get("code", "HTTP_" + str(exc.code))
                permanent = {"BATCH_FINALIZADO", "EVENT_DURING_PAUSE", "EVENT_ID_REUSED", "INVALID_EVENT_TIME"}
                if exc.code in (400,401,403,404) or code in permanent:
                    print("Interrompido:", code, "evento preservado em", args.outbox, file=sys.stderr)
                    return 2
                print("Reenvio pendente:", code, file=sys.stderr)
                time.sleep(backoff)
                backoff = min(10, backoff * 2)
            except (urllib.error.URLError, TimeoutError, ValueError, OSError) as exc:
                print("Sem ACK; fila preservada:", str(exc), file=sys.stderr)
                time.sleep(backoff)
                backoff = min(10, backoff * 2)
        if producer_errors:
            print("Falha de persistência:", producer_errors[0], file=sys.stderr)
            return 2
        print("Fila sincronizada.")
        return 0
    except KeyboardInterrupt:
        print("Interrompido. Eventos persistidos serão reenviados na próxima execução.")
        return 130
    finally:
        stopped.set()
        thread.join()
        db.close()

if __name__ == "__main__":
    sys.exit(main())
