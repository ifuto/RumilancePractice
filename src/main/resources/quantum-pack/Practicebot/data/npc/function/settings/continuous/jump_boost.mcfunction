effect clear @a[tag=xlib_target] jump_boost
title @a actionbar [{"text":"Player jump boost has been set to: ","color":"aqua"},{"score":{"name": ".jump_boost","objective": "var"},"color":"gold"}]
$effect give @a[tag=xlib_target] jump_boost infinite $(jump_boost)