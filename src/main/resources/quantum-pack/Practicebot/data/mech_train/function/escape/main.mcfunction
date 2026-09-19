player @s move

# Escape
execute if entity @a[tag=xlib_target,distance=..15] unless score @s pearlcd matches 1.. unless entity @e[tag=slib,type=marker] if predicate quantum:onground run return run function quantum:crystal/passive/escape/pearl

# Shield
execute if score .shield toggles matches 1 if entity @a[tag=xlib_target,distance=..10] as @s[scores={shield_cd=..0}] run return run function quantum:crystal/passive/shield/main

# Block
execute if entity @a[tag=xlib_target,distance=..10] unless block ~ ~-1 ~ grass_block if predicate quantum:onground run return run function quantum:crystal/passive/block/main