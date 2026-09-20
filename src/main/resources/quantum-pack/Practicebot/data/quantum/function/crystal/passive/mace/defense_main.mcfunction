execute if score .cobweb toggles matches 1 unless score @s airborne matches 1 unless block ~ ~1 ~ #xaniclelib:nonsolid2 if function quantum:crystal/passive/mace/cobweb_main run return 1
execute unless score .mode mode matches 3 unless score @s shield_cd matches 1.. if score .shield toggles matches 1 run return run function quantum:crystal/passive/shield/main
execute if score .mode mode matches 3 if score .shield toggles matches 1 unless score @s shield_cd matches 1.. run return run function quantum:crystal/passive/shield/main
return 0