# NeoForge lab proof client - MC 1.21.1 (protocol 767), offline mode.
# Adapted from the RC2 clone's petty_bot.py NeoForge negotiation.
# Joins localhost:25566, completes the NeoForge modded-network handshake by
# echoing the server's channel declarations, then holds the connection and
# executes file-driven commands (drop *.txt into tools/bot/nfcmd/):
#   dig <x> <y> <z>        start+stop destroy block (creative = instant break)
#   place <x> <y> <z>      use_item_on against that block face=up (held item)
#   use                    activate held item (use_item)
#   swing                  arm animation
#   slot <0-8>             held hotbar slot
#   cmd <command>          unsigned chat_command (no leading slash)
#   interact <entityId>    use_entity interact (hand=main)
#   attack <entityId>      use_entity attack
#   pos                    print last known position
#   respawn                client_command respawn
# Decodes action_bar/system_chat text for gate-denial evidence.
import socket
import struct
import zlib
import sys
import threading
import time
import os

HOST = "127.0.0.1"
PORT = int(os.environ.get("BOT_PORT", "25566"))
NAME = os.environ.get("BOT_NAME", "LPProof")
CMDDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "nfcmd")
os.makedirs(CMDDIR, exist_ok=True)
LOG = open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
           "nf_lab_bot.log"), "a", buffering=1, encoding="utf-8")
cur_pos = [None]


def log(msg):
    line = f"[{time.strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    LOG.write(line + "\n")


def vwrite(i):
    out = bytearray()
    i &= 0xFFFFFFFF
    while True:
        b = i & 0x7F
        i >>= 7
        if i:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def vread_buf(buf, pos):
    val = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        val |= (b & 0x7F) << shift
        if not (b & 0x80):
            return val, pos
        shift += 7
        if shift > 35:
            raise ValueError("varint too long")


def vread_sock(sock):
    val = 0
    shift = 0
    while True:
        b = sock.recv(1)
        if not b:
            raise ConnectionError("eof")
        b = b[0]
        val |= (b & 0x7F) << shift
        if not (b & 0x80):
            return val
        shift += 7


def pack_pos(x, y, z):
    v = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return struct.pack(">q", v - (1 << 64) if v >= (1 << 63) else v)


class Conn:
    def __init__(self, sock):
        self.s = sock
        self.s.settimeout(120)
        self.threshold = -1

    def read_packet(self):
        total = vread_sock(self.s)
        raw = b""
        while len(raw) < total:
            chunk = self.s.recv(total - len(raw))
            if not chunk:
                raise ConnectionError("eof mid-packet")
            raw += chunk
        if self.threshold >= 0:
            dlen, pos = vread_buf(raw, 0)
            payload = raw[pos:]
            if dlen != 0:
                payload = zlib.decompress(payload)
            raw = payload
        pid, pos = vread_buf(raw, 0)
        return pid, raw[pos:]

    def send_packet(self, pid, data=b""):
        body = vwrite(pid) + data
        if self.threshold >= 0:
            if len(body) >= self.threshold:
                comp = zlib.compress(body)
                frame = vwrite(len(body)) + comp
            else:
                frame = vwrite(0) + body
            self.s.sendall(vwrite(len(frame)) + frame)
        else:
            self.s.sendall(vwrite(len(body)) + body)


def str_field(s):
    b = s.encode("utf-8")
    return vwrite(len(b)) + b


# PacketFlow ordinals: SERVERBOUND=0, CLIENTBOUND=1
FLOW = {"SERVERBOUND": 0, "CLIENTBOUND": 1}

# Channels this client declares to the server during negotiation.
# [id, flow, version]  - lifepath play payloads register with version "1".
DECLARE_PLAY = [
    ("lifepath:ability/activate", "SERVERBOUND", "1"),
    ("lifepath:ability/highlight", "CLIENTBOUND", "1"),
    ("lifepath:feedback", "CLIENTBOUND", "1"),
    ("lifepath:selection/catalog", "CLIENTBOUND", "1"),
    ("lifepath:selection/catalog_request", "SERVERBOUND", "1"),
    ("lifepath:selection/morph_form", "SERVERBOUND", "1"),
    ("lifepath:selection/specialization", "SERVERBOUND", "1"),
    ("lifepath:selection/species", "SERVERBOUND", "1"),
    ("lifepath:sync/character", "CLIENTBOUND", "1"),
    ("lifepath:sync/cooldown", "CLIENTBOUND", "1"),
    ("lifepath:sync/identity", "CLIENTBOUND", "1"),
    ("lifepath:sync/resource", "CLIENTBOUND", "1"),
    ("lifepath:sync/skills", "CLIENTBOUND", "1"),
    ("enchantmentlibrary:ench_selection", "SERVERBOUND", "enchantmentlibrary"),
    ("timber_tales:camera_shake", "CLIENTBOUND", "1"),
]

# Optional extra channel declarations loaded from channels_extra.json if present
# (lets us add server-required channels from other mods without a redeploy).


def build_query(cfg_channels, play_channels):
    """ModdedNetworkQueryPayload: {CONFIGURATION: {...}, PLAY: {...}}"""
    def comp(ch, flow, ver):
        b = str_field(ch) + str_field(ver)
        if flow is None:
            b += b"\x00"
        else:
            b += b"\x01" + vwrite(FLOW[flow])
        return b + b"\x00"  # optional=false

    entries = [(4, cfg_channels), (1, play_channels)]
    body = vwrite(sum(1 for _, m in entries if m))
    for proto, channels in entries:
        if not channels:
            continue
        body += vwrite(proto) + vwrite(len(channels))
        for ch, flow, ver in channels:
            body += comp(ch, flow, ver)
    return body


def load_extra(fname="channels_extra.json"):
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), fname)
    try:
        import json
        return [tuple(e) for e in json.load(open(p))]
    except Exception:
        return []


def describe(data):
    txt = data.decode("utf-8", errors="ignore")
    return "".join(c for c in txt if 32 <= ord(c) < 127 or c in "\n")[:300]


def extract_text(data):
    """Pull every printable run >=4 chars out of a chat/NBT blob."""
    runs, cur = [], []
    for b in data:
        if 32 <= b < 127:
            cur.append(chr(b))
        else:
            if len(cur) >= 4:
                runs.append("".join(cur))
            cur = []
    if len(cur) >= 4:
        runs.append("".join(cur))
    return " | ".join(runs)


def cmd_loop(c, phase_ref):
    while True:
        try:
            files = sorted(f for f in os.listdir(CMDDIR) if f.endswith(".txt"))
        except OSError:
            files = []
        for f in files:
            fp = os.path.join(CMDDIR, f)
            try:
                lines = open(fp, encoding="utf-8").read().splitlines()
                os.unlink(fp)
            except OSError:
                continue
            for line in lines:
                line = line.strip()
                if line:
                    run_cmd(c, line)


def run_cmd(c, line):
    global cur_pos
    parts = line.split()
    op = parts[0]
    try:
        if op == "dig":
            x, y, z = (int(float(p)) for p in parts[1:4])
            wait = float(parts[4]) if len(parts) > 4 else 1.2
            # status 0 = start destroy, face up, seq increments
            c.send_packet(0x24, vwrite(0) + pack_pos(x, y, z) + b"\x01" + vwrite(0))
            time.sleep(wait)
            c.send_packet(0x24, vwrite(2) + pack_pos(x, y, z) + b"\x01" + vwrite(1))
            log(f"DIG sent {x} {y} {z} wait={wait}")
        elif op == "place":
            x, y, z = (int(float(p)) for p in parts[1:4])
            face = int(parts[4]) if len(parts) > 4 else 1
            # use_item_on: hand, pos, direction, cursor xyz, inside, seq
            body = (vwrite(0) + pack_pos(x, y, z) + vwrite(face)
                    + struct.pack(">fff", 0.5, 1.0, 0.5) + b"\x00" + vwrite(2))
            c.send_packet(0x38, body)
            log(f"PLACE sent at {x} {y} {z} face={face}")
        elif op == "use":
            yaw, pitch = 0.0, 0.0
            if cur_pos[0]:
                yaw, pitch = cur_pos[0][3], cur_pos[0][4]
            c.send_packet(0x39, vwrite(0) + vwrite(3)
                          + struct.pack(">ff", yaw, pitch))
            log("USE sent")
        elif op == "swing":
            c.send_packet(0x36, vwrite(0))
            log("SWING sent")
        elif op == "slot":
            c.send_packet(0x2F, struct.pack(">h", int(parts[1])))
            log(f"SLOT {parts[1]}")
        elif op == "cmd":
            text = " ".join(parts[1:])
            c.send_packet(0x04, str_field(text))
            log(f"CMD {text}")
        elif op == "interact":
            c.send_packet(0x16, vwrite(int(parts[1])) + vwrite(0)
                          + vwrite(0) + b"\x00")
            log(f"INTERACT entity {parts[1]}")
        elif op == "attack":
            c.send_packet(0x16, vwrite(int(parts[1])) + vwrite(1) + b"\x00")
            log(f"ATTACK entity {parts[1]}")
        elif op == "pos":
            log(f"POS {cur_pos[0]}")
        elif op == "respawn":
            c.send_packet(0x09, vwrite(0))
            log("RESPAWN sent")
        else:
            log(f"UNKNOWN {op}")
    except Exception as e:
        log(f"CMD ERROR {line}: {e}")


def run():
    s = socket.create_connection((HOST, PORT), timeout=15)
    c = Conn(s)
    c.send_packet(0x00, vwrite(767) + str_field(HOST)
                  + struct.pack(">H", PORT) + vwrite(2))
    c.send_packet(0x00, str_field(NAME) + bytes(16))
    log(f"handshake+login sent name={NAME} port={PORT}")

    phase = "login"
    cmdthread = threading.Thread(target=cmd_loop, args=(c, None), daemon=True)
    cmdthread.start()
    deadline = time.time() + 7200
    last_move = 0.0

    while time.time() < deadline:
        try:
            pid, data = c.read_packet()
        except ConnectionError as e:
            log(f"socket closed: {e}")
            return

        if phase == "login":
            if pid == 0x00:
                log(f"LOGIN DISCONNECT: {describe(data)}")
                return
            if pid == 0x02:
                log("login success -> config phase")
                c.send_packet(0x03)
                phase = "config"
                ci = (str_field("en_us") + b"\x0a" + vwrite(0) + b"\x01"
                      + b"\x7f" + vwrite(1) + b"\x00" + b"\x01")
                c.send_packet(0x00, ci)
            elif pid == 0x03:
                thr, _ = vread_buf(data, 0)
                c.threshold = thr
                log(f"set compression threshold={thr}")
            elif pid == 0x04:
                _, pos = vread_buf(data, 0)
                msgid, pos = vread_buf(data, pos)
                c.send_packet(0x02, vwrite(msgid) + b"\x00")
            else:
                log(f"login cb 0x{pid:02x} len={len(data)}")
            continue

        if phase == "config":
            if pid in (0x00, 0x02):
                log(f"CONFIG DISCONNECT 0x{pid:02x}: {describe(data)}")
                log(f"  hex={data[:200].hex()}")
                return
            if pid == 0x01:
                try:
                    n, pos = vread_buf(data, 0)
                    ident = data[pos:pos + n].decode("utf-8")
                    rest = data[pos + n:]
                    log(f"config payload {ident} len={len(rest)}")
                    if ident == "minecraft:register":
                        c.send_packet(0x02, str_field("minecraft:register") + rest)
                        log(f"-> echoed minecraft:register ({len(rest)}b)")
                    elif ident == "neoforge:register":
                        play = DECLARE_PLAY + load_extra()
                        c.send_packet(0x02, str_field("neoforge:register")
                                      + build_query([], play))
                        log(f"-> answered neoforge:register "
                            f"({len(play)} play channels)")
                    elif ident == "neoforge:modded_network_setup_failed":
                        fn = os.path.join(os.path.dirname(
                            os.path.abspath(__file__)), "setup_failed.bin")
                        open(fn, "wb").write(rest)
                        log(f"   setup_failed dumped ({len(rest)}b) "
                            f"{extract_text(rest)[:400]}")
                    elif ident == "neoforge:known_registry_data_maps":
                        n2, p2 = vread_buf(rest, 0)
                        out = vwrite(n2)
                        for _ in range(n2):
                            rk, p2 = vread_buf(rest, p2)
                            rkname = rest[p2:p2 + rk]
                            p2 += rk
                            cnt, p2 = vread_buf(rest, p2)
                            out += vwrite(rk) + rkname + vwrite(cnt)
                            for _ in range(cnt):
                                il, p2 = vread_buf(rest, p2)
                                out += vwrite(il) + rest[p2:p2 + il]
                                p2 += il + 1
                        c.send_packet(0x02, str_field(
                            "neoforge:known_registry_data_maps_reply") + out)
                        log(f"-> answered known_registry_data_maps ({n2})")
                    elif ident == "neoforge:frozen_registry_sync_completed":
                        c.send_packet(0x02, str_field(
                            "neoforge:frozen_registry_sync_completed"))
                        log("-> acked frozen_registry_sync_completed")
                    elif ident == "neoforge:extensible_enum_data":
                        c.send_packet(0x02, str_field("neoforge:extensible_enum_ack"))
                        log("-> acked extensible_enum_data")
                    elif ident == "neoforge:feature_flags":
                        c.send_packet(0x02, str_field("neoforge:feature_flags_ack"))
                        log("-> acked feature_flags")
                    elif ident == "c:version":
                        c.send_packet(0x02, str_field("c:version") + rest)
                        log("-> echoed c:version")
                    elif ident == "c:register":
                        try:
                            _v, p3 = vread_buf(rest, 0)
                            pl, p3 = vread_buf(rest, p3)
                            proto = rest[p3:p3 + pl].decode()
                            p3 += pl
                            cnt, p3 = vread_buf(rest, p3)
                            ids = []
                            for _ in range(cnt):
                                sl, p3 = vread_buf(rest, p3)
                                ids.append(rest[p3:p3 + sl].decode())
                                p3 += sl
                            log(f"   c:register proto={proto} declared {cnt}")
                            builtins = [
                                "minecraft:register", "minecraft:unregister",
                                "minecraft:brand", "neoforge:register",
                                "neoforge:network",
                                "neoforge:modded_network_setup_failed",
                                "neoforge:known_registry_data_maps",
                                "neoforge:known_registry_data_maps_reply",
                                "neoforge:frozen_registry_sync_start",
                                "neoforge:frozen_registry",
                                "neoforge:frozen_registry_sync_completed",
                                "neoforge:registry_data_map_sync",
                                "neoforge:extensible_enum_data",
                                "neoforge:extensible_enum_ack",
                                "neoforge:feature_flags",
                                "neoforge:feature_flags_ack",
                                "neoforge:advanced_add_entity",
                                "neoforge:advanced_container_set_data",
                                "neoforge:advanced_open_screen",
                                "neoforge:auxiliary_light_data",
                                "neoforge:config_file",
                                "neoforge:custom_time_packet",
                                "neoforge:split", "neoforge:sync_attachments",
                                "c:version", "c:register", "c:connection_type",
                            ]
                            lp = [
                                "lifepath:sync/character", "lifepath:sync/cooldown",
                                "lifepath:feedback", "lifepath:sync/identity",
                                "lifepath:sync/resource", "lifepath:sync/skills",
                                "lifepath:selection/catalog",
                                "lifepath:selection/catalog_request",
                                "lifepath:selection/species",
                                "lifepath:selection/specialization",
                                "lifepath:selection/morph_form",
                                "lifepath:ability/activate", "lifepath:ability/auto",
                                "lifepath:ability/highlight",
                            ]
                            all_ids = list(dict.fromkeys(builtins + lp + ids))
                            body = (vwrite(1) + str_field("configuration")
                                    + vwrite(len(all_ids)))
                            for ch in all_ids:
                                body += str_field(ch)
                            c.send_packet(0x02, str_field("c:register") + body)
                            log(f"-> answered c:register ({len(all_ids)} channels)")
                        except Exception as e:
                            log(f"   c:register parse fail: {e}")
                    else:
                        log(f"   unhandled config payload {ident}")
                except Exception as e:
                    log(f"config cb 0x01 parse_fail={e}")
            elif pid == 0x03:
                c.send_packet(0x03)
                phase = "play"
                log("-> play phase")
            elif pid == 0x04:
                c.send_packet(0x04, data)
            elif pid == 0x05:
                c.send_packet(0x05, data)
            elif pid == 0x0E:
                c.send_packet(0x07, data)
                log("-> echoed select_known_packs")
            else:
                log(f"config cb 0x{pid:02x} len={len(data)}")
            continue

        # ---- play phase ----
        if pid == 0x26:
            c.send_packet(0x18, data)
            continue
        if pid == 0x40:
            try:
                x, y, z, yaw, pitch, _flags = struct.unpack(">dddffB", data[:33])
                tid, _ = vread_buf(data, 33)
                cur_pos[0] = (x, y, z, yaw, pitch)
                c.send_packet(0x00, vwrite(tid))
                log(f"teleport confirm pos=({x:.1f},{y:.1f},{z:.1f})")
            except Exception as e:
                log(f"teleport parse fail: {e}")
            continue
        if pid == 0x4C:
            log(f"ACTIONBAR {extract_text(data)}")
            continue
        if pid == 0x6C:
            log(f"SYSCHAT {extract_text(data)}")
            continue
        if pid == 0x39:
            log(f"PLAYERCHAT {extract_text(data)}")
            continue
        if pid == 0x5C:
            try:
                prog = struct.unpack(">f", data[:4])[0]
                lvl, tot = struct.unpack(">ii", data[4:12])
                log(f"XP level={lvl} progress={prog:.2f} total={tot}")
            except Exception:
                pass
            continue
        if pid == 0x19:
            try:
                n, pos = vread_buf(data, 0)
                ident = data[pos:pos + n].decode("utf-8")
                rest = data[pos + n:]
                log(f"play payload {ident} len={len(rest)} {extract_text(rest)[:160]}")
                if ident in ("minecraft:register", "minecraft:unregister"):
                    c.send_packet(0x12, str_field(ident) + rest)
            except Exception:
                pass
            continue
        if pid == 0x1D:
            log(f"PLAY DISCONNECT: {describe(data)}")
            return
        if pid in (0x47, 0x2B):
            log(f"cb 0x{pid:02x} (respawn/login) len={len(data)}")
            continue
        now = time.time()
        if now - last_move > 2 and cur_pos[0]:
            last_move = now
            x, y, z, yaw, pitch = cur_pos[0]
            try:
                c.send_packet(0x1B, struct.pack(">dddffB", x, y, z, yaw, pitch, 1))
            except Exception:
                pass


if __name__ == "__main__":
    try:
        run()
    except KeyboardInterrupt:
        pass
    except Exception as e:
        log(f"FATAL {type(e).__name__}: {e}")
