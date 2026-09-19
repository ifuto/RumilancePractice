execute if entity @s[tag=!pot,scores={bowcharge=..0,windcd=..18,using_item=0}] run function quantum:look
player @s move
player @s sprint
player @s move forward
function quantum:sword/bot_mech/set_offhand
execute at @s[scores={in_range=1}] if entity @a[tag=xlib_target,scores={in_cobweb_decision=1}] run player @s move
execute unless score @s bowcharge matches 1.. unless entity @s[tag=pot] if score @s windcd matches ..18 unless score @s using_item matches 1.. run player @s hotbar 4
execute at @s[scores={OnGround=1}] rotated ~ 0 unless block ^ ^ ^1 #xaniclelib:nonsolid2 if block ^ ^1 ^1 #xaniclelib:nonsolid2 unless score @s hit_decision_without_cd matches 1 run player @s jump once