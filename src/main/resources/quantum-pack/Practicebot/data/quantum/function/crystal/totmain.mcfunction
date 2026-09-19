execute if score .bot_totems bot_totems <= @s pops if score .inf_tot toggles matches 0 run return 1
execute unless score @s totem_timer matches 1.. if items entity @s weapon.offhand totem_of_undying if score .difficulty difficulty matches 3.. run return 1
execute if items entity @s hotbar.0 totem_of_undying if items entity @s weapon.offhand totem_of_undying run return 1
execute unless entity @e[tag=xlib,distance=0..,type=marker] unless score @s totem_timer matches 1.. if items entity @s hotbar.0 totem_of_undying if score .difficulty difficulty matches 3.. run item replace entity @s weapon.offhand with totem_of_undying
execute unless entity @e[tag=xlib,distance=0..,type=marker] unless score @s totem_timer matches 1.. if items entity @s hotbar.0 totem_of_undying if score .difficulty difficulty matches 3..4 run scoreboard players set @s totem_timer 5
execute unless score @s totem_timer matches 1.. if items entity @s hotbar.0 totem_of_undying if score .difficulty difficulty matches 3.. run return run function quantum:crystal/ignore_pop
kill @e[tag=xlib,tag=!anchor_4,distance=0..,type=marker]
player @s hotbar 1
player @s stop
scoreboard players operation @s totem_timer = @s totem_cd
item replace entity @s weapon.offhand with totem_of_undying
item replace entity @s hotbar.0 with totem_of_undying
return 0