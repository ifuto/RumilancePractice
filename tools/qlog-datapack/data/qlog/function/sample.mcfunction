# one console line per bot per 2 ticks — position/velocity/look/hp/ground/item + map timers
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
$say [q] $(px),$(py),$(pz) v=$(vx),$(vy),$(vz) y=$(yaw) p=$(pit) hp=$(hp) g=$(g) i=$(item) hit=$(hit) tot=$(tot) ct=$(ct) ob=$(ob) pc=$(pc) cry=$(cry)
