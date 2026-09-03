package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.hiddenrank.HiddenRankService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * OP screen for the hidden {@code custom_shield} rank: lists every holder and assigns the
 * shield Custom Model Data. Each holder is a shield item — left/right click nudges the model
 * data, shift-click jumps by 100, Q removes the hidden rank entirely.
 */
public final class CustomShieldAdminGui extends AbstractGui {

    private final HiddenRankService hiddenRanks;

    public CustomShieldAdminGui(GuiSessionRegistry registry, SoundService sounds,
                                HiddenRankService hiddenRanks) {
        super(registry, sounds, GuiType.CUSTOM_SHIELD_ADMIN, 6, true);
        this.hiddenRanks = hiddenRanks;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Custom Shield — Hidden Rank", NamedTextColor.LIGHT_PURPLE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<UUID> holders = new ArrayList<>(hiddenRanks.customShieldHolders());
        holders.sort(Comparator.comparing(hiddenRanks::lastName, String.CASE_INSENSITIVE_ORDER));

        int row = 1;
        int col = 1;
        for (UUID holder : holders) {
            if (row > 4) {
                break; // simple: first 36 holders visible
            }
            inventory.setItem(GuiSlots.slot(row, col), holderItem(holder));
            col++;
            if (col > 8) {
                col = 1;
                row++;
            }
        }
        if (holders.isEmpty()) {
            inventory.setItem(GuiSlots.slot(2, 4), ItemBuilder.of(Material.BARRIER)
                    .name(Component.text("No holders", NamedTextColor.GRAY))
                    .lore(Component.text("/urank custom_shield <player>", NamedTextColor.DARK_GRAY))
                    .build());
        }

        MenuScaffold.closeButton(inventory, Component.text("Close"));
    }

    private ItemStack holderItem(UUID holder) {
        int cmd = hiddenRanks.shieldModelData(holder);
        ItemStack shield = ItemBuilder.of(Material.SHIELD)
                .name(Component.text(hiddenRanks.lastName(holder), NamedTextColor.AQUA))
                .lore(UiTheme.divider(),
                        UiTheme.line(Component.text("Custom Model Data: ", NamedTextColor.GRAY)
                                .append(Component.text(cmd == 0 ? "not set" : String.valueOf(cmd),
                                        cmd == 0 ? NamedTextColor.RED : NamedTextColor.GOLD))),
                        UiTheme.blank(),
                        UiTheme.hint("Click: +1 | Right: -1"),
                        UiTheme.hint("Shift: ±100 | Q: remove rank"))
                .action("holder:" + holder)
                .build();
        if (cmd > 0) {
            ItemMeta meta = shield.getItemMeta();
            meta.setCustomModelData(cmd);
            shield.setItemMeta(meta);
        }
        return shield;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType clickType) {
        if (!action.startsWith("holder:")) {
            if ("close".equals(action)) {
                player.closeInventory();
            }
            return;
        }
        UUID holder = UUID.fromString(action.substring(7));
        if (clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP) {
            hiddenRanks.setCustomShield(holder, null, false);
            sounds.play(player, "error");
        } else {
            int delta = switch (clickType) {
                case LEFT -> 1;
                case RIGHT -> -1;
                case SHIFT_LEFT -> 100;
                case SHIFT_RIGHT -> -100;
                default -> 0;
            };
            if (delta != 0) {
                int next = Math.max(0, hiddenRanks.shieldModelData(holder) + delta);
                hiddenRanks.setShieldModelData(holder, next);
                sounds.play(player, "gui-click");
            }
        }
        refresh(player, session, inventory);
    }
}
