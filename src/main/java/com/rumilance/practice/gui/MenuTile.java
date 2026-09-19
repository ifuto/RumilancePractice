package com.rumilance.practice.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The one tile anatomy every player-facing menu uses — the "GUI refresh" standard:
 *
 * <pre>
 *   Name (coloured, non-italic)
 *   ───────────
 *   one-line description (muted)
 *
 *   ● live status line (optional: waiting / online / free rooms / in-use state)
 *   ▸ click hint (or the lock reason when disabled)
 * </pre>
 *
 * <p>Locked tiles mute the name, keep the description, and swap the hint for the exact
 * reason — so a player always sees WHAT it is, WHY it is unavailable and WHAT to do, on
 * every screen, in the same shape.</p>
 */
public final class MenuTile {

    private final Player player;
    private final AbstractGui gui;
    private final Material material;
    private final String nameKey;
    private final TextColor color;
    private final String descKey;
    private final String action;
    private boolean glint;
    private final List<Component> extraLore = new ArrayList<>();

    private MenuTile(Player player, AbstractGui gui, Material material, String nameKey,
                     TextColor color, String descKey, String action) {
        this.player = player;
        this.gui = gui;
        this.material = material;
        this.nameKey = nameKey;
        this.color = color;
        this.descKey = descKey;
        this.action = action;
    }

    /** Standard enabled tile. */
    public static MenuTile of(Player player, AbstractGui gui, Material material, String nameKey,
                              TextColor color, String descKey, String action) {
        return new MenuTile(player, gui, material, nameKey, color, descKey, action);
    }

    public MenuTile glint(boolean glint) {
        this.glint = glint;
        return this;
    }

    /** Optional live-status line(s) between the description and the hint. */
    public MenuTile live(Component... lines) {
        for (Component line : lines) {
            this.extraLore.add(line);
        }
        return this;
    }

    /**
     * Builds the tile. When {@code locked} the name mutes, the glint drops and the final
     * hint becomes {@code lockReasonKey} (rendered as a warning status line).
     */
    public ItemStack build(boolean locked, String lockReasonKey) {
        ItemBuilder builder = ItemBuilder.of(material)
                .name(gui.t(player, nameKey).color(locked ? UiTheme.MUTED : color))
                .glint(glint && !locked);
        List<Component> lore = new ArrayList<>();
        lore.add(UiTheme.divider());
        if (descKey != null) {
            lore.add(UiTheme.line(gui.line(player, descKey)));
        }
        if (!extraLore.isEmpty()) {
            lore.add(UiTheme.blank());
            lore.addAll(extraLore);
        }
        lore.add(UiTheme.blank());
        if (locked) {
            lore.add(UiTheme.status(gui.line(player, lockReasonKey), UiTheme.WARNING));
        } else {
            lore.add(UiTheme.hint(gui.line(player, "menu.click")));
        }
        builder.lore(lore.toArray(new Component[0]));
        builder.action(locked ? "locked:" + action : action);
        return builder.build();
    }
}
