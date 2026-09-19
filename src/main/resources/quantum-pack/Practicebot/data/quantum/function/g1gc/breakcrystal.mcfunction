scoreboard players set .temp temp 0
execute at @s as @n[distance=..4,type=end_crystal] if predicate quantum:is_higher_2 run scoreboard players set .temp temp 1
execute at @n[distance=..4,type=minecraft:end_crystal] run player @s look at ~ ~ ~
execute unless score @s real_hitcd matches 1.. if score .clip toggles matches 1 if score @s crystal3 matches 2.. at @n[distance=..4,type=minecraft:end_crystal] run player @s look at ~ ~.5 ~
execute if score .temp temp matches 1 at @n[distance=..4,type=minecraft:end_crystal] facing entity @s eyes rotated ~ 0.0 run player @s look at ^ ^ ^.7
player @s attack once
damage @n[distance=..4,type=minecraft:end_crystal] 1 minecraft:player_attack by @s
scoreboard players add @s crystal3 1