schedule clear quantum:map/start
schedule function quantum:map/start3 3.5s
effect clear @a
scoreboard players set .resetcd resetcd 10
forceload add 11 22 11 22
execute if score .Quantum bots matches 1 run playerspawn Quantum at ~ ~ ~ facing 0 0 in survival
execute if score .Notch bots matches 1 run playerspawn Notch at ~ ~ ~ facing 0 0 in survival
execute if score .Herobrine bots matches 1 run playerspawn Herobrine at ~ ~ ~ facing 0 0 in survival
# function quantum:kits/loadkit
execute store result score .bot_totems bot_totems run clear @a[tag=xlib_target,gamemode=!spectator] totem_of_undying[max_stack_size=1] 0

scoreboard players set .clip toggles 0
function quantum:map/start_line_18
execute if entity @a[name=QuantumUltimate] run function quantum:map/start_line_17
execute unless entity @a[name=QuantumUltimate] run function quantum:map/start_line_18
execute if score .ranked toggles matches 1 run function stats:reset_stats
tag @a[tag=xlib_bot] remove macing
tag @a[tag=xlib_bot] remove pot

function quantum:rtp/passed
team modify rtp collisionRule always
execute unless score .mode mode matches 2 run team modify rtp collisionRule never
execute as @a[tag=xlib_bot] run attribute @s max_health base set 20
execute as @a[tag=xlib_bot] run attribute @s gravity base set 0.08

function quantum:map/start2
effect give @a regeneration 1 255 true
effect give @a saturation 1 255 true
effect give @a absorption 120 0 true
kill @e[type=bee]
tellraw @a {"text":"<quantumbot> glhf"}
forceload remove all