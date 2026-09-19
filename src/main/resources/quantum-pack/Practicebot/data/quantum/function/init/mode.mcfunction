function quantum:miscellaneous/treats
function quantum:cooldowns

# Crystal
tag @a[tag=xlib_target,tag=checked] remove checked
execute as @a[tag=xlib_bot,scores={death=0}] if score .crystal_hardcode toggles matches 0 if score .mode mode matches 2 at @s run function quantum:crystal/tick
execute as @a[tag=xlib_bot,scores={death=0}] if score .crystal_hardcode toggles matches 1 if score .mode mode matches 2 at @s run function quantum:crystal/hardcode/tick
execute as @a[tag=xlib_bot,scores={death=0}] if score .mode mode matches 2 run return 1

# Sword
execute if score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=1,death=0}] at @s run function quantum:init/crit
execute if score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=0,death=0}] at @s run function quantum:init/combo
execute unless score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=0,death=0}] at @s run function quantum:init/combo
execute unless score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=1,death=0}] at @s run function quantum:init/crit