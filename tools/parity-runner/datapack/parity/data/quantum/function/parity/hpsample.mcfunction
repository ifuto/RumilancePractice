# parity:hpsample — 生HPサンプル(2tick毎)。
data merge storage parity:hps {a:0.0d,b:0.0d,aabs:0.0d,babs:0.0d,ahrt:0,aht:0,bht:0,areg:0,breg:0}
execute store result storage parity:hps a double 0.01 run data get entity quantumbot Health 100
execute store result storage parity:hps b double 0.01 run data get entity qbot2 Health 100
execute store result storage parity:hps aabs double 0.01 run data get entity quantumbot AbsorptionAmount 100
execute store result storage parity:hps babs double 0.01 run data get entity qbot2 AbsorptionAmount 100
execute store result storage parity:hps ahrt double 1 run data get entity quantumbot HurtTime
execute store result storage parity:hps bht double 1 run data get entity qbot2 HurtTime
execute store result storage parity:hps areg double 1 run data get entity quantumbot active_effects[{id:"minecraft:regeneration"}].amplifier
execute store result storage parity:hps breg double 1 run data get entity qbot2 active_effects[{id:"minecraft:regeneration"}].amplifier
function parity:hps_line with storage parity:hps
