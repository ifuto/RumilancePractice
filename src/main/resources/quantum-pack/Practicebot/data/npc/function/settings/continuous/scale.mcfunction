$title @a actionbar [{"text":"Your scale has been set to:","color":"aqua"},{"text":" $(scale).","color":"gold"},{"text":" Default is 1.0","color":"green"}]
$execute as @a[tag=xlib_target] run attribute @s scale base set $(scale)
$execute as @a[tag=xlib_target] unless score .scale var matches ..10 run attribute @s minecraft:block_interaction_range base set $(scale)