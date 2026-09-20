scoreboard players set @s pops 0
scoreboard players set @s resetcd 0
scoreboard players set @s reset_trigger 0
scoreboard players set @s mech_stopwatch 0
scoreboard players set @s hitcd 0
scoreboard players set @s anchor_timer 0
scoreboard players set .trigger2 reset_trigger 0
scoreboard players set @s pearlcd 0
effect clear @a
effect give @s regeneration 1 255 true
player @s stop
player @s itemCd minecraft:ender_pearl reset
kill @e[type=#mech_train:reset]
execute if score .mode mode matches 600..699 at @s run fill ~-1 ~ ~-1 ~1 ~1 ~1 air replace #rails