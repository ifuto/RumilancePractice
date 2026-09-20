function quantum:options/toggles/slow_death_off
gamerule send_command_feedback false
scoreboard objectives setdisplay sidebar pops
execute unless score .mode mode matches 2 run scoreboard objectives setdisplay sidebar damage_absorbed
