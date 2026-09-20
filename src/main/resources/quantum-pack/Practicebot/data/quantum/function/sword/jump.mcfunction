player @s sprint
player @s move forward
player @s jump once
execute at @s unless score @p[tag=xlib_target] real_hitcd matches 1.. run scoreboard players set @s hitcd 9
