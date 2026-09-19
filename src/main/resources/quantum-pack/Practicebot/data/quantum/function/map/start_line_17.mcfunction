gamerule send_command_feedback true
function quantum:options/toggles/slow_death_on
execute if score .mode mode matches 2 run function quantum:options/toggles/strafeon
execute unless score .dbp toggles matches 1 run scoreboard players set .clip toggles 1
