package com.rumilance.practice.craft;

import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Server-wide crafting restriction: only log -&gt; planks crafting is allowed.
 * Recipe results are suppressed at the prepare stage, which blocks normal clicks,
 * shift-clicks and number-key moves in one place (no {@code CraftItemEvent} reaches
 * an empty result slot).
 *
 * <p>Exemption: operators currently in {@link PlayerState#LOBBY} (admin/build work in
 * the lobby world) keep the full vanilla recipe book. OPs inside matches/practice/FFA
 * are restricted like everyone else.</p>
 */
public final class CraftRestrictionListener implements Listener {

    private final PlayerStateManager stateManager;

    public CraftRestrictionListener(PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }
        ItemStack result = recipe.getResult();
        if (result == null || CraftingAllowance.isPlanksResult(result.getType())) {
            return; // log -> planks stays available for everyone
        }
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player && isExempt(player)) {
                return; // lobby OP exemption — vanilla behaviour untouched
            }
        }
        event.getInventory().setResult(null);
    }

    private boolean isExempt(Player player) {
        return player.isOp() && stateManager.getState(player.getUniqueId()) == PlayerState.LOBBY;
    }
}
