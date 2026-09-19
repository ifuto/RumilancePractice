execute if score .slow_death toggles matches 1 unless entity @a[tag=xlib_target,scores={death=0}] if entity @a[tag=xlib_target,scores={death=1},nbt={Dimension:"minecraft:overworld"}] in overworld run function quantum:map/reset
execute unless score .slow_death toggles matches 1 unless entity @a[tag=xlib_target,scores={death=0}] in overworld run function quantum:map/reset
execute unless entity @a[tag=xlib_bot,scores={death=0}] if entity @a[tag=xlib_bot] in overworld run function quantum:map/reset
execute unless score @s shielding matches 1 run player @s stop
player @s hotbar 4
scoreboard players set .move temp -1
scoreboard players set .move2 temp -1
item replace entity @s weapon.offhand with totem_of_undying
item replace entity @s hotbar.0 with totem_of_undying
execute as @a[tag=xlib_bot,gamemode=!creative] at @s run function quantum:allstats/newstats
function npc:toggle_settings
function npc:treats
function quantum:cooldowns
player @s move
player @s sprint
execute if score .move temp matches 0 run player @s move backward
execute if score .move temp matches 1 run player @s move forward
execute if score .move2 temp matches 0 run player @s move left
execute if score .move2 temp matches 1 run player @s move right
scoreboard players set @a shielding 0
# Display bot armour dura