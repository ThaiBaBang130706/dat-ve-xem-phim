"""Smoke test for the packaged server using actual TCP sockets and HTTP."""
import argparse
import json
import socket
import time
import urllib.request
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--tcp-port", type=int, default=15000)
parser.add_argument("--http-port", type=int, default=15001)
parser.add_argument("--key-file", default="target/smoke/dashboard.key")
args = parser.parse_args()


class Client:
    def __init__(self):
        self.socket = socket.create_connection(("127.0.0.1", args.tcp_port), timeout=10)
        self.reader = self.socket.makefile("r", encoding="utf-8")
        self.token = None
        self.sequence = 0

    def request(self, kind, data):
        self.sequence += 1
        rid = "smoke-" + str(self.sequence)
        frame = {"id": rid, "type": kind, "token": self.token, "data": data}
        self.socket.sendall((json.dumps(frame, ensure_ascii=False) + "\n").encode("utf-8"))
        while True:
            line = self.reader.readline()
            if not line:
                raise AssertionError("Server closed the connection")
            response = json.loads(line)
            if response["id"] == rid:
                if kind == "LOGIN" and response["success"]:
                    self.token = response["data"]["token"]
                return response

    def close(self):
        self.reader.close()
        self.socket.close()


for attempt in range(100):
    try:
        probe = socket.create_connection(("127.0.0.1", args.tcp_port), timeout=0.5)
        probe.close()
        break
    except OSError:
        time.sleep(0.2)
else:
    raise SystemExit("Packaged Java server did not start within 20 seconds")

a, b = Client(), Client()
try:
    assert a.request("LOGIN", {"username": "user1", "password": "User@1234"})["success"]
    assert b.request("LOGIN", {"username": "user2", "password": "User@1234"})["success"]
    show = a.request("GET_SHOWTIMES", {})["data"][0]["id"]
    selection = {"showId": show, "seats": ["B7", "B8"]}
    assert a.request("HOLD_SEATS", selection)["success"]
    assert not b.request("HOLD_SEATS", selection)["success"]
    payment = {**selection, "requestId": "packaged-smoke-001"}
    first = a.request("CONFIRM_BOOKING", payment)
    retry = a.request("CONFIRM_BOOKING", payment)
    assert first["success"] and retry["success"]
    assert first["data"]["id"] == retry["data"]["id"]
    assert len(first["data"]["tickets"]) == 2
    key = Path(args.key_file).read_text().strip()
    request = urllib.request.Request(
        "http://127.0.0.1:" + str(args.http_port) + "/api/stats",
        headers={"Authorization": "Bearer " + key},
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        stats = json.load(response)
        assert stats["connectedClients"] >= 2
        assert stats["revenue"] >= first["data"]["total_vnd"]
    assert a.request("CANCEL_BOOKING", {"bookingId": first["data"]["id"]})["success"]
    assert b.request("HOLD_SEATS", selection)["success"]
    print("Packaged server OK: TCP booking/conflict/retry/cancellation and HTTP stats.")
finally:
    a.close()
    b.close()
