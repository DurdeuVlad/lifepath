"""Minimal Source RCON client — send commands to the running dev server.
Usage: python tools/rcon.py "command" [host] [port] [password]
"""
import socket
import struct
import sys


def packet(req_id, ptype, payload):
    body = struct.pack("<ii", req_id, ptype) + payload.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(body)) + body


def recv_packet(sock):
    raw_len = b""
    while len(raw_len) < 4:
        raw_len += sock.recv(4 - len(raw_len))
    (length,) = struct.unpack("<i", raw_len)
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    req_id, ptype = struct.unpack("<ii", data[:8])
    return req_id, ptype, data[8:-2].decode("utf-8", errors="replace")


def main():
    command = sys.argv[1]
    host = sys.argv[2] if len(sys.argv) > 2 else "127.0.0.1"
    port = int(sys.argv[3]) if len(sys.argv) > 3 else 25575
    password = sys.argv[4] if len(sys.argv) > 4 else "lifepath-test"

    s = socket.create_connection((host, port), timeout=10)
    s.sendall(packet(1, 3, password))
    rid, _, payload = recv_packet(s)
    if rid == -1:
        print("AUTH FAILED", file=sys.stderr)
        sys.exit(1)
    s.sendall(packet(2, 2, command))
    _, _, payload = recv_packet(s)
    s.close()
    print(payload if payload else "(no output)")


if __name__ == "__main__":
    main()
