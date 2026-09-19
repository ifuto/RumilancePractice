function quantum:bin/40

execute at @s positioned ~-16 ~ ~-30 as @e[nbt={CustomNameVisible:1b},distance=0..] if entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 1f
execute at @s positioned ~-16 ~ ~-30 as @e[nbt={CustomNameVisible:1b},distance=0..] if entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 1f
execute at @s positioned ~-16 ~ ~-30 as @e[nbt={CustomNameVisible:1b},distance=0..] if entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 1f
execute at @s positioned ~-16 ~ ~-21 as @e[nbt={CustomNameVisible:1b},distance=0..] if entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 1f
execute at @s positioned ~-16 ~ ~-21 as @e[nbt={CustomNameVisible:1b},distance=0..] if entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 1f

execute at @s positioned ~-16 ~ ~-30 as @e[nbt={CustomNameVisible:1b},distance=0..] unless entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 0f
execute at @s positioned ~-16 ~ ~-30 as @e[nbt={CustomNameVisible:1b},distance=0..] unless entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 0f
execute at @s positioned ~-16 ~ ~-30 as @e[nbt={CustomNameVisible:1b},distance=0..] unless entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 0f
execute at @s positioned ~-16 ~ ~-21 as @e[nbt={CustomNameVisible:1b},distance=0..] unless entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 0f
execute at @s positioned ~-16 ~ ~-21 as @e[nbt={CustomNameVisible:1b},distance=0..] unless entity @s[dx=31,dy=10,dz=60] run data modify entity @s view_range set value 0f

execute positioned -656.5 55.00 76.50 as @e[nbt={CustomNameVisible:1b},distance=0..] positioned ~-10 ~ ~-10 if entity @s[dx=20,dy=10,dz=20] run data modify entity @s view_range set value 1f