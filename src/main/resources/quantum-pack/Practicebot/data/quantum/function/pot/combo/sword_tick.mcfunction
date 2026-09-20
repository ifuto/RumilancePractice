function quantum:sword/bot_mech/combo_logic
function quantum:sword/bot_mech/distance
execute at @s rotated ~ 0.0 if block ^ ^ ^1 cobweb unless score @s in_cobweb_decision matches 1 run player @s move backward
execute at @s rotated ~ 0.0 if block ^ ^1 ^1 cobweb unless score @s in_cobweb_decision matches 1 run player @s move backward
execute if score .strafe toggles matches 1 at @s run function quantum:sword/bot_mech/strafe
execute if score @s hit_decision matches 1 at @s run function quantum:sword/combo/hit
function quantum:cobwebs/fluid_main
scoreboard players set @a disable_shield_decision 0
function quantum:decisions/tick2