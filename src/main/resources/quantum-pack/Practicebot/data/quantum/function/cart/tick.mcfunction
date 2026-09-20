# Sword
function quantum:sword/tick
execute if score @s bowcharge matches 1 run player @s use
execute if score @s bowcharge matches 5.. run player @s move backward

# Look at cart
execute if score @s bowcharge matches 1.. run execute at @s at @p[tag=xlib_target] at @n[type=tnt_minecart] run player @s look at ~ ~1.5 ~

# Cart
execute at @s at @p[tag=xlib_target] at @n[tag=rail,type=marker] run setblock ~ ~ ~ powered_rail
execute at @s at @p[tag=xlib_target] at @n[tag=rail,type=marker] run summon tnt_minecart ~ ~ ~
execute at @s at @p[tag=xlib_target] at @n[tag=rail,type=marker] run function quantum:cart/charge

# Mark
kill @e[type=marker,tag=cartlib]
execute if score @s rail_timer matches 1.. run return 0
execute as @s[scores={bowcharge=..0,OnGround=1}] at @s if score @p[tag=xlib_target] in_cobweb_decision matches 1 unless entity @e[type=arrow,nbt={inGround:0b}] at @p[tag=xlib_target,distance=..7] facing entity @s feet rotated ~ 0.0 run fill ^2 ^1 ^2 ^-2 ^-1 ^ command_block{auto:1b,Command:"function quantum:cart/mark"} replace #xaniclelib:nonsolid
execute as @s[scores={bowcharge=..0,OnGround=1}] at @s unless score @s hit_decision_without_cd matches 1 unless score @s crit_decision_without_cd matches 1 unless entity @e[type=arrow,nbt={inGround:0b}] at @p[tag=xlib_target,distance=..7] facing entity @s feet rotated ~ 0.0 run fill ^2 ^1 ^2 ^-2 ^-1 ^ command_block{auto:1b,Command:"function quantum:cart/mark"} replace #xaniclelib:nonsolid

function quantum:decisions/tick2