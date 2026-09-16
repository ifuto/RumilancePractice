# parity:hpreset — 被ダメ計測のリセット。
scoreboard players set .c_dmg_a dbgc 0
scoreboard players set .c_nhurt_a dbgc 0
scoreboard players set .c_heal_a dbgc 0
execute store result score .ht_prev_a dbgc run data get entity quantumbot Health 100
scoreboard players set .c_dmg_b dbgc 0
scoreboard players set .c_nhurt_b dbgc 0
scoreboard players set .c_heal_b dbgc 0
execute store result score .ht_prev_b dbgc run data get entity qbot2 Health 100
