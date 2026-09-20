scoreboard players set @s state 1

# Crystal
execute if score .mode mode matches 2 if score @s eval matches ..-1 run scoreboard players set @s state 3
execute if score .mode mode matches 2 unless entity @a[tag=xlib_target,distance=..50] run scoreboard players set @s state 1
execute if score .mode mode matches 2 if score @s eval matches -224..-1 if score @s random_cd matches 0 if entity @a[tag=xlib_target,distance=..15] if function quantum:bin/29 run scoreboard players set @s state 1

# Sword
execute unless score .mode mode matches 2 run scoreboard players set @s[scores={eval=..-50}] state 3
scoreboard players operation @s debug_eval = @s eval