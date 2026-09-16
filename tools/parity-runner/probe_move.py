import socket, sys, time, re
sys.path.insert(0,'/home/user/RumilancePractice/tools/parity-runner')
import rcon
port, pre, label = int(sys.argv[1]), sys.argv[2], sys.argv[3]
with socket.create_connection(('127.0.0.1', port), timeout=10) as sock:
    sock.sendall(rcon.pack(1,3,'parity')); rcon.read_packet(sock)
    i=[2]
    def raw(c):
        i[0]+=1
        try: return str(rcon.command(sock, i[0], c))
        except Exception as e: return 'ERR'
    def verb(c): return raw(pre+c)
    def num(cmd):
        m = re.search(r'data:\s*([-\d.]+)', raw(cmd))
        return float(m.group(1)) if m else None
    def pos():
        f = lambda k: num('data get entity quantumbot %s' % k)
        return 'x=%.3f y=%.3f z=%.3f vx=%.4f vy=%.4f vz=%.4f g=%s' % (
            f('Pos[0]') or 0, f('Pos[1]') or 0, f('Pos[2]') or 0,
            f('Motion[0]') or 0, f('Motion[1]') or 0, f('Motion[2]') or 0, raw('data get entity quantumbot OnGround'))
    print('===', label)
    verb('function parity:load')
    print('  fill:', verb('function parity:arena_fill'))
    time.sleep(28)
    verb('playerspawn quantumbot at -646 57 88 facing 0 0 in survival')
    time.sleep(1.5)
    print('  idle    ', pos())
    print('  verbret :', verb('player quantumbot move forward'))
    for t in (0.3, 1.0):
        time.sleep(0.4); print('  fwd %.1fs' % t, pos())
    verb('player quantumbot sprint'); time.sleep(0.5); print('  sprint  ', pos())
    verb('player quantumbot unsprint')
    verb('player quantumbot move'); time.sleep(0.6); print('  stop    ', pos())
