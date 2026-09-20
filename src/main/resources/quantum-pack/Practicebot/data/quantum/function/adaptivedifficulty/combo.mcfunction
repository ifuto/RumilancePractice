advancement revoke @s only quantum:hitplayer
execute if score .mode mode matches 2 run return 0

# Jump reset if damaged
execute as @s[tag=xlib_bot] if score .jumpreset toggles matches 1 if score .difficulty difficulty matches 1.. unless score .mode mode matches 2 unless score .mode mode matches 100.. unless function quantum:sword/pcrit run function quantum:sword/combo/jumpreset
execute as @s[tag=xlib_bot] if score .jumpreset toggles matches 1 if score .difficulty difficulty matches 1.. unless score .mode mode matches 2 unless score .mode mode matches 100.. if score @p[tag=xlib_target] in_cobweb_decision matches 1 run function quantum:sword/combo/jumpreset
execute as @s[tag=xlib_bot] if score .jreset npc matches 1 if score .difficulty difficulty matches 0 unless score .mode mode matches 100.. run function quantum:sword/combo/jumpreset

# If the bot is winning the combo difference will be positive
execute at @s[tag=xlib_target] if score @p[tag=xlib_bot] combo matches ..-1 run scoreboard players set @p[tag=xlib_bpt] combo 0
execute at @s[tag=xlib_target] unless score @p[tag=xlib_bot] combo matches 4.. run scoreboard players add @p[tag=xlib_bot] combo 1

execute if entity @s[tag=xlib_bot] if score @s combo matches 1.. run scoreboard players set @s combo 0
execute if entity @s[tag=xlib_bot] unless score @s combo matches ..-4 run scoreboard players remove @s combo 1

execute unless entity @a[tag=xlib_bot,tag=adapt] run return 0
# execute if score @s[tag=xlib_bot] combo matches 4.. if score @s reach matches 19..33 run scoreboard players remove @s reach 1
# execute if score @s[tag=xlib_bot] combo matches ..-4 if score @s reach matches 18..32 run scoreboard players add @s reach 1

# execute if score @s[tag=xlib_bot] combo matches 4.. if score @s reach matches 19..33 run title @a actionbar [{"text": "Reach", "color": "yellow"},{"text": " DECREASED", "color": "#ffa200"}]
# execute if score @s[tag=xlib_bot] combo matches ..-4 if score @s reach matches 18..32 run title @a actionbar [{"text": "Reach", "color": "yellow"},{"text": " INCREASED", "color": "green"}]

# execute if score @s[tag=xlib_bot] combo matches 4.. if score @s reach matches 19..33 run scoreboard players set @s combo 0
# execute if score @s[tag=xlib_bot] combo matches ..-4 if score @s reach matches 18..32 run scoreboard players set @s combo 0