execute unless score .prompt_activation toggles matches 1 as @e[type=item_display,name="Prompt Activation"] run data modify entity @s CustomName set value {"text":"Prompt Activation","color": "#00ff00"}
execute if score .prompt_activation toggles matches 1 as @e[type=item_display,name="Prompt Activation"] run data modify entity @s CustomName set value {"text":"Prompt Activation","color": "#ff0000"}

execute if score .prompt_activation toggles matches 1 run return run function quantum:options/toggles/prompt_start_off
execute unless score .prompt_activation toggles matches 1 run return run function quantum:options/toggles/prompt_start_on