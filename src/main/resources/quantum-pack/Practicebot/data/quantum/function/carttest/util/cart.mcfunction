summon tnt_minecart ~ ~ ~
player @s look at ~ ~ ~
item replace entity @s hotbar.2 with tnt_minecart[max_stack_size=99] 99
player @s hotbar 3
scoreboard players operation @s cart_timer = @s cart_cd
scoreboard players set @s using_item 3