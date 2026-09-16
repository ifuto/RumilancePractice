# parity:diag — 1 コマンドで観測用の 1 行を出す(計測の高速化用)。
data merge storage parity:diag {ax:0.0d,ay:0.0d,az:0.0d,avx:0.0d,avz:0.0d,bx:0.0d,by:0.0d,bz:0.0d,bvx:0.0d,bvz:0.0d,ahp:0.0d,bhp:0.0d,acrt:0.0d,bcrt:0.0d,ast:0.0d,bst:0.0d,ahd:0.0d,bhd:0.0d,ap1:0.0d,bp1:0.0d,ahit:0.0d,bhit:0.0d,acr:0.0d,bcr:0.0d,aobby:0.0d,bobby:0.0d,air:0.0d,bir:0.0d,ad2t:0.0d,bd2t:0.0d,ahc:0.0d,bhc:0.0d,arhc:0.0d,brhc:0.0d,apc:0.0d,bpc:0.0d,aht:0.0d,bht:0.0d,ahu:0.0d,bhu:0.0d,aog:0.0d,bog:0.0d,acst:0.0d,bcst:0.0d,atc:0.0d,btc:0.0d,astt:0.0d,bstt:0.0d,mloc:0.0d,musable:0.0d,mmall:0.0d,mmglow:0.0d}
execute store result storage parity:diag ax double 0.001 run data get entity quantumbot Pos[0] 1000
execute store result storage parity:diag ay double 0.001 run data get entity quantumbot Pos[1] 1000
execute store result storage parity:diag az double 0.001 run data get entity quantumbot Pos[2] 1000
execute store result storage parity:diag avx double 0.001 run data get entity quantumbot Motion[0] 1000
execute store result storage parity:diag avz double 0.001 run data get entity quantumbot Motion[2] 1000
execute store result storage parity:diag ahp double 0.001 run data get entity quantumbot Health 1000
execute store result storage parity:diag acrt double 1 run scoreboard players get quantumbot crystal_timer
execute store result storage parity:diag ast double 1 run scoreboard players get quantumbot state
execute store result storage parity:diag ahd double 1 run scoreboard players get quantumbot hit_decision_without_cd
execute store result storage parity:diag ap1 double 1 run scoreboard players get quantumbot Pos1_difference
execute store result storage parity:diag ahit double 1 run scoreboard players get quantumbot hit
execute store result storage parity:diag acr double 1 run scoreboard players get quantumbot num_of_crystals_placed
execute store result storage parity:diag aobby double 1 run scoreboard players get quantumbot num_of_anchors_placed
execute store result storage parity:diag air double 1 run scoreboard players get quantumbot in_range
execute store result storage parity:diag ad2t double 1 run scoreboard players get quantumbot distance_to_target
execute store result storage parity:diag ahc double 1 run scoreboard players get quantumbot hitcd
execute store result storage parity:diag arhc double 1 run scoreboard players get quantumbot real_hitcd
execute store result storage parity:diag apc double 1 run scoreboard players get quantumbot pearlcd
execute store result storage parity:diag aht double 1 run scoreboard players get quantumbot hurtTime
execute store result storage parity:diag ahu double 1 run scoreboard players get quantumbot hunger
execute store result storage parity:diag aog double 1 run scoreboard players get quantumbot OnGround
execute store result storage parity:diag acst double 1 run scoreboard players get quantumbot can_see_target
execute store result storage parity:diag atc double 1 run scoreboard players get quantumbot tempcrit
execute store result storage parity:diag astt double 1 run scoreboard players get quantumbot state_time
execute store result storage parity:diag bx double 0.001 run data get entity qbot2 Pos[0] 1000
execute store result storage parity:diag by double 0.001 run data get entity qbot2 Pos[1] 1000
execute store result storage parity:diag bz double 0.001 run data get entity qbot2 Pos[2] 1000
execute store result storage parity:diag bvx double 0.001 run data get entity qbot2 Motion[0] 1000
execute store result storage parity:diag bvz double 0.001 run data get entity qbot2 Motion[2] 1000
execute store result storage parity:diag bhp double 0.001 run data get entity qbot2 Health 1000
execute store result storage parity:diag bcrt double 1 run scoreboard players get qbot2 crystal_timer
execute store result storage parity:diag bst double 1 run scoreboard players get qbot2 state
execute store result storage parity:diag bhd double 1 run scoreboard players get qbot2 hit_decision_without_cd
execute store result storage parity:diag bp1 double 1 run scoreboard players get qbot2 Pos1_difference
execute store result storage parity:diag bhit double 1 run scoreboard players get qbot2 hit
execute store result storage parity:diag bcr double 1 run scoreboard players get qbot2 num_of_crystals_placed
execute store result storage parity:diag bobby double 1 run scoreboard players get qbot2 num_of_anchors_placed
execute store result storage parity:diag bir double 1 run scoreboard players get qbot2 in_range
execute store result storage parity:diag bd2t double 1 run scoreboard players get qbot2 distance_to_target
execute store result storage parity:diag bhc double 1 run scoreboard players get qbot2 hitcd
execute store result storage parity:diag brhc double 1 run scoreboard players get qbot2 real_hitcd
execute store result storage parity:diag bpc double 1 run scoreboard players get qbot2 pearlcd
execute store result storage parity:diag bht double 1 run scoreboard players get qbot2 hurtTime
execute store result storage parity:diag bhu double 1 run scoreboard players get qbot2 hunger
execute store result storage parity:diag bog double 1 run scoreboard players get qbot2 OnGround
execute store result storage parity:diag bcst double 1 run scoreboard players get qbot2 can_see_target
execute store result storage parity:diag btc double 1 run scoreboard players get qbot2 tempcrit
execute store result storage parity:diag bstt double 1 run scoreboard players get qbot2 state_time
scoreboard players set .d_loc dbgc 0
execute as @e[type=minecraft:marker,tag=loc] run scoreboard players add .d_loc dbgc 1
execute store result storage parity:diag mloc double 1 run scoreboard players get .d_loc dbgc
scoreboard players set .d_usable dbgc 0
execute as @e[type=minecraft:marker,tag=usable] run scoreboard players add .d_usable dbgc 1
execute store result storage parity:diag musable double 1 run scoreboard players get .d_usable dbgc
scoreboard players set .d_mall dbgc 0
execute as @e[type=minecraft:marker] run scoreboard players add .d_mall dbgc 1
execute store result storage parity:diag mmall double 1 run scoreboard players get .d_mall dbgc
scoreboard players set .d_mglow dbgc 0
execute as @e[type=minecraft:marker,tag=glowstone] run scoreboard players add .d_mglow dbgc 1
execute store result storage parity:diag mmglow double 1 run scoreboard players get .d_mglow dbgc
function parity:diag_line with storage parity:diag
function parity:diag_line2 with storage parity:diag
