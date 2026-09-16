import os, shutil, sys
FUNCS = ['cobwebs/fluid_main','cobwebs/water_main','cobwebs/fill_bucket','cobwebs/empty_bucket',
         'cobwebs/fill_lava','cobwebs/empty_lava','cobwebs/cobweb','cobwebs/lava_main',
         'sword/bot_mech/logic','sword/passive/aggression0','sword/passive/bow/load',
         'sword/passive/escape/pearl','sword/passive/gap','sword/passive/main','sword/attack',
         'sword/tick','sword/bot_mech/distance','sword/bot_mech/main','allstats/advancestats']
WORLDS = ['/tmp/mcref/mcserver/QuantumMap/datapacks/Practicebot/data/quantum/function',
          '/tmp/testsrv/QuantumMap/datapacks/Practicebot/data/quantum/function']
BAK = '/tmp/instr_flow_bak'
def inst():
    shutil.rmtree(BAK, ignore_errors=True)
    n=0
    for w in WORLDS:
        root = os.path.join(w, '..', '..')  # data/quantum
        for f in FUNCS:
            p = os.path.join(w, f + '.mcfunction')
            if not os.path.exists(p):
                print('  missing', p); continue
            rel = os.path.relpath(p, '/tmp')
            dst = os.path.join(BAK, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(p, dst)
            data = open(p,'rb').read()
            name = f.replace('/','_')
            open(p,'wb').write(b'scoreboard players add .c_%s dbgc 1\r\n' % name.encode() + data)
            n+=1
    print('instrumented', n)
def revert():
    n=0
    for root, dirs, files in os.walk(BAK):
        for f in files:
            src=os.path.join(root,f)
            dst='/'+os.path.relpath(src, BAK)
            shutil.copy2(src, dst); n+=1
    print('reverted', n)
if __name__=='__main__':
    (revert if len(sys.argv)>1 and sys.argv[1]=='--revert' else inst)()
