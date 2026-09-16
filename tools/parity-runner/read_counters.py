import socket, sys
sys.path.insert(0,'/home/user/RumilancePractice/tools/parity-runner')
import rcon
FUNCS = ['cobwebs/fluid_main','cobwebs/water_main','cobwebs/fill_bucket','cobwebs/empty_bucket',
         'cobwebs/fill_lava','cobwebs/empty_lava','cobwebs/cobweb',
         'sword/bot_mech/logic','sword/passive/aggression0','sword/passive/bow/load',
         'sword/passive/escape/pearl','sword/passive/gap','sword/passive/main',
         'sword/tick','sword/bot_mech/distance','allstats/advancestats']
def counts(port, label):
    with socket.create_connection(('127.0.0.1', port), timeout=10) as sock:
        sock.sendall(rcon.pack(1,3,'parity')); rcon.read_packet(sock)
        i=[2]
        def c(cmd):
            i[0]+=1
            try: return rcon.command(sock, i[0], cmd)
            except Exception: return ''
        print('===', label)
        for f in FUNCS:
            v = c('scoreboard players get .c_%s dbgc' % f.replace('/','_'))
            n = v.split(' has ')[-1].split(' ')[0]
            print('  %-28s %s' % (f, n))
counts(int(sys.argv[1]), sys.argv[2])
