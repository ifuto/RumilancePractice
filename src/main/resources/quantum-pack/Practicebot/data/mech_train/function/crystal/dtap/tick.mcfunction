function mech_train:crystal/common_actions
execute if entity @s[nbt={OnGround:1b},scores={hitcd=0}] if entity @a[tag=xlib_target,distance=..3] run scoreboard players set @s hitcd 7
execute if block ~ ~-.2 ~ #xaniclelib:nonsolid run scoreboard players set @s reset_trigger 1
execute if score @s reset_trigger matches 1 if score .shield toggles matches 1 run function quantum:crystal/passive/shield/main
execute if score @s hitcd matches 1 unless score @s reset_trigger matches 1 run scoreboard players set @s resetcd 1
execute unless score .dbp toggles matches 1 if score @s[nbt={OnGround:1b},scores={resetcd=0}] reset_trigger matches 1 run scoreboard players set @s resetcd 5
execute if score @s[nbt={OnGround:1b},scores={resetcd=0}] reset_trigger matches 1 if score .dbp toggles matches 1 run scoreboard players set @s resetcd 10
execute unless score @s hitcd matches 1.. if items entity @p[tag=xlib_target] weapon.mainhand #mech_train:prevent_reset run scoreboard players set @s resetcd 0
execute if score @s resetcd matches 1 run function mech_train:crystal/dtap/loop