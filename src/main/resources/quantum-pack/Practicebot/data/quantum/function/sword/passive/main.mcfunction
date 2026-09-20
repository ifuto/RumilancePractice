execute if score .shield toggles matches 0 run item replace entity @s weapon.offhand with totem_of_undying
player @s sprint
player @s move forward
kill @e[tag=cartlib,distance=0..,type=marker]

tag @s[scores={OnGround=1},tag=!touched_ground] add touched_ground
execute if score @s[tag=!touched_ground,scores={OnGround=0,motion=..0}] real_hitcd matches 1.. run player @s move backward

# Counter mace
execute if score @p[tag=xlib_target] pos matches 1 if function quantum:crystal/passive/mace/defense_main run return 1

execute if score @s shield_cd matches 96.. if score @p[tag=xlib_target] pos matches 1 if score .mode mode matches 3 run return 0

# Escape
execute if score .gear toggles matches 1 unless score .mode mode matches 4..5 if score @s distance_to_target matches ..100 unless score @s pearlcd matches 1.. if score @s gap_timer matches ..0 run return run function quantum:sword/passive/escape/pearl

execute at @s[scores={airborne=1}] if score .mode mode matches 3 run return run player @s hotbar 4
return run function quantum:sword/passive/aggression0