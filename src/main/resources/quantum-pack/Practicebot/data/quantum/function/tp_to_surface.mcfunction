execute if entity @s[advancements={quantum:detect_block/in_barrier=true},tag=xlib_target] at @s facing entity @p[tag=xlib_bot] eyes rotated ~ 0.0 run tp @s ^ ^ ^.5
execute if entity @s[advancements={quantum:detect_block/in_barrier=true},tag=xlib_bot] at @s facing entity @p[tag=xlib_target] eyes rotated ~ 0.0 run tp @s ^ ^ ^.5
execute if entity @s[advancements={quantum:detect_block/in_barrier=true}] run return run advancement revoke @s from quantum:detect_block/in_block
advancement revoke @s from quantum:detect_block/in_block
execute at @s[tag=xlib_bot] facing entity @p[tag=xlib_target] eyes run tp @s ^ ^ ^.5