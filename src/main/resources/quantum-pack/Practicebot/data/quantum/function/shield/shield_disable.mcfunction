advancement revoke @s only quantum:shield_disable
player @s itemCd shield set 100
execute if score .shieldcd toggles matches 0 if score .mode mode matches 3 if score .difficulty difficulty matches 1.. run player @s[tag=xlib_bot] itemCd shield set 10
execute if score .mode mode matches 100.. run player @s[tag=xlib_bot] itemCd shield set 10
execute if score .insta_shieldcd npc matches 1 if score .difficulty difficulty matches 0 run player @s[tag=xlib_bot] itemCd shield set 10
execute if score .mode mode matches 200..299 run player @s itemCd shield set 100
execute if entity @s[tag=xlib_bot] run title @p[tag=xlib_target] actionbar [{"text":"Successful","color": "green","italic": true},{"text": " Shield Disable"}]
execute if entity @s[tag=xlib_bot] run player @s stop
execute store result score @s shield_cd run player @s itemCd shield