# Reset scores
scoreboard players set @a crit_decision 0
scoreboard players set @a crit_decision_without_cd 0
scoreboard players set @s slam_decision 0
scoreboard players set @a hit_decision 0
scoreboard players set @a hit_decision_without_cd 0
scoreboard players set @s fill_water_decision 0
scoreboard players set @s empty_water_decision 0
scoreboard players set @s fill_lava_decision 0
scoreboard players set @s empty_lava_decision 0
scoreboard players set @s lunge_decision 0
scoreboard players set @s elytra_decision 0

# Optimisation
execute at @s[scores={in_cobweb_decision=1}] run scoreboard players set @p[tag=xlib_target,scores={hitcd=1..}] hitcd 0
execute at @s[scores={can_see_target=1}] run function quantum:decisions/optimise_stats/can_see

# Spear + elytra
execute if score .mode mode matches 3 run function quantum:decisions/spear

# Mace
execute if score .mode mode matches 3 run scoreboard players set @s[scores={in_range=1,can_see_target=1},predicate=quantum:fall_distance15] slam_decision 1
execute at @s[scores={hitcd=1..}] if entity @p[tag=xlib_target,scores={hurtTime=1..,shield_cd=..90,disablecd=0}] run scoreboard players set @s slam_decision 0

# Sword crit
scoreboard players set @s[scores={hitcd=..0,crit_decision_without_cd=1..}] crit_decision 1

# Sword hit
scoreboard players set @s[scores={hitcd=..0,hit_decision_without_cd=1..}] hit_decision 1

# Water
execute if score .mode mode matches 3 if score @s airborne matches 1 run return 0
tag @s remove cant_use_water
execute at @s if block ~ ~1 ~ cobweb if block ~ ~2 ~ cobweb run tag @s add cant_use_water
execute store result score @s water_bucket_count run clear @s water_bucket 0
execute store result score @s lava_bucket_count run clear @s lava_bucket 0
execute store result score @p[tag=xlib_target] water_bucket_count run clear @p[tag=xlib_target] water_bucket 0

execute unless items entity @s[scores={empty_water_cd=0,water_bucket_count=1..,in_cobweb_decision=1},tag=!cant_use_water] hotbar.7 water_bucket run function quantum:bin/21
execute unless items entity @s[predicate=!quantum:fire_res,predicate=quantum:fire,scores={water_cd=..0}] hotbar.7 water_bucket run function quantum:bin/21
execute if entity @e[tag=water,distance=0..,type=marker] if items entity @s[scores={empty_water_cd=0,water_bucket_count=..3,in_cobweb_decision=0},distance=0..] hotbar.7 water_bucket run function quantum:bin/22

execute if items entity @s[scores={in_cobweb_decision=1}] hotbar.7 water_bucket run scoreboard players set @s[tag=!cant_use_water] empty_water_decision 1
execute if entity @e[tag=water,distance=0..,type=marker] if items entity @s[scores={in_cobweb_decision=0},distance=0..] hotbar.7 bucket run scoreboard players set @s fill_water_decision 1
execute if score @s[scores={in_cobweb_decision=1}] in_range matches 1.. if score @p[tag=xlib_target] in_cobweb_decision matches 0 run scoreboard players set @s empty_water_decision 0

# Lava
execute if score .mode mode matches 3 run return 0
execute if entity @e[tag=in_player,distance=0..,type=marker] unless items entity @s[scores={lava_bucket_count=1..,lava_cd=0,real_hitcd=1..},distance=0..] hotbar.8 lava_bucket run function quantum:bin/23
execute if entity @e[tag=lava,distance=0..,type=marker] if items entity @s[scores={lava_bucket_count=..1,lava_cd=0},distance=0..] hotbar.8 lava_bucket run function quantum:bin/24
execute at @e[tag=in_player,distance=0..,type=marker] if items entity @s[scores={real_hitcd=1..,lava_cd=0,lava_bucket_count=1..},distance=0..] hotbar.8 lava_bucket unless entity @e[tag=water,distance=1..5,type=marker] run scoreboard players set @s empty_lava_decision 1
execute if entity @e[tag=lava,distance=0..,type=marker] if items entity @s hotbar.8 bucket run scoreboard players set @s fill_lava_decision 1
# execute at @s[scores={in_cobweb_decision=1..}] if entity @e[tag=lava,type=marker] if score @s lava_bucket_count matches ..1 run scoreboard players set @s fill_lava_decision 1