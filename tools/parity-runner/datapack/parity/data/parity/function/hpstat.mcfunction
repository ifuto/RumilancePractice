# parity:hpstat — 被ダメ計測の集計を 1 行で出す。
scoreboard players set .ht_mul dbgc 0
scoreboard players operation .ht_avg_a dbgc = .c_dmg_a dbgc
execute if score .c_nhurt_a dbgc matches 1.. run scoreboard players operation .ht_avg_a dbgc /= .c_nhurt_a dbgc
execute unless score .c_nhurt_a dbgc matches 1.. run scoreboard players set .ht_avg_a dbgc -1
scoreboard players operation .ht_avg_b dbgc = .c_dmg_b dbgc
execute if score .c_nhurt_b dbgc matches 1.. run scoreboard players operation .ht_avg_b dbgc /= .c_nhurt_b dbgc
execute unless score .c_nhurt_b dbgc matches 1.. run scoreboard players set .ht_avg_b dbgc -1
data merge storage parity:hpstat {admg:0.0d,anhit:0.0d,aheal:0.0d,aavg:0.0d,bdmg:0.0d,bnhit:0.0d,bheal:0.0d,bavg:0.0d}
execute store result storage parity:hpstat admg double 0.01 run scoreboard players get .c_dmg_a dbgc
execute store result storage parity:hpstat anhit double 0.01 run scoreboard players get .c_nhurt_a dbgc
execute store result storage parity:hpstat aheal double 0.01 run scoreboard players get .c_heal_a dbgc
execute store result storage parity:hpstat aavg double 0.01 run scoreboard players get .ht_avg_a dbgc
execute store result storage parity:hpstat bdmg double 0.01 run scoreboard players get .c_dmg_b dbgc
execute store result storage parity:hpstat bnhit double 0.01 run scoreboard players get .c_nhurt_b dbgc
execute store result storage parity:hpstat bheal double 0.01 run scoreboard players get .c_heal_b dbgc
execute store result storage parity:hpstat bavg double 0.01 run scoreboard players get .ht_avg_b dbgc
function parity:hpstat_line with storage parity:hpstat
