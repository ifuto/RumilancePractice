execute if score .fast toggles matches 1 run return run execute unless block ~ ~-1 ~ #xaniclelib:nonsolid2
execute if block ~ ~-1 ~ #xaniclelib:nonsolid2 if block ~1 ~ ~ #xaniclelib:nonsolid2 if block ~-1 ~ ~ #xaniclelib:nonsolid2 if block ~ ~ ~1 #xaniclelib:nonsolid2 if block ~ ~ ~-1 #xaniclelib:nonsolid2 run return 0
return 1