#!/usr/bin/env python3
"""最小の RCON クライアント。/tmp が消えても使えるよう repо に置いてある。

    python3 tools/parity-runner/rcon.py <port> <password> '<command>' ['<command>' ...]

コマンドは 1 つずつ別の引数で渡すこと(セミコロンで繋ぐと通らない)。
Paper 側でプラグインの verb を使う場合は 'quantum run ...' を前置する。

応答は **リクエスト ID で照合する**。単に「1 コマンド送って 1 パケット読む」だと、
サーバーが前のコマンドの応答をあとから返したときに 1 つズレた値を読み、
「0 を入れた直後に読んだら 5805 が返る」ような嘘の測定になる(実測で踏んだ)。
"""
import socket
import struct
import sys
import time


def pack(req_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack('<ii', req_id, kind) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(payload)) + payload


def recv_exact(sock: socket.socket, size: int) -> bytes:
    buf = b''
    while len(buf) < size:
        chunk = sock.recv(size - len(buf))
        if not chunk:
            raise EOFError('connection closed')
        buf += chunk
    return buf


def read_packet(sock: socket.socket):
    (length,) = struct.unpack('<i', recv_exact(sock, 4))
    payload = recv_exact(sock, length)
    req_id, kind = struct.unpack('<ii', payload[:8])
    return req_id, kind, payload[8:-2].decode('utf-8', 'replace')


def command(sock: socket.socket, req_id: int, body: str, timeout: float = 10.0) -> str:
    """1 コマンド送って、同じ req_id の応答が来るまで読む(古い応答は捨てる)。"""
    sock.sendall(pack(req_id, 2, body))
    deadline = time.time() + timeout
    while True:
        sock.settimeout(max(0.1, deadline - time.time()))
        got_id, _kind, got_body = read_packet(sock)
        if got_id == req_id:
            return got_body
        if time.time() > deadline:
            return got_body  # 落とさずに返す(呼び出し側が判断)


def main(argv):
    if len(argv) < 4:
        print(__doc__)
        return 2
    port, password, commands = int(argv[1]), argv[2], argv[3:]
    with socket.create_connection(('127.0.0.1', port), timeout=10) as sock:
        sock.sendall(pack(1, 3, password))
        req_id, _kind, _body = read_packet(sock)
        if req_id == -1:
            print('rcon: auth failed')
            return 1
        for i, cmd in enumerate(commands, 2):
            print(command(sock, i, cmd))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
