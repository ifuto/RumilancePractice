package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;

/** Generic yes/no confirmation dialog ("はい" / "いいえ"). */
public final class ConfirmGui extends AbstractGui {

    private com.rumilance.practice.originalkit.OriginalKitService originalKitService;

    public void setOriginalKitService(com.rumilance.practice.originalkit.OriginalKitService service) {
        this.originalKitService = service;
    }

    private com.rumilance.practice.originalkit.OriginalKitService originalKitService() {
        return originalKitService;
    }

    public ConfirmGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.CONFIRM, 3, false);
    }

    public void open(Player player, Component message, List<Component> lore,
                     Consumer<Player> onYes, Consumer<Player> onNo) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("title", message);
        session.put("lore", lore);
        session.put("yes", onYes);
        session.put("no", onNo);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        Component stored = session.get("title", Component.class);
        return stored != null ? stored : t(player, "gui.confirm-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        Component message = session.get("title", Component.class);
        List<Component> lore = session.get("lore", List.class);
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(message == null
                ? t(player, "gui.confirm-title").color(UiTheme.PRIMARY)
                : message);
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        info.setItemMeta(meta);
        inventory.setItem(GuiSlots.slot(1, 4), info);
        inventory.setItem(GuiSlots.slot(1, 2), ItemBuilder.action(UiTheme.CONFIRM,
                t(player, "gui.confirm-yes").color(UiTheme.SUCCESS), "yes"));
        inventory.setItem(GuiSlots.slot(1, 6), ItemBuilder.action(UiTheme.CLOSE,
                t(player, "gui.confirm-no").color(UiTheme.DANGER), "no"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "yes" -> {
                Consumer<Player> yes = session.get("yes", Consumer.class);
                com.rumilance.practice.originalkit.OriginalKitService svc = originalKitService();
                // Only mark navigating if the inventory is already stashed (e.g. the
                // delete-all confirm inside the editor). For the paper->confirm flow the
                // stash happens AFTER this dialog closes, so no flag is needed - and leaving
                // a stale flag would break ESC-restore on the next flow GUI.
                if (svc != null && svc.isStashed(player.getUniqueId())) {
                    svc.markNavigating(player.getUniqueId());
                }
                player.closeInventory();
                if (yes != null) {
                    yes.accept(player);
                }
            }
            case "no" -> {
                Consumer<Player> no = session.get("no", Consumer.class);
                com.rumilance.practice.originalkit.OriginalKitService svc = originalKitService();
                if (svc != null && svc.isStashed(player.getUniqueId())) {
                    svc.markNavigating(player.getUniqueId());
                }
                player.closeInventory();
                if (no != null) {
                    no.accept(player);
                }
            }
            default -> {
            }
        }
    }
}
