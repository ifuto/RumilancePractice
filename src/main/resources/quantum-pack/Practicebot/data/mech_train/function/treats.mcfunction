effect give @a saturation 1 255
player @s sprint
item modify entity @a weapon.mainhand quantum:refill
item modify entity @a weapon.offhand quantum:refill
execute if items entity @a[tag=xlib_target] weapon.mainhand netherite_sword run title @a actionbar [{"text":"Drop"},{"text":" Netherite Sword ","color":"light_purple"},{"text":"to go back to hub"}]
execute as @a store result score @s Pos1 run data get entity @s Pos[1]
function eval:stats/pos1
effect give @a fire_resistance infinite 1 true
execute if score .mode mode matches 200..299 if score .slowfall toggles matches 1 if score @s pops matches 0 run effect give @a slow_falling 1 1 true
execute as @e[type=ender_pearl] on origin if entity @s[tag=xlib_bot] unless score @s pearlcd matches 2.. run scoreboard players set @s pearlcd 2