execute if score .difficulty difficulty matches 0 at @s run return run function quantum:crystal/difficulty0
function quantum:crystal/hardcode/totem
execute if score @s totem_timer matches 1.. run return 0
function quantum:look
player @s sprint
player @s move forward
player @s hotbar 4
function quantum:crystal/shield/axe
execute if score .strafe toggles matches 1 run function quantum:sword/bot_mech/strafe
execute at @s if entity @a[tag=xlib_target,distance=..2] run player @s move
execute at @s if entity @e[tag=hardcode,distance=..2,type=marker] run player @s move
execute at @s if entity @e[tag=anchor,type=marker,distance=..4] run player @s move
execute at @e[tag=crystal,type=marker] facing entity @s feet rotated ~ 0.0 run player @s look at ^ ^1 ^.5
execute at @s unless entity @a[tag=xlib_target,distance=..3] unless score @s pearlcd matches 1.. unless entity @e[tag=hardcode,type=marker] at @p[tag=xlib_target] unless block ~ ~-.2 ~ #xaniclelib:nonsolid run function quantum:g1gc/pearl
execute at @s[scores={hitcd=..0}] if entity @a[tag=xlib_target,distance=..3,scores={OnGround=1}] unless entity @e[tag=hardcode,type=marker] run function quantum:crystal/hardcode/mech/hit
execute at @s unless block ~ ~-.2 ~ #xaniclelib:nonsolid at @e[tag=crystal,type=marker,distance=..5] run function quantum:crystal/hardcode/mech/breakcrystal
# execute at @s if score .random random matches ..49 at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 positioned ^1 ^ ^-1 as @n[tag=anchor,type=marker] run tag @s add chosen
# execute at @s if score .random random matches 50.. at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 positioned ^-1 ^ ^-1 as @n[tag=anchor,type=marker] run tag @s add chosen
# kill @e[tag=anchor,type=marker,tag=!chosen]
# execute at @s at @p[tag=xlib_target] at @n[tag=anchor,type=marker] run function quantum:crystal/hardcode/mech/anchor2
# execute at @s at @p[tag=xlib_target] at @e[tag=obby_anchor,type=marker] run function quantum:crystal/hardcode/mech/anchor
execute at @e[tag=crystal,type=marker,scores={crystal=2..}] run function quantum:crystal/hardcode/mark/kill
execute if score .difficulty difficulty matches ..2 at @e[tag=crystal,type=marker,scores={crystal=1..}] run function quantum:crystal/hardcode/mark/kill