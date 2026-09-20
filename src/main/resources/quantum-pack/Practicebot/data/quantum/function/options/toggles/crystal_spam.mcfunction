title @a actionbar [{"text":"Ledge Spammer","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
execute as @e[type=item_display,name="Ledge Spammer"] run data modify entity @s CustomName set value {"text":"Ledge Spammer","color": "#00ff00"}
execute as @e[type=item_display,name="Anchor spammer"] run data modify entity @s CustomName set value {"text":"Anchor spammer","color": "#ff0000"}
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1