# # Counteract momentum for more accurate pearling
# player @s move backward
# execute if score .strafe toggles matches 1 as @s[scores={strafe=1,airborne=0}] run player @s move right
# execute if score .strafe toggles matches 1 as @s[scores={strafe=0,airborne=0}] run player @s move left
function quantum:crystal/passive/crossbow/aim
function quantum:pearl