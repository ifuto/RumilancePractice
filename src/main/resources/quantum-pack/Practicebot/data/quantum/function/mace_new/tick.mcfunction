# Wind Charge
execute if score .difficulty difficulty matches 0 run return run function quantum:mace/difficulty0
execute unless score @s pearlcd matches 1..20 at @s[scores={windcd=..0,hitcd=..0,OnGround=1,in_cobweb_decision=0,in_range=0}] run function quantum:mace/wind
execute unless score @s pearlcd matches 1..20 at @s[scores={OnGround=1,in_cobweb_decision=0,windcd=..0}] if score @p[tag=xlib_target] in_cobweb_decision matches 1 run function quantum:mace/wind

# Sword
function quantum:sword/tick
execute if score @s pearlcd matches 20 run kill @n[tag=spawned,distance=0..,type=wind_charge]
execute if score @s state matches 3 run return 0

# Spear
execute if score @s lunge_decision matches 1 run function quantum:mace/lunge
execute if score @s elytra_decision matches 1 unless items entity @s armor.chest elytra run function quantum:mace/elytra
execute if score @s elytra_decision matches 0 if items entity @s armor.chest elytra run function quantum:mace/elytra1

# Mace
tag @s remove breach_slam
tag @s[scores={hitcd=0},predicate=!quantum:fall_distance70] add breach_slam
execute at @s[scores={slam_decision=1}] run function quantum:sword/crit
execute if score @s crit_decision matches 1 if score .breach toggles matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 run player @s hotbar 9
execute if score @s[scores={crit_decision=0}] hit_decision matches 1 if score .breach toggles matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 unless function quantum:sword/pcrit run player @s hotbar 9
execute if score @s slam_decision matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 run player @s hotbar 3
execute if score @s[tag=breach_slam] slam_decision matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 run player @s hotbar 9
execute if score @s slam_decision matches 1 unless score @p[tag=xlib_target] disable_shield_decision matches 1 run player @s move

# Far Pearl
execute if entity @s[scores={pearlcd=..0,Pos1_difference=..0}] if score .far_pearl toggles matches 1 at @p[tag=xlib_target,distance=5..,predicate=!quantum:vmotion_m3,predicate=!quantum:vmotion2] if function quantum:miscellaneous/random if function quantum:decisions/airborne2 at @s run function quantum:mace/far_pearl
execute at @s[tag=wind_pearl] run function quantum:mace/wind_pearl
execute at @s[scores={pearlcd=..0,windcd=..0,wind_pearl_cd=..0,Pos1_difference=..-5},predicate=!quantum:vmotion_m5] if score .wind_pearl toggles matches 1 run function quantum:mace/tick_line_37

function quantum:decisions/tick2

scoreboard players set @a disable_shield_decision 0