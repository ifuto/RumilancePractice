# parity:rescue — 場外/墜落した BOT をアリーナへ戻す(左右で復帰挙動を揃える)
execute as quantumbot at @s unless entity @s[x=-712,y=31,z=76,dx=24,dy=200,dz=24] run tp @s -698.5 31 88.5 90 0
execute as qbot2 at @s unless entity @s[x=-712,y=31,z=76,dx=24,dy=200,dz=24] run tp @s -704.5 31 88.5 -90 0
