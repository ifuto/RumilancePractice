# Wind Charge
execute unless score @s pearlcd matches 1..20 at @s[scores={windcd=..0,hitcd=..0,OnGround=1,in_cobweb_decision=0,in_range=0}] run function quantum:mace/wind
execute unless score @s pearlcd matches 1..20 at @s[scores={OnGround=1,in_cobweb_decision=0,windcd=..0}] if score @p[tag=xlib_target] in_cobweb_decision matches 1 run function quantum:mace/wind

# Sword
function quantum:sword/combo/tick
execute if score @s pearlcd matches 20 run kill @n[tag=spawned,distance=0..,type=wind_charge]
execute if score @s state matches 3 run return 0

# Spear
execute if score @s lunge_decision matches 1 run function quantum:mace_new/lunge
execute if score @s elytra_decision matches 1 unless items entity @s armor.chest elytra run function quantum:mace_new/elytra
execute if score @s elytra_decision matches 0 if items entity @s armor.chest elytra run function quantum:mace_new/elytra1

# Mace
tag @s remove breach_slam
tag @s[scores={hitcd=0},predicate=!quantum:fall_distance70] add breach_slam
execute at @s[scores={slam_decision=1}] run function quantum:sword/crit
execute if score @s hit_decision matches 1 if score .breach toggles matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 run player s hotbar 9
execute if score @s slam_decision matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 run player s hotbar 3
execute if score @s slam_decision matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 if entity @s[tag=breach_slam] run player s hotbar 9
execute if score @s slam_decision matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 at @s run player s move

# Far Pearl
execute at @s[scores={pearlcd=..0}] if score .far_pearl toggles matches 1 at @p[tag=xlib_target,distance=5..,predicate=!quantum:vmotion_m1,predicate=!quantum:vmotion2] if score @s Pos1 < @p[tag=xlib_target] Pos1 if function quantum:miscellaneous/random if function quantum:decisions/airborne2 at @s run function quantum:mace/far_pearl
execute at @s[tag=wind_pearl] run function quantum:mace/wind_pearl
execute at @s[scores={pearlcd=..0,windcd=..0,wind_pearl_cd=..0,Pos1_difference=..-5},predicate=!quantum:vmotion_m5] if score .wind_pearl toggles matches 1 if score .far_pearl toggles matches 1 positioned ~-7 ~ ~-7 at @p[tag=xlib_target,dx=13,dz=13,dy=30,predicate=quantum:vmotion1] positioned ~7 ~ ~7 run function quantum:mace/wind_pearl_main
execute at @s[scores={pearlcd=..0,windcd=..0,wind_pearl_cd=..0,Pos1_difference=..-5},predicate=quantum:vmotion_m5] if score .wind_pearl toggles matches 1 unless score .far_pearl toggles matches 1 at @p[tag=xlib_target,distance=5..20,predicate=!quantum:vmotion0_1] run function quantum:mace/wind_pearl_main

execute as @e[type=ender_pearl] at @s on origin if entity @s[tag=xlib_bot] unless entity @s[tag=xlib_bot,distanceH=..30] run kill @e[type=ender_pearl,limit=1,sort=nearest]

function quantum:decisions/tick2

scoreboard players set @a disable_shield_decision 0