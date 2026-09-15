# parity:setup/nethpot_k10v10 — mode=nethpot kitA=10 kitB=10 gear=2
function parity:arena_fill
scoreboard players set .difficulty difficulty 2
function quantum:options/nethpot
scoreboard players set .gear toggles 2
scoreboard players set .anchors toggles 1
scoreboard players set .crystals toggles 1
scoreboard players set .crystal_playstyle toggles 2
scoreboard players set .axe toggles 1
scoreboard players set .cobweb toggles 1
scoreboard players set .strafe toggles 1
scoreboard players set .crit toggles 1
scoreboard players set .pcrit toggles 1
scoreboard players set .scrit toggles 1
scoreboard players set .jumpreset toggles 1
scoreboard players set .stun toggles 1
scoreboard players set .triple_tap toggles 1
scoreboard players set .breach toggles 1
scoreboard players set .spear toggles 1
scoreboard players set .lava toggles 1
scoreboard players set .water toggles 1
scoreboard players set .far_pearl toggles 1
scoreboard players set .wind_pearl toggles 1
scoreboard players set .dbp toggles 1
scoreboard players set .refill toggles 1
scoreboard players set .blocks_drop toggles 1
scoreboard players set .inf_tot toggles 1
scoreboard players set .random toggles 1
scoreboard players set .random_mech toggles 1
scoreboard players set .crystal_hardcode toggles 0
playerspawn quantumbot at -646 57 88 facing 0 0 in survival
playerspawn qbot2 at -646 57 88 facing 0 0 in survival
scoreboard players set quantumbot kit 10
scoreboard players set qbot2 kit 10
execute as quantumbot run function quantum:bin/3
execute as qbot2 run function quantum:bin/3
execute as quantumbot run function quantum:botgear/dia
execute as qbot2 run function quantum:botgear/dia
function parity:start_round
scoreboard players set pari_round parity_t 80
