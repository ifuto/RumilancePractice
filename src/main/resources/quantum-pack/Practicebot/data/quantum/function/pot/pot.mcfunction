execute if score @s pot_count matches 2.. run return 0

execute at @s facing entity @p[tag=xlib_target] eyes rotated ~ 0.0 run player @s look at ^ ^ ^-1.3
player @s hotbar 2
player @s use once
player @s sprint
player @s move forward
scoreboard players add @s pot_count 1
scoreboard players set @s pot_cd 2