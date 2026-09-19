title @a actionbar [{"text":"ANCHOR SPAMMER","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
execute as @e[type=item_display,name="Ledge Spammer"] run data modify entity @s CustomName set value {"text":"Ledge Spammer","color": "#ff0000"}
execute as @e[type=item_display,name="Anchor spammer"] run data modify entity @s CustomName set value {"text":"Anchor spammer","color": "#00ff00"}
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
