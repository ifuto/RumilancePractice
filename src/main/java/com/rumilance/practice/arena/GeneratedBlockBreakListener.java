package com.rumilance.practice.arena;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.util.PlayerPlacedBlockTracker;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/**
 * 「プレイヤーが設置したブロックだけ破壊可」モード中でも、
 * **溶岩×水で自然生成した丸石・黒曜石は破壊できるようにする**専用リスナー
 * (ユーザー要望 2026-09-29:「溶岩と水を使用して出した丸石や黒曜石も壊せるようにして」)。
 *
 * <p>{@link BlockFormEvent}(液体生成)で、新ブロックが丸石・黒曜石のとき、
 * 対象ブロック位置が FFA アリーナまたは進行中マッチ領域に含まれていれば、
 * プレイヤー設置と同様に {@link PlayerPlacedBlockTracker} の当該スコープへ登録する。
 * その結果、プレイヤー設置オンのみ壊せるルールでも生成資材が自然に再利用できる。</p>
 */
public final class GeneratedBlockBreakListener implements Listener {

    private final FfaService ffaService;
    private final MatchService matchService;
    private final PlayerPlacedBlockTracker playerPlacedBlocks;

    public GeneratedBlockBreakListener(FfaService ffaService, MatchService matchService,
                                       PlayerPlacedBlockTracker playerPlacedBlocks) {
        this.ffaService = ffaService;
        this.matchService = matchService;
        this.playerPlacedBlocks = playerPlacedBlocks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Material type = event.getNewState().getType();
        if (type != Material.COBBLESTONE && type != Material.OBSIDIAN) {
            return; // ユーザー指定対象のみ(溶岩×水の生成資材)
        }
        Block block = event.getBlock();
        if (block == null) {
            return;
        }
        if (playerPlacedBlocks == null) {
            return;
        }
        Location loc = block.getLocation();
        // FFA arena scope ("ffa:<id>")
        if (ffaService != null) {
            String arenaId = ffaService.arenaIdContaining(loc);
            if (arenaId != null) {
                playerPlacedBlocks.mark(block, "ffa:" + arenaId);
                return;
            }
        }
        // Live match scope (session-id string, as {@code MatchListener#onPlace})
        if (matchService != null) {
            for (MatchSession session : matchService.registry().all()) {
                var instance = matchService.arenaService().get(session.arenaInstanceId()).orElse(null);
                if (instance != null && instance.bounds() != null && instance.bounds().contains(loc)) {
                    playerPlacedBlocks.mark(block, session.id().toString());
                    return;
                }
            }
        }
    }
}
