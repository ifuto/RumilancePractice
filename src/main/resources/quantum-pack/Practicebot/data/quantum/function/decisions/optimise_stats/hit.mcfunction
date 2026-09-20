execute unless score .mode mode matches 3 run scoreboard players set @s hit_decision_without_cd 1
execute unless score .mode mode matches 3 run scoreboard players set @s[predicate=quantum:vmotion_m3] crit_decision_without_cd 1

execute unless score .mode mode matches 3 run return 0
execute unless score @s airborne matches 0 run return 0
execute if entity @s[scores={windcd=..0}] run scoreboard players set @s hit_decision_without_cd 1
scoreboard players set @s[predicate=quantum:vmotion_m3] crit_decision_without_cd 1