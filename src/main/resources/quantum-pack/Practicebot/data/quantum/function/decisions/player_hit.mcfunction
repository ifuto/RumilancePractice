execute unless score .mode mode matches 3 if score @s[predicate=quantum:vmotion_m3] in_cobweb_decision matches 0 run scoreboard players set @s crit_decision_without_cd 1
execute unless score .mode mode matches 3 run scoreboard players set @s hit_decision_without_cd 1

execute unless score .mode mode matches 3 run return 0
execute if entity @s[scores={airborne=0}] run scoreboard players set @s hit_decision_without_cd 1
execute if entity @s[scores={airborne=0},predicate=quantum:vmotion_m3] if score @s in_cobweb_decision matches 0 run scoreboard players set @s crit_decision_without_cd 1