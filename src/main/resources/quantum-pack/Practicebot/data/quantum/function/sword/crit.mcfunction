# function quantum:look
function quantum:binomial_dist/reach/init
function quantum:binomial_dist/aim/init
scoreboard players set @s hitcd 11
execute if score .shield toggles matches 1 run player @s stop
player @s move forward
player @s unsprint
player @s attack once
execute if score .axe toggles matches 1 if score @p[tag=xlib_target] disable_shield_decision matches 1 run function quantum:shield/disable

execute if score .random toggles matches 1 run function quantum:sword/randomise