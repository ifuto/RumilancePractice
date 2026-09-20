execute if score @s state matches 2 run function quantum:sword/passive/state2
execute unless score @s state matches 2 run function quantum:sword/passive/notstate2

tag @s[scores={state=1},tag=touched_ground] remove touched_ground
execute unless score .mode mode matches 4..5 run function quantum:decisions/tick
execute unless score .mode mode matches 4..5 run function eval:experiment/sword_tick
execute unless score .mode mode matches 4..5 if score @s state matches 3 run function quantum:sword/passive/main
execute unless score .mode mode matches 4..5 if score @s state matches 3 run return run function quantum:decisions/tick2

function quantum:sword/bot_mech/logic

# Scrit
execute if score .mode mode matches 6 run function quantum:sword/scrit
execute if score @s tempscrit matches 1 if score .gear toggles matches 2 unless score .mode mode matches 6 run function quantum:sword/scrit

execute unless score @s bowcharge matches 1.. unless score @s arrows_in_air matches 1.. run function quantum:sword/bot_mech/jump

execute if score @s crit_decision matches 1 run function quantum:sword/crit
execute if score @s crit_decision matches 0 if score @s hit_decision matches 1 unless function quantum:sword/pcrit run function quantum:sword/combo/hit
function quantum:bin/41
execute if score @s tempstrafe matches 1 unless score @s bowcharge matches 1.. unless score @s arrows_in_air matches 1.. run function quantum:sword/bot_mech/strafe
execute unless score .mode mode matches 4..5 run function quantum:cobwebs/fluid_main
execute unless score .mode mode matches 3 run scoreboard players set @a disable_shield_decision 0
execute unless score .mode mode matches 6 unless score .mode mode matches 3 run function quantum:decisions/tick2