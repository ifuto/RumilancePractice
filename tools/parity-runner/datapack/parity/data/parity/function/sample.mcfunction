# parity:sample — 1体分の毎tickサンプル（qlog 互換の並び + who/st/kit）。
# 呼び出し: function parity:sample with storage parity:args  (args: name, enemy, who)
$execute store result storage parity:in px double 0.001 run data get entity $(name) Pos[0] 1000
$execute store result storage parity:in py double 0.001 run data get entity $(name) Pos[1] 1000
$execute store result storage parity:in pz double 0.001 run data get entity $(name) Pos[2] 1000
$execute store result storage parity:in vx double 0.001 run data get entity $(name) Motion[0] 1000
$execute store result storage parity:in vy double 0.001 run data get entity $(name) Motion[1] 1000
$execute store result storage parity:in vz double 0.001 run data get entity $(name) Motion[2] 1000
$execute store result storage parity:in yaw double 0.1 run data get entity $(name) Rotation[0] 10
$execute store result storage parity:in pit double 0.1 run data get entity $(name) Rotation[1] 10
$execute store result storage parity:in hp double 0.1 run data get entity $(name) Health 10
$execute store result storage parity:in g int 1 run data get entity $(name) OnGround 1
$data modify storage parity:in item set from entity $(name) SelectedItem.id
$execute store result storage parity:in hpT int 10 run data get entity $(enemy) Health 10
$execute store result storage parity:in hit int 1 run scoreboard players get $(name) hitcd
$execute store result storage parity:in realhit int 1 run scoreboard players get $(name) real_hitcd
$execute store result storage parity:in tot int 1 run scoreboard players get $(name) totem_timer
$execute store result storage parity:in ct int 1 run scoreboard players get $(name) crystal_timer
$execute store result storage parity:in ob int 1 run scoreboard players get $(name) obby_timer
$execute store result storage parity:in pc int 1 run scoreboard players get $(name) pearlcd
$execute store result storage parity:in anc int 1 run scoreboard players get $(name) anchor_timer
$execute store result storage parity:in chg int 1 run scoreboard players get $(name) charge_timer
$execute store result storage parity:in exp int 1 run scoreboard players get $(name) explosion_timer
$execute store result storage parity:in pop int 1 run scoreboard players get $(name) pops
$execute store success storage parity:in rx byte 1 run data get entity $(name) active_effects[{id:'minecraft:regeneration'}].duration 1
$execute store result storage parity:in st int 1 run scoreboard players get $(name) state
$execute store result storage parity:in kit int 1 run scoreboard players get $(name) kit
$execute at $(name) store result storage parity:in cry int 1 run execute if entity @e[type=end_crystal,distance=..9]
$execute at $(name) store result storage parity:in ec int 1 run execute if entity @e[type=end_crystal,distance=..16]
$execute store result storage parity:in hd int 1 run scoreboard players get $(name) hit_decision_without_cd
$execute store result storage parity:in cst int 1 run scoreboard players get $(name) can_see_target
$execute store result storage parity:in p1d int 1 run scoreboard players get $(name) Pos1_difference
# アイテムを使う判断(decision)とその入力。cobweb/water/lava の内側フローが Paper で
# 一度も走らない件を追うために毎tick記録する（スコアが無いとマクロ変数が欠けて
# サンプラごと壊れるので、先に 0 で作ってから読む）。
$data modify storage parity:in who set value "$(who)"
function parity:emit with storage parity:in
