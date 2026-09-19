function mech_train:mace/common_actions
function quantum:look
item replace entity @s hotbar.0 with shield[unbreakable={},enchantment_glint_override=true]
player @s hotbar 1
player @s use continuous
execute if score @s crossbow_timer matches 0 run effect clear @a levitation
execute if entity @a[tag=xlib_target,nbt={OnGround:1b}] run scoreboard players set @s[scores={resetcd=0}] resetcd 4
execute if entity @a[tag=xlib_bot,nbt={HurtTime:10s},scores={resetcd=0}] run scoreboard players set @s resetcd 3
execute as @e[type=ender_pearl] unless block ~ ~-1 ~ #xaniclelib:nonsolid run kill
execute if score @s resetcd matches 1 run function mech_train:mace/stun_slam/loop