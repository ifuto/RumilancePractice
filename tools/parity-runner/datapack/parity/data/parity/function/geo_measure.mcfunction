# parity:geo_measure — 知覚スコアを1行に落とす。
data merge storage parity:geo {ax:0.0d,az:0.0d,bx:0.0d,bz:0.0d,adt:0,ahd:0,avi:0,acst:0,ap1:0,bcst:0}
execute store result storage parity:geo ax double 0.001 run data get entity quantumbot Pos[0] 1000
execute store result storage parity:geo az double 0.001 run data get entity quantumbot Pos[2] 1000
execute store result storage parity:geo bx double 0.001 run data get entity qbot2 Pos[0] 1000
execute store result storage parity:geo bz double 0.001 run data get entity qbot2 Pos[2] 1000
execute as quantumbot at @s run function quantum:allstats/newstats
execute store result storage parity:geo adt int 1 run scoreboard players get quantumbot distance_to_target
execute store result storage parity:geo ahd int 1 run scoreboard players get quantumbot horiz_distance_to_target
execute store result storage parity:geo avi int 1 run scoreboard players get quantumbot vertical_distance_to_target
execute store result storage parity:geo acst int 1 run scoreboard players get quantumbot can_see_target
execute store result storage parity:geo ap1 int 1 run scoreboard players get quantumbot Pos1_difference
execute store result storage parity:geo bcst int 1 run scoreboard players get qbot2 can_see_target
function parity:geo_line with storage parity:geo
