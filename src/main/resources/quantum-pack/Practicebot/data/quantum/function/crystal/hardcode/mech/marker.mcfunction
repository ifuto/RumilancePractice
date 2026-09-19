execute as @e[tag=crystal2,type=marker] at @s run tp @s ~ ~1 ~
execute as @e[tag=crystal2,type=marker] at @s run tag @s remove crystal2
execute at @e[tag=crystal,type=marker] unless entity @e[type=end_crystal,distance=..2] align xyz run summon end_crystal ~.5 ~1 ~.5 {ShowBottom:false}
scoreboard players add @e[tag=crystal,type=marker] crystal 1
execute if entity @e[tag=crystal,type=marker] run schedule function quantum:crystal/hardcode/mech/marker 4t