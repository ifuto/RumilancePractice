# parity:tick — 毎tick:
#   1) 2体目(qbot2)の脳を「敵=quantumbot」の役割で1回まわす（quantumbot はマップ自身が回す）
#   2) ラウンドが終わっていたら開始し直す
#   3) 両BOTの毎tickサンプルを出す（qlog 互換 + who/state/kit）
scoreboard players add pari_clock parity_t 1
execute if score .start start matches 1 run function parity:vs_brain
function parity:keepalive
function parity:hptrack
scoreboard players operation .hp_mod dbgc = pari_clock parity_t
scoreboard players operation .hp_mod dbgc %= .two dbgc
execute if score .hp_mod dbgc matches 0 run function parity:hpsample
function parity:clock
data merge storage parity:args {name:"quantumbot",enemy:"qbot2",who:"a"}
function parity:sample with storage parity:args
data merge storage parity:args {name:"qbot2",enemy:"quantumbot",who:"b"}
function parity:sample with storage parity:args
