# parity:hptrack — HP 差分による被ダメ計測(毎tick)。
execute store result score .ht_now dbgc run data get entity quantumbot Health 100
execute store result score .ht_hp1 dbgc run data get entity quantumbot AbsorptionAmount 100
scoreboard players operation .ht_d dbgc = .ht_prev_a dbgc
scoreboard players operation .ht_d dbgc -= .ht_now dbgc
scoreboard players operation .ht_abs dbgc = .ht_d dbgc
execute if score .ht_d dbgc matches ..-1 run scoreboard players operation .ht_abs dbgc *= .neg_one dbgc
execute if score .ht_d dbgc matches 1.. run scoreboard players operation .c_dmg_a dbgc += .ht_d dbgc
execute if score .ht_d dbgc matches 1.. run scoreboard players add .c_nhurt_a dbgc 1
execute if score .ht_d dbgc matches ..-1 run scoreboard players operation .c_heal_a dbgc += .ht_abs dbgc
scoreboard players operation .ht_prev_a dbgc = .ht_now dbgc
execute store result score .ht_now dbgc run data get entity qbot2 Health 100
execute store result score .ht_hp1 dbgc run data get entity qbot2 AbsorptionAmount 100
scoreboard players operation .ht_d dbgc = .ht_prev_b dbgc
scoreboard players operation .ht_d dbgc -= .ht_now dbgc
scoreboard players operation .ht_abs dbgc = .ht_d dbgc
execute if score .ht_d dbgc matches ..-1 run scoreboard players operation .ht_abs dbgc *= .neg_one dbgc
execute if score .ht_d dbgc matches 1.. run scoreboard players operation .c_dmg_b dbgc += .ht_d dbgc
execute if score .ht_d dbgc matches 1.. run scoreboard players add .c_nhurt_b dbgc 1
execute if score .ht_d dbgc matches ..-1 run scoreboard players operation .c_heal_b dbgc += .ht_abs dbgc
scoreboard players operation .ht_prev_b dbgc = .ht_now dbgc
