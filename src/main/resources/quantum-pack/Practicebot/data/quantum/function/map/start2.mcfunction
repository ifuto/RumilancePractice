gamemode survival @a
title @a actionbar {"text": "Enable chat for full experience","color": "aqua"}

# Clean up
kill @e[type=marker]
kill @e[type=#mech_train:reset]
# tellraw @a {"text":"Press me for a slow game!","click_event":{"action":"run_command","command":"/tick rate 10"},"color":"blue"}
# tellraw @a {"text":"Press me for a normal game!","click_event":{"action":"run_command","command":"/tick rate 20"},"color": "#ff7b00"}
# tellraw @a {"text":"Press me for a fast game!","click_event":{"action":"run_command","command":"/tick rate 30"},"color":"#ff3939"}

# Set up reset
execute if score .gear toggles matches 1 as @a[tag=xlib_bot] run function quantum:botgear/neth
execute if score .gear toggles matches 2 as @a[tag=xlib_bot] run function quantum:botgear/dia
scoreboard players set @a pops 0