execute unless score .pearl_spam toggles matches 1 as @e[type=item_display,name="Pearl spam"] run data modify entity @s CustomName set value {"text":"Pearl spam","color": "#00ff00"}
execute if score .pearl_spam toggles matches 1 as @e[type=item_display,name="Pearl spam"] run data modify entity @s CustomName set value {"text":"Pearl spam","color": "#ff0000"}

execute if score .pearl_spam toggles matches 1 run return run function quantum:options/toggles/pearl_spam_off
execute unless score .pearl_spam toggles matches 1 run return run function quantum:options/toggles/pearl_spam_on