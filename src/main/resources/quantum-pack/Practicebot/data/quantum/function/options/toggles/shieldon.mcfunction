scoreboard players set .shield toggles 1
title @a actionbar [{"text":"SHIELDING","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
item replace entity @a[tag=xlib_bot] weapon.offhand with shield[death_protection={death_effects:[{type:"clear_all_effects"},{type:"apply_effects",effects:[{id:"minecraft:regeneration",amplifier:1,duration:900},{id:"minecraft:fire_resistance",amplifier:0,duration:800},{id:"minecraft:absorption",amplifier:1,duration:100}]}]},enchantments={unbreaking:3,mending:1},unbreakable={}]
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1