execute unless score .refill toggles matches 1 as @e[type=item_display,name="Refill Items"] run data modify entity @s CustomName set value {"text":"Refill Items","color": "#00ff00"}
execute if score .refill toggles matches 1 as @e[type=item_display,name="Refill Items"] run data modify entity @s CustomName set value {"text":"Refill Items","color": "#ff0000"}

execute if score .refill toggles matches 1 run return run function quantum:options/toggles/refill_off
execute unless score .refill toggles matches 1 run return run function quantum:options/toggles/refill_on