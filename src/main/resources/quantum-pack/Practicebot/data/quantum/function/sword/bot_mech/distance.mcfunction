# W-tap
execute unless score .tap chance >= .random random run return 0
execute at @s if entity @p[tag=xlib_target,distance=..1.8] run player @s move

execute if score .buffs toggles matches 1 if score @s tempshield matches 1 if score .stap toggles matches 0 if score @s hitcd matches 2.. if score @s shield_cd matches 1.. run player @s move
execute if score .stap toggles matches 0 if score @s tempshield matches 1 if score @s real_hitcd matches 5.. if score @s shield_cd matches 1.. run player @s move
execute at @s[scores={real_hitcd=5..}] if score .buffs toggles matches 0 if score .stap toggles matches 0 run player @s move
execute at @s[scores={real_hitcd=2..}] if score .buffs toggles matches 1 if score .stap toggles matches 0 run player @s move

# S tap
# execute as @s[scores={hitcd=5..}] if score .stap toggles matches 1 if score .buffs toggles matches 1 run player @s move backward
# execute as @s[scores={hitcd=7..}] at @s facing entity @p[tag=xlib_target] feet positioned ^ ^ ^1.6836 if score @s in_range matches 1 unless score @s tempshield matches 1 if score .stap toggles matches 1 run player @s move backward
execute if entity @p[tag=xlib_target,predicate=quantum:half_neth] if score @s[scores={hitcd=4}] distance_to_target < .tempreach reach run scoreboard players add @s real_hitcd 1
execute unless entity @p[tag=xlib_target,predicate=quantum:half_neth] if score @s[scores={hitcd=6}] distance_to_target < .tempreach reach run scoreboard players add @s real_hitcd 1
execute at @s[scores={real_hitcd=5..}] if entity @p[tag=xlib_target,predicate=quantum:half_neth] if score .stap toggles matches 1 run player @s move backward
execute at @s[scores={real_hitcd=7..}] if score .stap toggles matches 1 run player @s move backward
execute at @s[scores={real_hitcd=7..}] if score .mode mode matches 6 run player @s move backward
execute at @s[scores={real_hitcd=7..}] if score .mode mode matches 3 run player @s move backward