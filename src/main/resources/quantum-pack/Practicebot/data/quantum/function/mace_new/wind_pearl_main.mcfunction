scoreboard players set @s wind_pearl_cd 10
execute unless function quantum:miscellaneous/random run return 0

# # Counteract momentum for more accurate pearling
# player @s move backward
# execute if score .strafe toggles matches 1 as @s[scores={strafe=1,airborne=0}] run player @s move right
# execute if score .strafe toggles matches 1 as @s[scores={strafe=0,airborne=0}] run player @s move left
player @s look up
function quantum:pearl
tag @s add wind_pearl