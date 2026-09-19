scoreboard players set @s eval_hit 0

# Hit
execute if score @s state matches 1..2 if score @s can_see_target matches 1 if score @s[scores={in_range=1}] real_hitcd <= @p[tag=xlib_target] hitcd run scoreboard players add @s eval_hit 2
execute if score @s state matches 1..2 unless score @s real_hitcd <= @p[tag=xlib_target,distance=..3] hitcd run scoreboard players remove @s eval_hit 1
# execute if score @p[tag=xlib_target] crit_decision_without_cd matches 1 unless score @p[tag=xlib_target] rea

# Mace
execute if score .mode mode matches 3 if score @s fall_distance >= @p[tag=xlib_target] fall_distance if score @s Pos1 >= @p[tag=xlib_target] Pos1 run scoreboard players add @s eval_hit 4
execute if score .mode mode matches 3 if score @p[tag=xlib_target] fall_distance >= @s fall_distance if score @p[tag=xlib_target] Pos1 >= @s Pos1 run scoreboard players remove @s eval_hit 4

# scoreboard players operation @s eval_hit *= .eval_hit factor