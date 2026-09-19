schedule clear quantum:map/reset
function quantum:difficulty/resetnpc
execute if score .mode mode matches 100..199 run function quantum:options/sword
execute if score .mode mode matches 200..299 run function quantum:options/crystal
execute if score .mode mode matches 300..399 run function quantum:options/mace
execute if score .mode mode matches 400..599 run function quantum:options/pot
execute if score .mode mode matches 600..699 run function quantum:options/cart
schedule clear quantum:map/start3
schedule clear quantum:bin/38
forceload remove all
effect clear @a
execute in overworld run scoreboard players set .resetcd resetcd 40
scoreboard players set @a damage_absorbed 0
execute if score .crit toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempcrit 1
execute if score .crit toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempcrit 0
execute if score .shield toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempshield 1
execute if score .shield toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempshield 0
execute if score .scrit toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempscrit 1
execute if score .scrit toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempscrit 0
execute if score .shield toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempstap 1
execute if score .shield toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempstap 0
tag @s remove macing

# Set scores
execute if score @a[tag=xlib_target,limit=1] death matches 1.. in overworld run scoreboard players add bot score 1
execute unless entity @a[tag=xlib_bot] if score .start start matches 1 run scoreboard players add player score 1

# Display winner
execute if score @a[tag=xlib_target,limit=1] death matches 1.. if score .start start matches 1 in overworld run title @a title {"text":"You lost!","color":"red"}
execute if score @a[tag=xlib_bot,limit=1] death matches 1.. if score .start start matches 1 in overworld run title @a title {"text":"You won!","color":"green"}

# Display scores
# execute if score .start start matches 1 run execute in overworld run title @a subtitle {"text":"Score:","color": "yellow"}
execute as @a[tag=xlib_bot,name=!quantumbot] run player @s disconnect
function stats:calculate_stats

# Reset
kill @e[type=item]
kill @e[tag=killable]
execute as @a[tag=xlib_target] run function quantum:bin/40
execute in overworld run tp @a[tag=xlib_bot] -646 57 88
execute in overworld run setblock -646 56 88 barrier
execute in overworld run scoreboard players set .start start 0
execute in overworld run gamemode adventure @a[tag=xlib_target]
gamemode creative @a[tag=xlib_bot]
kill @e[type=ender_pearl]
function quantum:kits/loadkit
execute in overworld run scoreboard players set @a death 0
stopsound @a
tellraw @a {"text":"<quantumbot> ggs!"}
execute unless entity @a[name=quantumbot] run function quantum:miscellaneous/botspawning