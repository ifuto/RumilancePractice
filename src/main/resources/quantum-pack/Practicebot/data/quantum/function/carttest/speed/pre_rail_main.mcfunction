execute unless score .difficulty difficulty matches 5 run scoreboard players set @s rail_timer 10
execute unless function quantum:miscellaneous/random run return 0
execute at @n[tag=rail_3,distance=0..,type=tnt_minecart] run function quantum:cart/charge
execute at @n[tag=rail_2,distance=0..,type=marker] unless entity @e[tag=rail_3,distance=0..,type=tnt_minecart] run function quantum:cart/charge

execute at @n[tag=rail_3,distance=0..,type=tnt_minecart] run tag @s add spot
execute as @n[tag=rail_2,distance=0..,type=marker] unless entity @e[tag=rail_3,distance=0..,type=tnt_minecart] run tag @s add spot
execute as @e[tag=spot,distance=0..,type=marker] run kill @e[tag=rail_1,tag=!spot,distance=0..,type=marker]
execute as @e[tag=spot,distance=0..,type=marker] run kill @e[tag=rail_2,tag=!spot,distance=0..,type=marker]
execute as @e[tag=spot,distance=0..,type=marker] run tag @e[tag=rail_3,tag=!spot,distance=0..,type=tnt_minecart] remove rail_3