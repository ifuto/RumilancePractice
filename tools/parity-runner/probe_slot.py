import socket, sys, time
sys.path.insert(0,'/home/user/RumilancePractice/tools/parity-runner')
import rcon
port, pre = int(sys.argv[1]), sys.argv[2]
with socket.create_connection(('127.0.0.1', port), timeout=10) as sock:
    sock.sendall(rcon.pack(1,3,'parity')); rcon.read_packet(sock)
    i=[2]
    def raw(c):
        i[0]+=1
        return rcon.command(sock, i[0], c)
    def verb(c): return raw(pre+c)
    def read(c): return raw(c)
    print('before:', read('data get entity quantumbot SelectedItem.id'), '|', read('data get entity quantumbot SelectedItemSlot'))
    verb('player quantumbot hotbar 5'); time.sleep(0.6)
    print('after5:', read('data get entity quantumbot SelectedItem.id'), '|', read('data get entity quantumbot SelectedItemSlot'))
    verb('player quantumbot hotbar 9'); time.sleep(0.6)
    print('after9:', read('data get entity quantumbot SelectedItem.id'), '|', read('data get entity quantumbot SelectedItemSlot'))
