execute if score .cart_speed toggles matches 1 unless score @s bowcharge matches 1.. at @p[tag=xlib_target] run function quantum:carttest/speed/pre_cart
execute if score .cart_speed toggles matches 2 unless score @s bowcharge matches 1.. at @p[tag=xlib_target] run function quantum:carttest/speed/pre_rail
execute if score .cart_speed toggles matches 3 unless score @s bowcharge matches 1.. at @p[tag=xlib_target] run function quantum:carttest/speed/insta

function quantum:sword/combo/tick
execute if score @s state matches 3 run return 0


execute if score @s bowcharge matches 1.. at @s at @p[tag=xlib_target,distance=0..] at @n[tag=cart_look,distance=0..,type=marker] align y run player @s look at ~ ~1.5 ~
execute if score @s bowcharge matches 0 run kill @e[tag=cart_look,distance=0..,type=marker]
execute if score @s bowcharge matches 1 run player @s use
scoreboard players set @s arrows_in_air 0
execute as @e[nbt={inGround:0b},distance=0..,type=#arrows] on origin if entity @s[tag=xlib_bot,distance=0..] run scoreboard players add @s arrows_in_air 1

execute if score @s bowcharge matches 2.. run return 0
execute if score @s arrows_in_air matches 1.. run return run player @s move backward
kill @e[distance=0..,tag=cartlib,type=marker]
tag @e[distance=0..,type=tnt_minecart] remove rail_3
execute as @e[distance=..6,type=tnt_minecart] at @s if entity @a[tag=xlib_target,distance=..5] if function xaniclelib:check/intersect if function xaniclelib:check/raycast if function xaniclelib:check/raycast2 run tag @s add rail_3
execute at @p[tag=xlib_target,distance=..6] unless score @s hit_decision_without_cd matches 1 unless score @p[tag=xlib_target] in_cobweb_decision matches 1 run function quantum:carttest/mark/tick
execute at @p[tag=xlib_target] if score @p[tag=xlib_target] in_cobweb_decision matches 1 run function quantum:carttest/mark/tick

function quantum:decisions/tick2