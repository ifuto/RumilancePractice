# Reset
execute if score .slow_death toggles matches 1 unless entity @a[tag=xlib_target,scores={death=0}] if entity @a[tag=xlib_target,scores={death=1},nbt={Dimension:"minecraft:overworld"}] in overworld run function quantum:map/reset
execute unless score .slow_death toggles matches 1 unless entity @a[tag=xlib_target,scores={death=0}] in overworld run function quantum:map/reset
execute unless entity @a[tag=xlib_bot,scores={death=0}] if entity @a[tag=xlib_bot] in overworld run function quantum:map/reset

# Controls hunger, gives resistance, gives tags, adds utilities
execute store result score .random random run random value 0..100
execute as @a[tag=xlib_bot] at @s run function quantum:allstats/newstats
execute if entity @a[tag=xlib_target,scores={death=0}] run function quantum:miscellaneous/tags

# In hub
execute as @a[tag=xlib_bot,gamemode=creative] run scoreboard players set .start start 0
execute as @a[tag=xlib_bot] if score .start start matches 0 run function quantum:map/passive
execute if score .start start matches 0 run return 0

execute as @a in minecraft:overworld positioned 11 34 10 if entity @a[tag=xlib_target,distance=..1.3] run return run player @s stop
execute in minecraft:overworld positioned 11 34 10 run kill @e[distance=..3,type=wind_charge]

# Initiate modes
execute if score .start start matches 1 run function quantum:init/mode