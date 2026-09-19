execute if score @p[tag=xlib_target] OnGround matches 1 run return 1
execute if score @s[scores={Pos1_difference=..-1}] real_hitcd matches 1.. if score @p[tag=xlib_target] OnGround matches 0 run return 1
execute if score @s Pos1_difference matches ..-1 if score @p[tag=xlib_target,predicate=quantum:vmotion0] OnGround matches 0 run return 1
return 0