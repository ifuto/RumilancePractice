scoreboard players set @s hitcd 11
# function quantum:look
function quantum:binomial_dist/reach/init
function quantum:binomial_dist/aim/init

# Crit if critting, sprint hit if comboing
player @s stop
player @s move forward
player @s sprint
player @s attack once
execute if score .axe toggles matches 1 if score @p[tag=xlib_target] disable_shield_decision matches 1 run function quantum:shield/disable


execute if score .random toggles matches 1 run function quantum:sword/randomise