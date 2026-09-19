effect clear @a[tag=xlib_target] strength
title @a actionbar [{"text":"player @strength has been set to: ","color":"aqua"},{"score":{"name": ".strength","objective": "var"},"color":"gold"}]
$effect give @a[tag=xlib_target] strength infinite $(strength)