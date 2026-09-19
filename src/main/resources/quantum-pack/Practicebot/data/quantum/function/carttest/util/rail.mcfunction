setblock ~ ~ ~ powered_rail
item replace entity @s hotbar.2 with powered_rail[max_stack_size=99] 99
player @s hotbar 2
player @s look at ~ ~ ~
scoreboard players set @s using_item 1