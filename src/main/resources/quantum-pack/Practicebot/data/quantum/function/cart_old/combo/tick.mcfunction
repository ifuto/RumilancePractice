# Health
effect give @s[tag=shield] resistance 2 4 true
item replace entity @s[tag=!shield] weapon.offhand with totem_of_undying

# Sword
function quantum:sword/combo/tick
execute if score @s bowcharge matches 1 run player quantumbot use
execute if score @s bowcharge matches 5.. run player quantumbot move backward

# Look at cart
execute if score @s bowcharge matches 1.. run execute at @s at @p[tag=xlib_target] at @e[type=tnt_minecart,limit=1,sort=nearest] run player quantumbot look at ~ ~1.5 ~

# Cart
execute at @s at @p[tag=xlib_target] at @e[tag=rail,type=marker,limit=1,sort=nearest] run setblock ~ ~ ~ powered_rail
execute at @s at @p[tag=xlib_target] at @e[tag=rail,type=marker,limit=1,sort=nearest] run summon tnt_minecart ~ ~ ~
execute at @s at @p[tag=xlib_target] at @e[tag=rail,type=marker,limit=1,sort=nearest] run function quantum:cart/charge

# Mark
kill @e[type=marker,tag=cartlib]
execute if score @s rail_timer matches 1.. run return 0
execute as @s[nbt={OnGround:1b}] at @s if entity @p[tag=xlib_target,distance=3.6..7] unless entity @e[type=arrow,nbt={inGround:0b}] at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 run fill ^2 ^1 ^2 ^-2 ^-1 ^ command_block{auto:1b,Command:"function quantum:cart/mark"} replace #xaniclelib:nonsolid