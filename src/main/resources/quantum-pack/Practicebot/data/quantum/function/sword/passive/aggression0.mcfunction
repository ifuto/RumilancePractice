# function quantum:look
execute unless entity @a[tag=xlib_target,distance=..8] run player @s move
player @s sprint
execute if entity @a[tag=xlib_target,distance=..8] run player @s jump once
execute unless entity @a[tag=xlib_target,distance=..8] run function quantum:sword/bot_mech/strafe

player @s hotbar 5
player @s use continuous
execute if score @s gap_timer matches 0 run function quantum:crystal/passive/gap
execute facing entity @p[tag=xlib_target,distance=..8] eyes rotated ~ 0 positioned ^ ^ ^-2 run player @s look at ~ ~1.4 ~
execute if score @s[tag=!touched_ground,scores={OnGround=0,motion=..0}] real_hitcd matches 1.. run function quantum:look

execute if entity @s[scores={crossbow_timer=0,gap_timer=1},tag=used] run function quantum:crystal/passive/stop_use