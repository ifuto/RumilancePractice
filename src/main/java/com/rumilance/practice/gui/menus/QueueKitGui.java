package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranked / Unranked kit queue selector GUI(6).
 */
public final class QueueKitGui extends AbstractGui {

    private final KitService kitService;
    private final QueueService queueService;
    private final QueueCoordinator queueCoordinator;
    private final boolean ranked;

    public QueueKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            QueueService queueService,
            QueueCoordinator queueCoordinator,
            boolean ranked
    ) {
        super(registry, sounds, ranked ? GuiType.RANKED_QUEUE : GuiType.UNRANKED_QUEUE, 6, ranked);
        this.kitService = kitService;
        this.queueService = queueService;
        this.queueCoordinator = queueCoordinator;
        this.ranked = ranked;
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setRanked(ranked);
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text(ranked ? "Ranked Queue" : "Unranked Queue")
                .color(ranked ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        List<KitDefinition> kits = kitService.enabled();
        int index = 0;
        for (int row = 1; row <= 4 && index < kits.size(); row++) {
            for (int col = 1; col <= 7 && index < kits.size(); col++) {
                KitDefinition kit = kits.get(index++);
                int slot = GuiSlots.slot(row, col);
                boolean queueOn = kitService.isQueueEnabled(kit.name());
                ItemStack icon = new ItemStack(queueOn
                        ? materialOr(kit.icon(), Material.DIAMOND_SWORD)
                        : Material.BARRIER);
                ItemMeta meta = icon.getItemMeta();
                meta.displayName(MiniMessage.miniMessage().deserialize(kit.displayName())
                        .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Waiting: " + queueService.waitingCount(
                        ranked ? MatchMode.RANKED : MatchMode.UNRANKED, kit.name()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                if (!queueOn) {
                    lore.add(Component.text("Disabled", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "kit:" + kit.name());
                meta.getPersistentDataContainer().set(ItemKeys.kitName(), PersistentDataType.STRING, kit.name());
                icon.setItemMeta(meta);
                inventory.setItem(slot, icon);
            }
        }
        inventory.setItem(GuiSlots.slot(5, 4), GuiDecorator.button(
                Material.BARRIER,
                Component.text("Close", NamedTextColor.RED),
                "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action.startsWith("kit:")) {
            String kitId = action.substring(4);
            if (!kitService.isQueueEnabled(kitId)) {
                sounds.play(player, "error");
                return;
            }
            sounds.play(player, "kit-select");
            player.closeInventory();
            queueCoordinator.join(player, kitId, ranked ? MatchMode.RANKED : MatchMode.UNRANKED);
        }
    }

    private static Material materialOr(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }
}
