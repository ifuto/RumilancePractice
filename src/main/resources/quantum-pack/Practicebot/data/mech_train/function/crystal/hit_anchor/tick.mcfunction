function mech_train:crystal/common_actions
execute if block ~ ~-.1 ~ #xaniclelib:nonsolid run scoreboard players set @s reset_trigger 1
execute if score @s anchor_timer matches 1 run scoreboard players set @s resetcd 4
execute if score @s reset_trigger matches 1 if score .shield toggles matches 1 run function quantum:crystal/passive/shield/main
execute if score @s reset_trigger matches 1 run scoreboard players set @s hitcd 7
execute if score @s pops matches 0 if score @s hitcd matches 1 run scoreboard players set @s resetcd 1
execute if score @s[nbt={OnGround:1b},scores={pops=0,resetcd=0}] reset_trigger matches 1 unless block ~ ~-.2 ~ respawn_anchor run scoreboard players set @s resetcd 4
execute if score @s[nbt={OnGround:1b},scores={pops=1,resetcd=0,anchor_timer=0}] reset_trigger matches 1 run scoreboard players set @s anchor_timer 22
execute if score @s[scores={pops=2..,resetcd=0}] reset_trigger matches 1 unless items entity @p[tag=xlib_target] weapon.mainhand #mech_train:prevent_reset2 run scoreboard players set @s resetcd 4
execute if score @s[scores={pops=2..,resetcd=0}] reset_trigger matches 1 unless entity @a[tag=xlib_target,distance=..4.5] run scoreboard players set @s resetcd 4
execute if score @s resetcd matches 1 run function mech_train:crystal/hit_anchor/loop