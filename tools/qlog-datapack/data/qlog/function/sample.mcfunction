# 1行/毎tick — 位置/速度/視点/HP/接地/手持ち + マップのタイマ(クリスタル/アンカー/トーテム/パール)
execute store result storage qlog:in px double 0.001 run data get entity @s Pos[0] 1000
execute store result storage qlog:in py double 0.001 run data get entity @s Pos[1] 1000
execute store result storage qlog:in pz double 0.001 run data get entity @s Pos[2] 1000
execute store result storage qlog:in vx double 0.001 run data get entity @s Motion[0] 1000
execute store result storage qlog:in vy double 0.001 run data get entity @s Motion[1] 1000
execute store result storage qlog:in vz double 0.001 run data get entity @s Motion[2] 1000
execute store result storage qlog:in yaw double 0.1 run data get entity @s Rotation[0] 10
execute store result storage qlog:in pit double 0.1 run data get entity @s Rotation[1] 10
execute store result storage qlog:in hp double 0.1 run data get entity @s Health 10
execute store result storage qlog:in g int 1 run data get entity @s OnGround 1
data modify storage qlog:in item set from entity @s SelectedItem.id
execute store result storage qlog:in hit int 1 run scoreboard players get @s hitcd
execute store result storage qlog:in tot int 1 run scoreboard players get @s totem_timer
execute store result storage qlog:in ct int 1 run scoreboard players get @s crystal_timer
execute store result storage qlog:in ob int 1 run scoreboard players get @s obby_timer
execute store result storage qlog:in pc int 1 run scoreboard players get @s pearlcd
execute store result storage qlog:in cry int 1 run execute if entity @e[type=end_crystal,distance=..9]
execute store result storage qlog:in anc int 1 run scoreboard players get @s anchor_timer
execute store result storage qlog:in chg int 1 run scoreboard players get @s charge_timer
execute store result storage qlog:in exp int 1 run scoreboard players get @s explosion_timer
execute store result storage qlog:in pop int 1 run scoreboard players get @s pops
execute store result storage qlog:in ec int 1 run execute if entity @e[type=end_crystal,distance=..16]
execute if entity @p[tag=xlib_target,distance=..30] store result storage qlog:in hpT int 10 run data get entity @p[tag=xlib_target,distance=..30] Health 10
execute store result storage qlog:in t int 1 run scoreboard players get #qlog_tick qlog_t
function qlog:emit with storage qlog:in
