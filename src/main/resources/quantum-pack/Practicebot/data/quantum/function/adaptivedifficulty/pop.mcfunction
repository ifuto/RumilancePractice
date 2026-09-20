advancement revoke @s only quantum:pop
execute if score .difficulty difficulty matches 0 run function npc:give_effects
scoreboard players set @s[tag=xlib_target] totem_timer 15
execute unless entity @a[tag=xlib_bot,tag=adapt] run return 0
# If the player pops more the combo will be positive
execute if entity @s[tag=xlib_target] unless score .combo pops matches 4.. run scoreboard players add .combo pops 1

execute if entity @s[tag=xlib_bot] unless score .combo pops matches ..-4 run scoreboard players remove .combo pops 1

execute if score .combo pops matches ..-4 run scoreboard players remove @a[tag=xlib_bot] totem_cd 5
execute if score .combo pops matches ..-4 run scoreboard players remove @a[tag=xlib_bot] totem_timer 5
execute if score .combo pops matches ..-4 run scoreboard players remove .combocount pops 1
execute if score .combo pops matches ..-4 run scoreboard players set .combo pops 0

execute if score .combo pops matches 4.. run scoreboard players add @a[tag=xlib_bot] totem_cd 5
execute if score .combo pops matches 4.. run scoreboard players add .combocount pops 1
execute if score .combo pops matches 4.. run scoreboard players set .combo pops 0

execute if score .combocount pops matches ..-2 unless score .difficulty difficulty matches 5.. run scoreboard players add .difficulty difficulty 1
execute if score .combocount pops matches 2.. unless score .difficulty difficulty matches ..1 run scoreboard players remove .difficulty difficulty 1
execute unless score .combocount pops matches -1..1 unless score .difficulty difficulty matches 5.. unless score .difficulty difficulty matches ..1 run function quantum:adaptivedifficulty/setdifficulty