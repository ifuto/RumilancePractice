execute if score .triple_tap toggles matches 0 if score @s crystal2 matches 2.. unless score @s Pos1_difference matches ..-2 if score .crystal_playstyle toggles matches 2 if score .anchors toggles matches 1 run return 0
execute if score .triple_tap toggles matches 1 if score @s crystal2 matches 3.. unless score @s Pos1_difference matches ..-2 if score .crystal_playstyle toggles matches 2 if score .anchors toggles matches 1 run return 0
execute unless entity @e[tag=crystal_2,tag=usable,distance=0..,type=marker] run function xaniclelib:crystal/no_obby

execute if score .clip toggles matches 1 if score @s crystal3 matches 2.. run return run scoreboard players set @s crystal3 0
execute unless score @s hitcd matches 7 run fill ~-3 ~-0.7 ~-3 ~3 ~-4 ~3 command_block{auto:1b,Command:"function xaniclelib:obbycheck"} replace obsidian
execute if score @s hitcd matches 7 run fill ~-3 ~ ~-3 ~3 ~ ~3 command_block{auto:1b,Command:"function xaniclelib:obbycheck"} replace obsidian
# execute if score @s Pos1 matches ..-58 run fill ~-3 ~-0.7 ~-3 ~3 ~-4 ~3 command_block{auto:1b,Command:"function xaniclelib:bedrockcheck"} replace bedrock

execute if score .clip toggles matches 1 run return 1
execute at @s run fill ~-5 ~-1 ~-5 ~5 ~-2 ~5 obsidian replace command_block{auto:1b,Command:"function xaniclelib:obbycheck"}