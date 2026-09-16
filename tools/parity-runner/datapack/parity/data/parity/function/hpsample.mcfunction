# parity:hpsample — 生HPサンプル(2tick毎)。
data merge storage parity:hps {a:0.0d,b:0.0d,aabs:0.0d,babs:0.0d,ahrt:0,aht:0,bht:0,areg:0,breg:0,atc:0,ahc:0,arhc:0,ahd:0,ahdc:0,adt:0,acst:0,ast:0,astrc:0,atsr:0,atsp:0,atss:0,ahit:0,atgt:0,btc:0,bhc:0,brhc:0,bhd:0,bhdc:0,bdt:0,bcst:0,bst:0,bstrc:0,btsr:0,btsp:0,btss:0,bhit:0,btgt:0}
execute store result storage parity:hps a double 0.01 run data get entity quantumbot Health 100
execute store result storage parity:hps b double 0.01 run data get entity qbot2 Health 100
execute store result storage parity:hps aabs double 0.01 run data get entity quantumbot AbsorptionAmount 100
execute store result storage parity:hps babs double 0.01 run data get entity qbot2 AbsorptionAmount 100
execute store result storage parity:hps ahrt double 1 run data get entity quantumbot HurtTime
execute store result storage parity:hps bht double 1 run data get entity qbot2 HurtTime
execute store result storage parity:hps areg double 1 run data get entity quantumbot active_effects[{id:"minecraft:regeneration"}].amplifier
execute store result storage parity:hps breg double 1 run data get entity qbot2 active_effects[{id:"minecraft:regeneration"}].amplifier
execute store result storage parity:hps atc double 1 run scoreboard players get quantumbot tempcrit
execute store result storage parity:hps ahc double 1 run scoreboard players get quantumbot hitcd
execute store result storage parity:hps arhc double 1 run scoreboard players get quantumbot real_hitcd
execute store result storage parity:hps ahd double 1 run scoreboard players get quantumbot hit_decision
execute store result storage parity:hps ahdc double 1 run scoreboard players get quantumbot hit_decision_without_cd
execute store result storage parity:hps adt double 1 run scoreboard players get quantumbot distance_to_target
execute store result storage parity:hps acst double 1 run scoreboard players get quantumbot can_see_target
execute store result storage parity:hps ast double 1 run scoreboard players get quantumbot state
execute store result storage parity:hps astrc double 1 run scoreboard players get quantumbot strafecd
execute store result storage parity:hps atsr double 1 run scoreboard players get quantumbot tempstrafe
execute store result storage parity:hps atsp double 1 run scoreboard players get quantumbot tempstap
execute store result storage parity:hps atss double 1 run scoreboard players get quantumbot tempscrit
execute store result storage parity:hps ahit double 1 run scoreboard players get quantumbot hit
execute store result storage parity:hps atgt double 1 run scoreboard players get quantumbot xlib_target_missing
execute store result storage parity:hps btc double 1 run scoreboard players get qbot2 tempcrit
execute store result storage parity:hps bhc double 1 run scoreboard players get qbot2 hitcd
execute store result storage parity:hps brhc double 1 run scoreboard players get qbot2 real_hitcd
execute store result storage parity:hps bhd double 1 run scoreboard players get qbot2 hit_decision
execute store result storage parity:hps bhdc double 1 run scoreboard players get qbot2 hit_decision_without_cd
execute store result storage parity:hps bdt double 1 run scoreboard players get qbot2 distance_to_target
execute store result storage parity:hps bcst double 1 run scoreboard players get qbot2 can_see_target
execute store result storage parity:hps bst double 1 run scoreboard players get qbot2 state
execute store result storage parity:hps bstrc double 1 run scoreboard players get qbot2 strafecd
execute store result storage parity:hps btsr double 1 run scoreboard players get qbot2 tempstrafe
execute store result storage parity:hps btsp double 1 run scoreboard players get qbot2 tempstap
execute store result storage parity:hps btss double 1 run scoreboard players get qbot2 tempscrit
execute store result storage parity:hps bhit double 1 run scoreboard players get qbot2 hit
execute store result storage parity:hps btgt double 1 run scoreboard players get qbot2 xlib_target_missing
function parity:hpsample_line with storage parity:hps
function parity:hpsample_line2 with storage parity:hps
