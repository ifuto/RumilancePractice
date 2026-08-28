package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.match.inventory.MatchInventoryStore;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * View of a fighter's end-of-match inventory. Swap cycles through all stored participants.
 */
public final class MatchInventoryGui extends AbstractGui {

    private final MatchInventoryStore store;

    public MatchInventoryGui(GuiSessionRegistry registry, SoundService sounds, MatchInventoryStore store) {
        super(registry, sounds, GuiType.MATCH_INVENTORY, 6, true);
        this.store = store;
    }

    public void open(Player viewer, UUID matchId) {
        open(viewer, matchId, null);
    }

    public void open(Player viewer, UUID matchId, UUID focusPlayerId) {
        MatchInventoryStore.Snapshot snap = store.get(matchId).orElse(null);
        if (snap == null || snap.fighters().isEmpty()) {
            viewer.sendMessage(Component.text("That match inventory is no longer available.", NamedTextColor.RED));
            return;
        }
        GuiSession session = registry.open(viewer.getUniqueId(), type(), rows);
        session.put("match_id", matchId.toString());
        UUID focus = focusPlayerId;
        if (focus == null || snap.fighter(focus).isEmpty()) {
            focus = snap.fighters().get(0).playerId();
        }
        session.put("focus", focus.toString());
        PracticeGuiOpen.open(this, viewer, session);
        sounds.play(viewer, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        MatchInventoryStore.Fighter fighter = focusedFighter(session);
        if (fighter == null) {
            return Component.text("Match Inventory", UiTheme.PRIMARY);
        }
        return Component.text("End Inv · " + fighter.name(), UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MatchInventoryStore.Snapshot snap = snapshot(session);
        MenuScaffold.fillBackground(inventory);
        MatchInventoryStore.Fighter fighter = focusedFighter(session);
        if (snap == null || fighter == null) {
            inventory.setItem(GuiSlots.slot(2, 4), GuiDecorator.button(Material.BARRIER,
                    Component.text("Not found", NamedTextColor.RED), "close"));
            return;
        }
        ItemStack[] contents = fighter.contents();
        MatchInventoryStore.Fighter next = nextFighter(snap, fighter.playerId());
        String otherName = next == null ? "-" : next.name();

        inventory.setItem(GuiSlots.slot(0, 0), ItemBuilder.of(Material.NETHER_STAR)
                .name(Component.text(fighter.name() + " (end)", UiTheme.PRIMARY))
                .lore(UiTheme.line("Match end inventory"),
                        UiTheme.hint(snap.fighters().size() + " fighter(s) stored"),
                        UiTheme.hint("Next: " + otherName))
                .action("decorate").build());

        // Hotbar 0-8 -> row 5, main 9-35 -> rows 1-3.
        for (int i = 0; i < 9 && i < contents.length; i++) {
            inventory.setItem(GuiSlots.slot(5, i), copyOrAir(contents[i]));
        }
        for (int i = 9; i < 36 && i < contents.length; i++) {
            int local = i - 9;
            inventory.setItem(GuiSlots.slot(1 + local / 9, local % 9), copyOrAir(contents[i]));
        }
        // Armor row: star@0, boots@1, then helmet/chest/legs/offhand shifted right.
        if (contents.length > 39) {
            inventory.setItem(GuiSlots.slot(0, 1), armorOr(contents[39], Material.LEATHER_BOOTS));
        }
        if (contents.length > 36) {
            inventory.setItem(GuiSlots.slot(0, 3), armorOr(contents[36], Material.LEATHER_HELMET));
        }
        if (contents.length > 37) {
            inventory.setItem(GuiSlots.slot(0, 4), armorOr(contents[37], Material.LEATHER_CHESTPLATE));
        }
        if (contents.length > 38) {
            inventory.setItem(GuiSlots.slot(0, 5), armorOr(contents[38], Material.LEATHER_LEGGINGS));
        }
        if (contents.length > 40) {
            inventory.setItem(GuiSlots.slot(0, 6), armorOr(contents[40], Material.SHIELD));
        }

        if (snap.fighters().size() > 1) {
            inventory.setItem(GuiSlots.slot(0, 7), ItemBuilder.of(Material.ARROW)
                    .name(Component.text("\u2192 " + otherName, UiTheme.WARNING))
                    .lore(UiTheme.hint("Switch fighter"))
                    .action("swap").build());
        }
        inventory.setItem(GuiSlots.slot(0, 8), GuiDecorator.button(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED), "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("swap".equals(action)) {
            MatchInventoryStore.Snapshot snap = snapshot(session);
            MatchInventoryStore.Fighter current = focusedFighter(session);
            if (snap == null || current == null) {
                return;
            }
            MatchInventoryStore.Fighter next = nextFighter(snap, current.playerId());
            if (next != null) {
                session.put("focus", next.playerId().toString());
                refresh(player, session, inventory);
                sounds.play(player, "gui-click");
            }
        }
    }

    private MatchInventoryStore.Snapshot snapshot(GuiSession session) {
        String raw = session.get("match_id", String.class);
        if (raw == null) {
            return null;
        }
        try {
            return store.get(UUID.fromString(raw)).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private MatchInventoryStore.Fighter focusedFighter(GuiSession session) {
        MatchInventoryStore.Snapshot snap = snapshot(session);
        if (snap == null || snap.fighters().isEmpty()) {
            return null;
        }
        String focusRaw = session.get("focus", String.class);
        if (focusRaw != null) {
            try {
                return snap.fighter(UUID.fromString(focusRaw)).orElse(snap.fighters().get(0));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return snap.fighters().get(0);
    }

    private static MatchInventoryStore.Fighter nextFighter(MatchInventoryStore.Snapshot snap, UUID currentId) {
        List<MatchInventoryStore.Fighter> fighters = snap.fighters();
        if (fighters.size() <= 1) {
            return fighters.isEmpty() ? null : fighters.get(0);
        }
        int idx = 0;
        for (int i = 0; i < fighters.size(); i++) {
            if (fighters.get(i).playerId().equals(currentId)) {
                idx = i;
                break;
            }
        }
        return fighters.get((idx + 1) % fighters.size());
    }

    private static ItemStack copyOrAir(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }

    private static ItemStack armorOr(ItemStack stack, Material emptyIcon) {
        if (stack == null || stack.getType().isAir()) {
            return ItemBuilder.of(emptyIcon)
                    .name(Component.text("Empty", UiTheme.MUTED))
                    .action("decorate").build();
        }
        return stack.clone();
    }
}
