function mech_train:mace/common_actions
execute facing entity @p[tag=xlib_target] eyes rotated 0.0 ~ run player @s look at ^ ^ ^1
execute unless score .shield toggles matches 1 run function quantum:look
execute if score .shield toggles matches 1 run item replace entity @s hotbar.0 with shield[unbreakable={},enchantment_glint_override=true]
player @s hotbar 1
execute if score .shield toggles matches 1 unless score @s shield_cd matches 1.. run player @s use continuous
execute if entity @a[tag=xlib_target,nbt={HurtTime:10s}] run scoreboard players set @s[scores={resetcd=0}] resetcd 7
execute if entity @a[tag=xlib_bot,nbt={HurtTime:10s},scores={resetcd=0}] run scoreboard players set @s resetcd 3
execute as @e[type=ender_pearl] at @s unless block ~ ~-1 ~ #xaniclelib:nonsolid run kill
execute if score @s resetcd matches 1 run function mech_train:mace/far_pearl/loop