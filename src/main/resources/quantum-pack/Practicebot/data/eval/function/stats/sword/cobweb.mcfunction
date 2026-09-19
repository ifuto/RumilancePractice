scoreboard players set @s eval_cobweb 0
scoreboard players remove @s[scores={in_cobweb_decision=1..}] eval_cobweb 1
execute if score @p[tag=xlib_target] in_cobweb_decision matches 1.. run scoreboard players add @s eval_cobweb 1
scoreboard players operation @s eval_cobweb *= .cobweb factor