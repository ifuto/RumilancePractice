# Minecraft 効果音リファレンス (Java Edition 1.21)

`/playsound` や `SoundService`（`sounds.yml` の `key`）で使える主な音を用途別にまとめたリストです。
キーは `minecraft:` を省略した `名前空間` 形式（例: `entity.player.levelup`）。Pitch は 0.5〜2.0 が実用範囲。

## プラグインでよく使う音（おすすめ）

| 用途 | おすすめキー | 備考 |
|---|---|---|
| 成功・承認 | `entity.player.levelup` | 勝利演出・レベルアップのキラーン |
| 選択決定 | `entity.experience_orb.pickup` | ポンッと軽い確認音 |
| 通知・合図 | `block.note_block.pling` / `block.note_block.chime` | デュエル申請の通知に |
| 成功フィードバック | `block.note_block.pling` | 軽やかな成功 |
| エラー・拒否 | `block.note_block.bass` (pitch 0.5) / `entity.item.break` | 低く沈む音 |
| 開始・衝撃 | `item.mace.smash_ground_heavy` / `entity.generic.explode` | FIGHT! などの重い一撃 |
| 終了・着地 | `block.anvil.land` / `block.anvil.destroy` | 試合終了の重い音 |
| クリック/UI | `ui.button.click` / `block.note_block.hat` | ボタン・スロット操作 |
| チャイム系 | `block.beacon.power_select` / `block.beacon.activate` | 厳かでおめでたい |
| 花火・祝福 | `entity.firework_rocket.launch` / `entity.firework_rocket.blast` | ランクアップ演出 |
| 入場 | `entity.item.pickup` / `block.portal.travel` | 参加時 |
| 離脱・解除 | `entity.item.break` / `block.note_block.snare` | キャンセル系 |

---

## カテゴリ別 全音リスト

### UI / システム
```
ui.button.click
ui.loom_select_pattern
ui.loom_take_result
ui.cartography_table_take_result
ui.stonecutter.take_result
ui.toast.in
ui.toast.out
ui.toast.challenge_complete
```

### ノートブロック（楽器）
```
block.note_block.banjo / basedrum / bass / bell / bit
block.note_block.chime / cow_bell / didgeridoo / flute / guitar
block.note_block.harp / hat / iron_xylophone / pling / snare / xylophone
```

### アンビエント・天候
```
ambient.cave
ambient.basalt_deltas.additions / loop / mood
ambient.crimson_forest.additions / loop / mood
ambient.nether_wastes.additions / loop / mood
ambient.soul_sand_valley.additions / loop / mood
ambient.warped_forest.additions / loop / mood
ambient.underwater.enter / exit / loop / loop.additions / loop.additions.rare / loop.additions.ultra_rare
ambient.ancient_city.additions / loop / mood / wysper
weather.rain / rain.above
weather.thunder
intentionally_designated_weather.rumble_thunder
lightning.impact / lightning.bolt.thunder
```

### ブロック - 汎用系（金属/石/木/土 などに break/fall/hit/place/step が付く）
```
block.anvil.break / destroy / fall / hit / land / place / step / use
block.beacon.activate / ambient / deactivate / power_select
block.bell.use / resonate
block.blastfurnace.fire_crackle
block.brewing_stand.brew
block.campfire.crackle
block.chest.close / locked / open
block.composter.empty / fill / fill_success / ready
block.conduit.activate / ambient / ambient.short / attack.target / deactivate
block.dispenser.dispense / fail / launch
block.enchantment_table.use
block.end_portal.spawn
block.end_portal_frame.fill
block.ender_chest.close / open
block.fence_gate.close / open
block.fire.ambient / extinguish
block.furnace.fire_crackle
block.glass.break / fall / hit / place / step
block.grass.break / fall / hit / place / step
block.grindstone.use
block.honey_block.break / slide / step
block.iron_door.close / open
block.iron_trapdoor.close / open
block.ladder.break / fall / hit / place / step
block.lantern.break / fall / hit / place / step
block.lava.ambient / extinguish / pop
block.lever.click
block.lily_pad.place
block.metal.break / fall / hit / place / step
block.metal_pressure_plate.click_off / click_on
block.piston.contract / extend
block.portal.ambient / travel / trigger
block.pumpkin.carve
block.redstone_torch.burnout
block.respawn_anchor.charge / deplete / set_spawn
block.sand.break / fall / hit / place / step
block.scaffolding.break / fall / hit / place / step
block.shroomlight.break / fall / hit / place / step
block.slime_block.break / fall / hit / place / step
block.smithing_table.use
block.smoker.smoke
block.snow.break / fall / hit / place / step
block.soul_sand.break / fall / hit / place / step
block.sponge.absorb
block.stone.break / fall / hit / place / step
block.sweet_berry_bush.pick_berries / place
block.trial_spawner.about_to_spawn_item / ambient / spawn_item / spawn_mob / detect_player
block.tripwire.attach / click_off / click_on / detach / remove
block.vault.activate / deactivate / insert_item / open_shutter / close_shutter / eject_item
block.water.ambient
block.wet_grass.break / fall / hit / place / step
block.wood.break / fall / hit / place / step
block.wooden_button.click_off / click_on
block.wooden_door.close / open
block.wooden_pressure_plate.click_off / click_on
block.wooden_trapdoor.close / open
block.wool.break / fall / hit / place / step
block.copper.break / fall / hit / place / step
block.copper_bulb.turn_on / turn_off
block.copper_grid.break / step / place
block.crafter.block / fail / craft
block.creaking_heart.ambient / break / spawn_mob
block.decorated_pot.insert / insert_fail / shatter
block.big_dripleaf.break / tilt_down / tilt_up
block.cave_vines.pick_berries
block.chorus_flower.death / grow
block.comparator.click
block.coral_block.break
block.crop.break
block.fungus.break
block.hanging_roots.break
block.hanging_sign.break / fall / hit / place / step
block.mud.break / fall / hit / place / step
block.mud_bricks.break
block.netherrack.break
block.nether_wart.break
block.netherite_block.break
block.nylium.break
block.pink_petals.break / place / step
block.pointed_dripstone.break / drip_lava / drip_water / fall / hit / place / step
block.powder_snow.break / fall / hit / place / step
block.amethyst_block.break / chime / fall / hit / place / step / resonate
block.anvil.land
block.calcite.break / fall / hit / place / step
block.deepslate.break / fall / hit / place / step
block.dripstone_block.break / fall / hit / place / step
block.hanging_roots.step
block.moss.break / fall / hit / place / step
block.spore_blossom.break
block.tuff.break / fall / hit / place / step
block.tuff_bricks.break
block.tuff_bricks.fall
block.tuff_bricks.hit
block.tuff_bricks.place
block.tuff_bricks.step
block.tuff.calcite_bricks.break
block.rooted_dirt.break / fall / hit / place / step
block.mangrove_roots.break / fall / hit / place / step
block.muddy_mangrove_roots.break / fall / hit / place / step
block.froglight.break / fall / hit / place / step
block.frogspawn.break / hatch
block.lily_pad.place
block.creaking_heart.fall / hit / place / step
block.creaking_heart.trail
block.cherry_leaves.break / fall / hit / place / step
block.cherry_sapling.break / place / step
block.bamboo.break / fall / hit / place / step
block.bamboo_sapling.break / place
block.azalea.break / fall / hit / place / step
block.azalea_leaves.break / fall / hit / place / step
block.big_dripleaf.break / place / step
block.flowering_azalea.break / fall / hit / place / step
block.dried_ghast.ambient / break / place / state_change / step
block.firefly_bush.ambient
block.eyeblossom.open / close / open_long / close_long / ambient
block.leaf_litter.break / place / step
block.dry_grass.ambient.attached
```

### エンティティ - プレイヤー・人型
```
entity.player.attack.crit / attack.knockback / attack.nodamage / attack.slash / attack.strong / swoop
entity.player.attack.weak
entity.player.hurt / hurt_drown / hurt_freeze / hurt_on_fire / hurt_sweet_berry_bush
entity.player.death
entity.player.big_fall / small_fall
entity.player.breath
entity.player.burp
entity.player.splash / splash.high_speed
entity.player.swim
entity.player.levelup
entity.player.experience.death (donate)
```

### エンティティ - 汎用
```
entity.generic.explode
entity.generic.big_fall / small_fall
entity.generic.burn
entity.generic.death
entity.generic.drink / eat
entity.generic.extinguish_fire
entity.generic.hurt
entity.generic.splash / swim
entity.generic.wind_burst
entity.item.pickup
entity.item.break
entity.item_frame.add_item / break / place / remove_item / rotate_item
entity.item_frame.add_item (glow_item_frame.* も同系)
entity.leash_knot.break / place
entity.experience_orb.pickup / throw
entity.fishing_bobber.retrieve / splash / throw
entity.lightning_bolt.impact / thunder
```

### エンティティ - 爆発/火薬/武器系
```
entity.firework_rocket.blast / blast_far / large_blast / large_blast_far / launch / shoot / twinkle / twinkle_far
entity.arrow.hit / hit_player / shoot
entity.breeze.charge / deflect / shoot / wind_burst
entity.dragon_fireball.explode
entity.fireball.impact / shoot
entity.small_fireball.impact / shoot
entity.wither.shoot
entity.shulker.shoot
entity.llama.spit
entity.evoker_fangs.attack
entity.fox.spit
```

### エンティティ - モブ（鳴き声・死亡など多数。代表的なもの）
```
entity.allay.ambient_without_item / ambient_with_item / death / hurt / item_given / item_taken / item_thrown
entity.armadillo.brush / death / eat / hurt / peel / roll / step / unroll
entity.axolotl.attack / death / hurt / idle_water / play_dead / splash
entity.bat.ambient / death / hurt / loop / takeoff
entity.bee.loop / loop_aggressive / death / hurt / sting / pollinate / nectar
entity.blaze.ambient / burn / death / hurt / shoot
entity.cat.hiss / hail / purr / purreow / death / hurt / ambient / eat / stray_ambient
entity.camel.dash / death / eat / hurt / saddle / sit / stand / step
entity.chicken.ambient / death / hurt / step / egg
entity.cod.ambient / death / hurt / flop
entity.cow.ambient / death / hurt / milk / step / step (long/short variants)
entity.creeper.hurt / death / primed
entity.dolphin.ambient / ambient_water / attack / death / hurt / splash / swim / eat / jump
entity.donkey.ambient / angry / chest / death / hurt
entity.drowned.ambient / ambient_water / death / hurt / step / shoot
entity.elder_guardian.ambient / ambient_land / curse / death / death_land / hurt / hurt_land / flop
entity.ender_dragon.ambient / death / flap / growl / hurt / shoot
entity.enderman.ambient / death / hurt / scream / stare / teleport
entity.endermite.ambient / death / hurt / step
entity.evoker.ambient / cast_spell / celebrate / death / hurt / prepare_summon / prepare_wololo
entity.fox.ambient / death / hurt / snore / sleep / spit / screech / eat / bite / aggro
entity.frog.ambient / death / hurt / long_jump / step / tongue / eat
entity.ghast.ambient / death / hurt / scream / shoot / warn
entity.glow_squid.ambient / death / hurt / squirt
entity.goat.ambient / death / hurt / screaming.ambient / milking / eat / long_jump / ram_impact / prepare_ram
entity.guardian.ambient / ambient_land / attack / death / death_land / flop / hurt / hurt_land
entity.hoglin.ambient / angry / attack / death / hurt / step / retreat
entity.horse.ambient / angry / armor / breathe / death / eat / gallop / hurt / jump / land / step / saddle
entity.husk.ambient / death / hurt / step / converted_to_zombie
entity.iron_golem.attack / damage / death / hurt / repair / step
entity.llama.ambient / angry / chest / death / eat / hurt / step / swagger / spit
entity.magma_cube.jump / squish / death / hurt
entity.mooshroom.suspicious_milk / convert / shear
entity.ocelot.ambient / death / hurt
entity.panda.bite / death / hurt / pre_sneeze / sneeze / step / ambient / aggressive / worried / eat / cannibalize
entity.parrot.ambient / death / hurt / fly / step / eat / mimic
entity.phantom.ambient / death / hurt / flap / swoop / bite
entity.pig.ambient / death / hurt / saddle / step / death
entity.piglin.ambient / angry / celebrating / death / hurt / retreating / jealously / admiring_item
entity.piglin_brute.ambient / angry / death / hurt / step
entity.pillager.ambient / celebrating / death / hurt
entity.polar_bear.ambient / baby_ambient / death / hurt / step
entity.puffer_fish.blow_out / blow_up / death / flop / hurt / sting
entity.rabbit.ambient / attack / death / hurt / jump
entity.ravager.ambient / attack / death / hurt / roar / step / stunned / celebrate
entity.salmon.ambient / death / hurt / flop
entity.sheep.ambient / death / hurt / shear / step
entity.shulker.ambient / close / death / hurt / open / shoot / teleport
entity.silverfish.ambient / death / hurt / step
entity.skeleton.ambient / death / hurt / step / shoot
entity.skeleton_horse.ambient / death / hurt / step
entity.slime.attack / death / hurt / jump / squash / squish / step
entity.sniffer.digging / digging_long / happy / death / hurt / step / egg_crack / sniffing / drop_seedling / scenting / search_ambient / sploot
entity.strider.ambient / happy / death / hurt / retreat / step / eat
entity.squid.ambient / death / hurt / squirt
entity.stray.ambient / death / hurt / step
entity.tadpole.death / flop / grow_up / hurt
entity.tropical_fish.ambient / death / hurt / flop
entity.turtle.ambient / death / hurt / step / swim / lay_egg / born / shamble / egg
entity.vex.ambient / death / hurt / charge
entity.villager.ambient / death / hurt / no / yes / celebrate / work_completing
entity.vindicator.ambient / celebrating / death / hurt
entity.wandering_trader.ambient / death / hurt / no / yes / drink / drink_milk / reappeared / disappeared
entity.warden.ambient / angry / listening / heartbeat / death / hurt / dig / emerge / roar / sonic_boon / sniff / step / attack_impact / agitated / nearby_close / nearby_closer
entity.witch.ambient / death / hurt / celebrate / drink / throw
entity.wither.ambient / death / hurt / shoot / spawn / break_block
entity.wolf.ambient / death / hurt / growl / howl / pant / shake / step / whine
entity.zoglin.ambient / angry / attack / death / hurt / step
entity.zombie.ambient / death / hurt / step / infect / remedy / unfect / villager_converted / convert_to_drowned / wood / woodbreak
entity.zombie_horse.ambient / death / hurt
entity.zombie_villager.ambient / death / hurt / step / cured
```

### アイテム系
```
item.armor.equip_generic / equip_chain / equip_diamond / equip_iron / equip_gold / equip_leather / equip_netherite / equip_elytra / equip_turtle
item.armor.equip_wolf
item.axe.scrape / strip / wax_off
item.book.page_turn / put
item.bottle.empty / fill_dragonbreath / fill
item.bucket.empty / fill / empty_powder_snow / fill_powder_snow / empty_lava / fill_lava / empty_water / fill_water
item.bundle.drop_contents / insert / remove_one
item.chorus_fruit.teleport
item.crop.plant
item.crossbow.hit / loading_end / loading_middle / loading_start / quick_charge / shoot
item.dye.use
item.elytra.flying
item.firecharge.use
item.flintandsteel.use
item.glow_ink_sac.use
item.goat_horn.play / sound_0..sound_7
item.hoe.till
item.honey_bottle.drink
item.ink_sac.use
item.lodestone_compass.lock
item.mace.smash_ground_heavy / smash_ground_light
item.nether_wart.plant
item.shield.block / break
item.shovel.flatten
item.spyglass.stop_using / use
item.totem.use
item.trident.hit / hit_ground / throw / return / riptide_1..3 / throw / spin
item.sweet_berry_bush.pick_berries
```

### その他
```
entity.zombie.infect
music.* (BGM 音源、曲ごとに多数)
music.creative / music.credits / music.dragon / music.end / music.game / music.menu / music.nether.* / music.overworld.* / music underwater
particle.soul_escape
event.mob_effect.*
event.raid.horn
event.mob_effect.bad_omen / raid_omen / trial_omen / hero_of_the_village
ui.toast.*
```

## pitch の目安
- `0.5〜0.8`: 低く重い（エラー・終了）
- `1.0`: 標準
- `1.2〜1.6`: 高く軽い（クリック・成功）
- `2.0`: 非常に高い（キラキラ感）

> 備考: 一部の音はバージョンによって追加/改名されています。このリストは 1.21 時点を基準にしています。
> `/playsound <key> master @p ~ ~ ~ 1 1` でゲーム内試聴できます。
