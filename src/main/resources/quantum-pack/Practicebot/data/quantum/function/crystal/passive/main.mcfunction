player @s move

# Counter mace
execute if score @p[tag=xlib_target] pos matches 1 if function quantum:crystal/passive/mace/defense_main run return 1

# Escape
execute if entity @a[tag=xlib_target,distance=..8] unless score @s[tag=close] pearlcd matches 1.. run return run function quantum:crystal/passive/escape/pearl
execute if score @s eval matches ..-220 if entity @a[tag=xlib_target,distance=8..15] unless score @s pearlcd matches 1.. run return run function quantum:crystal/passive/escape/pearl
execute unless score @s eval matches ..-220 if entity @a[tag=xlib_target,distance=8..10] unless score @s pearlcd matches 1.. run return run function quantum:crystal/passive/escape/pearl


# Shield
execute if score .shield toggles matches 1 if entity @a[tag=xlib_target,distance=..10] as @s[scores={shield_cd=..0}] run return run function quantum:crystal/passive/shield/main

# Block
execute if entity @a[tag=xlib_target,distance=..10] run return run function quantum:crystal/passive/block/main

# Heal/crossbow
return run function quantum:crystal/passive/aggression0