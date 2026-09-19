# Strafe
execute unless score @s pearlcd matches ..17 run return 0
execute if score .strafe toggles matches 1 unless score @s pearlcd matches 1.. run function quantum:sword/bot_mech/strafe
scoreboard players set @s[scores={strafecd=..0,OnGround=1}] strafecd 10

execute if score @s Health matches ..16 unless score @s gap_timer matches 1.. run function quantum:crystal/passive/gap
execute at @p[tag=xlib_target] if entity @p[tag=xlib_target,distance=..0.1,predicate=quantum:sf] if score @s[scores={gap_timer=..0}] Health matches ..23 run function quantum:crystal/passive/gap
execute at @p[tag=xlib_target] if entity @p[tag=xlib_target,distance=..0.1,predicate=quantum:sf] if score @s[scores={gap_timer=..0}] saturation matches ..19 run function quantum:crystal/passive/gap

execute if score @s[scores={gap_timer=..0}] Health matches 17.. run function quantum:crystal/passive/crossbow/load
execute if entity @s[scores={crossbow_timer=0,gap_timer=0},tag=used] run function quantum:crystal/passive/stop_use

execute if score @s eval matches ..-220 unless entity @a[tag=xlib_target,distance=..15] run function quantum:bin/28
execute unless score @s eval matches ..-220 unless entity @a[tag=xlib_target,distance=..10] run function quantum:bin/28
execute if score .strafe toggles matches 1 run function quantum:sword/bot_mech/strafe