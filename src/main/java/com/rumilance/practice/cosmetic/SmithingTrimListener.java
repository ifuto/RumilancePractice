package com.rumilance.practice.cosmetic;

import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.gui.menus.SmithingTrimGui;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;

/**
 * VIP right-click armor (in hand) opens the trim picker.
 */
public final class SmithingTrimListener implements Listener {

    private final RankService rankService;
    private final SmithingTrimGui trimGui;
    private final PlayerStateManager stateManager;

    public SmithingTrimListener(RankService rankService, SmithingTrimGui trimGui,
                                PlayerStateManager stateManager) {
        this.rankService = rankService;
        this.trimGui = trimGui;
        this.stateManager = stateManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !(hand.getItemMeta() instanceof ArmorMeta)) {
            return;
        }
        if (!PracticeGuards.isTrimmableArmorMaterial(hand.getType().name())) {
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (!PracticeGuards.trimEditorAllowedInState(state)) {
            return;
        }
        if (!rankService.isVipOrAbove(player)) {
            player.sendMessage(Component.text(
                    "鍛冶型装飾は VIP 以上の特典です。/rank でランクを確認してください。",
                    NamedTextColor.RED));
            return;
        }
        event.setCancelled(true);
        SmithingTrimGui.annotateTrimmable(hand);
        player.getInventory().setItemInMainHand(hand);
        trimGui.openFor(player, hand, player.getInventory().getHeldItemSlot());
    }
}
