kill @e[tag=hardcode,distance=0..,type=marker]
$execute positioned ~ $(Pos1) ~ run fill ~-3 ~ ~-3 ~3 ~-1 ~3 command_block{auto:1b,Command:"function quantum:crystal/hardcode/mark/aircmd"} replace #xaniclelib:nonsolid
$execute positioned ~ $(Pos1) ~ run fill ~-3 ~ ~-3 ~3 ~-1 ~3 command_block{auto:1b,Command:"function quantum:crystal/hardcode/mark/obbycmd"} replace obsidian

execute unless score .selfdmg toggles matches 1 run fill ~-4 ~-1 ~-4 ~4 ~-2 ~4 air replace command_block{auto:1b,Command:"function quantum:crystal/hardcode/mark/aircmd"}
execute unless score .selfdmg toggles matches 1 run fill ~-4 ~-1 ~-4 ~4 ~-2 ~4 obsidian replace command_block{auto:1b,Command:"function quantum:crystal/hardcode/mark/obbycmd"}