scoreboard players add @s[scores={crystal_speed_tick_timer=1..}] crystal_speed_tick_timer 1
execute unless items entity @s weapon.mainhand end_crystal run scoreboard players set @s crystal_speed_tick_timer 0
scoreboard players add @s[scores={anchor_speed_tick_timer=1..}] anchor_speed_tick_timer 1
execute unless items entity @s weapon.mainhand #stats:anchors if items entity @s weapon.mainhand * run scoreboard players set @s anchor_speed_tick_timer 0