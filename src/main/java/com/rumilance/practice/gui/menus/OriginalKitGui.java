package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.originalkit.ClientModBridge;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class OriginalKitGui extends AbstractGui {

    private final OriginalKitService originalKitService;
    private final ClientModBridge clientModBridge;

    public OriginalKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            OriginalKitService originalKitService,
            ClientModBridge clientModBridge
    ) {
        super(registry, sounds, GuiType.ORIGINAL_KIT, 5, true);
        this.originalKitService = originalKitService;
        this.clientModBridge = clientModBridge;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Original Kit", NamedTextColor.BLUE).decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        OriginalKitService.Plan plan = originalKitService.planOf(player);
        for (int slot = 0; slot < 45; slot++) {
            if (!originalKitService.isSlotUnlocked(plan, slot)) {
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta meta = barrier.getItemMeta();
                meta.displayName(Component.text("Locked", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text("Requires higher plan: " + nextPlan(plan), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                barrier.setItemMeta(meta);
                inventory.setItem(slot, GuiDecorator.button(Material.BARRIER,
                        Component.text("Locked (" + nextPlan(plan) + ")", NamedTextColor.RED), "locked"));
            }
        }
        inventory.setItem(GuiSlots.slot(4, 1), GuiDecorator.button(Material.CHEST,
                Component.text("Save Current Inv", NamedTextColor.GREEN), "save"));
        inventory.setItem(GuiSlots.slot(4, 3), GuiDecorator.button(Material.ENDER_CHEST,
                Component.text("Load Saved", NamedTextColor.AQUA), "load"));
        inventory.setItem(GuiSlots.slot(4, 5), GuiDecorator.button(Material.PAPER,
                Component.text("Share Code", NamedTextColor.YELLOW), "share"));
        inventory.setItem(GuiSlots.slot(4, 7), GuiDecorator.button(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED), "close"));
        int limit = originalKitService.monthlyEditLimit(plan);
        String limitLabel = limit < 0 ? "∞" : String.valueOf(limit);
        player.sendActionBar(Component.text("Monthly edits: "
                        + originalKitService.monthlyEdits(player.getUniqueId()) + "/" + limitLabel,
                NamedTextColor.YELLOW));
    }

    private static String nextPlan(OriginalKitService.Plan plan) {
        return switch (plan) {
            case DEFAULT -> "Member";
            case MEMBER -> "VIP";
            case VIP -> "VIP+";
            case VIP_PLUS -> "VIP+";
        };
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "save" -> {
                originalKitService.saveFromInventory(player);
                sounds.play(player, "select");
            }
            case "load" -> {
                originalKitService.loadToInventory(player);
                sounds.play(player, "gui-open");
            }
            case "share" -> {
                String code = originalKitService.createShareCode(player);
                clientModBridge.notifyKitShare(player.getUniqueId(), code);
                player.sendMessage(Component.text("Share code: " + code
                                + (clientModBridge.isEnabled() ? " (mod notified)" : " (copy to friends)"),
                        NamedTextColor.GOLD));
                sounds.play(player, "select");
            }
            case "close" -> player.closeInventory();
            default -> {
            }
        }
    }
}
