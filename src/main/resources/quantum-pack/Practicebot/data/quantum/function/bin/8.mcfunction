# scoreboard players set @s pearl_count 1
execute if entity @s[tag=xlib_bot] run function quantum:bin/9
execute if score .no_pearl_land toggles matches 1 if score .mode mode matches 3 if entity @s[tag=xlib_target,distance=0..] if function quantum:mark/blockplace run kill @n[distance=0..,type=ender_pearl]
scoreboard players set @s[tag=xlib_target,scores={pearlcd=..7}] pearlcd 7
scoreboard players add @s pearl_count 1