# # Counteract momentum for more accurate pearling
# player @s move backward
# execute if score .strafe toggles matches 1 as @s[scores={strafe=1,airborne=0}] run player @s move right
# execute if score .strafe toggles matches 1 as @s[scores={strafe=0,airborne=0}] run player @s move left
tag @s remove wind_pearl
player @s look up
player @s hotbar 2
player @s use once