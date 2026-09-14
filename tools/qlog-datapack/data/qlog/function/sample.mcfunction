# one console line per bot per sample: position / hp / the map's own timers / nearby crystals
execute store result storage qlog:in px double 0.01 run data get entity @s Pos[0] 100
execute store result storage qlog:in py double 0.01 run data get entity @s Pos[1] 100
execute store result storage qlog:in pz double 0.01 run data get entity @s Pos[2] 100
execute store result storage qlog:in hp double 0.1 run data get entity @s Health 10
execute store result storage qlog:in hit int 1 run scoreboard players get @s hitcd
execute store result storage qlog:in tot int 1 run scoreboard players get @s totem_timer
execute store result storage qlog:in ctim int 1 run scoreboard players get @s crystal_timer
execute store result storage qlog:in cry int 1 run execute if entity @e[type=end_crystal,distance=..8]
$say [qlog] pos=$(px),$(py),$(pz) hp=$(hp) hitcd=$(hitcd) totem=$(tot) ct=$(ctim) cry=$(cry)
