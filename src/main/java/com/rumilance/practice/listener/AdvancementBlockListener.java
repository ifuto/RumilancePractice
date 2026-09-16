package com.rumilance.practice.listener;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import net.minecraft.advancements.AdvancementRewards;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.craftbukkit.advancement.CraftAdvancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Optional cosmetic for practice worlds: keep vanilla achievements from popping up. Off by default.
 *
 * <h2>Why this is off by default (parity incident, 2026-09-16)</h2>
 * Cancelling {@link PlayerAdvancementCriterionGrantEvent} does far more than hide a toast. Paper's
 * {@code PlayerAdvancements.award} is:
 *
 * <pre>
 *   if (!progress.grantProgress(criterion)) return false;          // grantProgress already ran…
 *   if (!PlayerAdvancementCriterionGrantEvent.callEvent()) {       // …and is rewound here
 *       progress.revokeProgress(criterion);
 *       return false;                                              // ← and the reward block is skipped
 *   }
 *   if (wasDone || !progress.isDone()) return flag;
 *   …PlayerAdvancementDoneEvent…
 *   advancement.rewards().grant(player);                           // experience + loot + reward function
 * </pre>
 *
 * So a cancelled criterion silently suppresses {@code AdvancementRewards.grant}, i.e. the reward
 * <b>function</b>, loot and experience. The Quantum pack drives its entire hit pipeline through that
 * path ({@code stats/advancement/hit.json} → reward {@code quantum:allstats/advancestats} →
 * {@code real_hitcd} → every item-switch / mace / totem flow), and a Fabric server has no such event
 * at all. Blocking therefore made Paper diverge from the reference bot: `real_hitcd` never rose
 * above 1-2, so no downstream flow ever ran.
 *
 * <p>Rules kept even when the feature is enabled, so it can never eat engine machinery again:
 * <ul>
 *   <li>non-{@code minecraft:} namespaces are never touched — every datapack advancement belongs to
 *       a mechanic, not to a player achievement;</li>
 *   <li>recipe unlocks are never touched (the recipe book stays functional);</li>
 *   <li>anything carrying rewards (function / experience / loot / recipe) is never touched, because
 *       cancelling rewinds the criterion that granted it.</li>
 * </ul>
 */
public final class AdvancementBlockListener implements Listener {

    /** {@code config.yml} → {@code advancements.block-vanilla}. Default false = vanilla parity. */
    public static final String CONFIG_PATH = "advancements.block-vanilla";

    private final boolean blockVanilla;

    public AdvancementBlockListener(boolean blockVanilla) {
        this.blockVanilla = blockVanilla;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        if (!blockVanilla) {
            return;
        }
        NamespacedKey key = event.getAdvancement().getKey();
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return;
        }
        if (key.getKey().startsWith("recipes/")) {
            return;
        }
        if (hasRewards(event.getAdvancement())) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * True when granting this advancement would hand out anything. Bukkit has no reward accessor, so
     * this asks the server-side copy; if the handle is unavailable we assume it has rewards (safer:
     * never cancel something we cannot inspect).
     */
    private static boolean hasRewards(Advancement advancement) {
        if (!(advancement instanceof CraftAdvancement craft) || craft.getHandle() == null) {
            return true;
        }
        AdvancementRewards rewards = craft.getHandle().value().rewards();
        if (rewards == AdvancementRewards.EMPTY) {
            return false;
        }
        return rewards.experience() != 0
                || !rewards.loot().isEmpty()
                || !rewards.recipes().isEmpty()
                || rewards.function().isPresent();
    }
}
