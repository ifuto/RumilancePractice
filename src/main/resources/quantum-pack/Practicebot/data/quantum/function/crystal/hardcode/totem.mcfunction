execute if score .bot_totems bot_totems <= @s pops if score .inf_tot toggles matches 0 run return 1
execute if score .difficulty difficulty matches 5 run item replace entity @s weapon.offhand with totem_of_undying
execute if score .difficulty difficulty matches 5 run item replace entity @s hotbar.0 with totem_of_undying
execute unless score @s totem_timer matches 1.. if items entity @s weapon.offhand totem_of_undying if score .difficulty difficulty matches 3.. run return 1
execute if items entity @s hotbar.0 totem_of_undying if items entity @s weapon.offhand totem_of_undying run return 1
execute unless score @s totem_timer matches 1.. if items entity @s hotbar.0 totem_of_undying if score .difficulty difficulty matches 3.. run return run function quantum:crystal/ignore_pop
player @s hotbar 1
player @s stop
kill @e[tag=hardcode,distance=0..,type=marker]
scoreboard players operation @s totem_timer = @s totem_cd
schedule function quantum:crystal/hardcode/mech/offtot 4t append
schedule function quantum:crystal/hardcode/mech/maintot 9t append
return 0