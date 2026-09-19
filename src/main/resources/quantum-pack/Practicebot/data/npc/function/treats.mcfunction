execute at @n[type=snowball] positioned over motion_blocking_no_leaves run tp @a ~ ~ ~
effect give @a fire_resistance 5 0 true
effect give @a[scores={hunger=..12}] saturation 1 1 true
execute as @a[tag=xlib_bot] run function quantum:miscellaneous/treats/refill
execute if score .regen_bot npc matches 1 run effect give @s regeneration infinite 255 true
execute if score .res npc matches 1 run effect give @s resistance infinite 4 true
execute if score .bot_sf npc matches 1 unless score .airborne_sf npc matches 1 run effect give @s slow_falling infinite 0 false
execute if score .airborne_sf npc matches 1 run effect give @s[scores={OnGround=1}] slow_falling infinite 0 false
execute if score .sf npc matches 1 run effect give @a[tag=xlib_target] slow_falling infinite 0 false
execute if score .weakness npc matches 1 run effect give @a[tag=xlib_target] weakness infinite 0
effect give @a[tag=xlib_target] resistance infinite 4 true
kill @e[type=item]
kill @e[type=falling_block]