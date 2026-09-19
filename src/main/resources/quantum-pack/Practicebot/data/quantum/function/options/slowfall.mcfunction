execute unless score .slowfall toggles matches 1 as @e[type=item_display,name="Permanent Slowfall"] run data modify entity @s CustomName set value {"text":"Permanent Slowfall","color": "#00ff00"}
execute if score .slowfall toggles matches 1 as @e[type=item_display,name="Permanent Slowfall"] run data modify entity @s CustomName set value {"text":"Permanent Slowfall","color": "#ff0000"}

execute if score .slowfall toggles matches 1 run return run function quantum:options/toggles/slowfalloff
execute unless score .slowfall toggles matches 1 run return run function quantum:options/toggles/slowfallon