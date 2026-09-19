effect give @a[scores={hunger=..12}] saturation 1 1 true
execute if function quantum:hub/30t run function quantum:bin/20
execute if score .buffs toggles matches 1 run function quantum:miscellaneous/treats/buffs
execute if items entity @s weapon.mainhand player_head run gamemode adventure @s[gamemode=survival]
execute unless items entity @s weapon.mainhand player_head run gamemode survival @s[gamemode=adventure]
execute as @a[tag=xlib_bot] run function quantum:bin/19
execute if score .refill toggles matches 1 as @a[tag=xlib_target] run function quantum:miscellaneous/treats/refill
execute if score .terrain terrain matches 0 in overworld run setblock 11 34 10 minecraft:stone_button[face=floor,facing=north,powered=false] replace
execute if score .slowfall toggles matches 1 if score .mode mode matches 2 run effect give @a[scores={OnGround=1}] slow_falling infinite 0 false
execute at @n[type=snowball] positioned over motion_blocking_no_leaves run tp @a ~ ~ ~
kill @e[type=#quantum:magic_items,distance=0..]
scoreboard players set @a pearl_count 0
execute as @e[type=ender_pearl] at @s on origin run function quantum:bin/8

execute as @e[type=creeper] run data modify entity @s ignited set value 1b
kill @e[type=#arrows,nbt={inGround:1b},distance=0..]
execute as @a[tag=xlib_target,scores={rk=1..}] run function quantum:miscellaneous/rekit


#> ####################################> FINISH
# execute if score .slow_death toggles matches 1 at @a[tag=xlib_target,scores={death=1},limit=1] run player @s look at ~ ~1 ~
# execute if score .slow_death toggles matches 1 as @a[tag=xlib_target,scores={death=1},limit=1] unless items entity @s armor.head #enchantable/armor run return run function quantum:kits/loadkit
execute if score .blocks_drop toggles matches 0 run kill @e[distance=0..,type=item]