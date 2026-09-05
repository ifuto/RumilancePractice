package com.rumilance.practice.gui.menus;

import com.rumilance.practice.cosmetic.namecolor.NameColorSelection;
import com.rumilance.practice.cosmetic.namecolor.NameColorService;
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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.time.Duration;
import java.util.List;

/**
 * VIP+ name-color studio: pick a mode (off / single color / two-color gradient) and the
 * colors from a wool palette. Saving is rate-limited by {@link NameColorService#COOLDOWN}.
 */
public final class NameColorGui extends AbstractGui {

    /** Curated palette (Minecraft dye colors), two rows of eight. */
    private static final List<String[]> PALETTE = List.of(
            new String[]{"#FFFFFF", "WHITE_WOOL"},
            new String[]{"#F9801D", "ORANGE_WOOL"},
            new String[]{"#C74EBD", "MAGENTA_WOOL"},
            new String[]{"#3AB3DA", "LIGHT_BLUE_WOOL"},
            new String[]{"#FED83D", "YELLOW_WOOL"},
            new String[]{"#80C71F", "LIME_WOOL"},
            new String[]{"#F38BAA", "PINK_WOOL"},
            new String[]{"#474F52", "GRAY_WOOL"},
            new String[]{"#9D9D97", "LIGHT_GRAY_WOOL"},
            new String[]{"#169696", "CYAN_WOOL"},
            new String[]{"#8932B8", "PURPLE_WOOL"},
            new String[]{"#3C44AA", "BLUE_WOOL"},
            new String[]{"#835432", "BROWN_WOOL"},
            new String[]{"#5E7C16", "GREEN_WOOL"},
            new String[]{"#B02E26", "RED_WOOL"},
            new String[]{"#1D1D21", "BLACK_WOOL"});

    private final NameColorService nameColorService;
    private SettingsGui settingsGui;
    /** Which gradient stop the palette edits (session flag "editing_secondary"). */
    private static final String KEY_SECONDARY = "namecolor_edit_secondary";

    public NameColorGui(GuiSessionRegistry registry, SoundService sounds,
                        NameColorService nameColorService) {
        super(registry, sounds, GuiType.NAME_COLOR, 6, true);
        this.nameColorService = nameColorService;
    }

    public void setSettingsGui(SettingsGui settingsGui) {
        this.settingsGui = settingsGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.namecolor-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        if (!nameColorService.isVipPlus(player.getUniqueId())) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.namecolor-locked").color(UiTheme.DANGER))
                            .lore(UiTheme.line(line(player, "gui.namecolor-locked-lore")))
                            .action("decorate").build());
            backToSettings(inventory, player);
            return;
        }

        NameColorSelection selection = nameColorService.selection(player.getUniqueId());
        boolean editingSecondary = Boolean.TRUE.equals(session.get(KEY_SECONDARY, Boolean.class));

        // --- row 1: mode / gradient target / preview / cooldown ---
        inventory.setItem(GuiSlots.slot(1, 1),
                ItemBuilder.of(modeMaterial(selection.mode()))
                        .name(Component.text(line(player, "gui.namecolor-mode") + ": "
                                + line(player, modeKey(selection.mode())), modeColor(selection.mode()))
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.hint(line(player, "gui.namecolor-mode-hint")))
                        .action("mode:cycle").build());

        if (selection.mode() == NameColorSelection.Mode.GRADIENT) {
            inventory.setItem(GuiSlots.slot(1, 3),
                    ItemBuilder.of(editingSecondary ? Material.ORANGE_STAINED_GLASS
                                    : Material.LIGHT_BLUE_STAINED_GLASS)
                            .name(Component.text(line(player, "gui.namecolor-target") + ": "
                                    + line(player, editingSecondary
                                            ? "gui.namecolor-target-2" : "gui.namecolor-target-1"),
                                    UiTheme.HEADER)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(UiTheme.hint(line(player, "gui.namecolor-target-hint")))
                            .action("target:toggle").build());
        }

        inventory.setItem(GuiSlots.slot(1, 5),
                ItemBuilder.of(Material.NAME_TAG)
                        .name(Component.text(line(player, "gui.namecolor-preview"), UiTheme.MUTED)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(), previewLine(player, selection))
                        .action("decorate").build());

        Duration remaining = nameColorService.remainingCooldown(player.getUniqueId()).orElse(null);
        inventory.setItem(GuiSlots.slot(1, 7),
                ItemBuilder.of(remaining == null ? UiTheme.TOGGLE_ON : Material.CLOCK)
                        .name(Component.text(remaining == null
                                        ? line(player, "gui.namecolor-ready")
                                        : line(player, "gui.namecolor-waiting"),
                                remaining == null ? UiTheme.SUCCESS : UiTheme.WARNING)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.line(line(player, "gui.namecolor-cooldown-lore")
                                .replace("<days>", "3")))
                        .action("decorate").build());

        // --- rows 2-3: the palette (8 per row) ---
        boolean picksAllowed = remaining == null;
        for (int i = 0; i < PALETTE.size(); i++) {
            String hex = PALETTE.get(i)[0];
            Material wool = Material.matchMaterial(PALETTE.get(i)[1]);
            boolean selected = hex.equalsIgnoreCase(selection.primaryHex()) && !editingSecondary
                    || hex.equalsIgnoreCase(selection.secondaryHex()) && editingSecondary;
            TextColor swatch = TextColor.fromHexString(hex);
            inventory.setItem(GuiSlots.slot(2 + i / 8, 1 + i % 8),
                    ItemBuilder.of(wool == null ? Material.WHITE_WOOL : wool)
                            .name(Component.text(hex, swatch == null ? UiTheme.VALUE : swatch)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(UiTheme.hint(picksAllowed
                                    ? line(player, "gui.namecolor-pick-hint")
                                    : line(player, "gui.namecolor-waiting")))
                            .glintIf(selected)
                            .action("pick:" + hex).build());
        }

        backToSettings(inventory, player);
    }

    private void backToSettings(Inventory inventory, Player player) {
        inventory.setItem(GuiSlots.slot(5, 8),
                ItemBuilder.of(UiTheme.BACK)
                        .name(t(player, "menu.back").color(UiTheme.WARNING))
                        .action("back_settings").build());
    }

    private Component previewLine(Player player, NameColorSelection selection) {
        if (!selection.active()) {
            return UiTheme.line(line(player, "gui.namecolor-preview-none"));
        }
        if (selection.mode() == NameColorSelection.Mode.SOLID) {
            TextColor color = TextColor.fromHexString("#" + selection.primaryHex());
            return Component.text(player.getName(), color == null ? UiTheme.VALUE : color);
        }
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<gradient:#" + selection.primaryHex() + ":#" + selection.secondaryHex() + ">"
                        + player.getName() + "</gradient>");
    }

    private static Material modeMaterial(NameColorSelection.Mode mode) {
        return switch (mode) {
            case NONE -> Material.GUNPOWDER;
            case SOLID -> Material.LAPIS_LAZULI;
            case GRADIENT -> Material.BLAZE_POWDER;
        };
    }

    private static String modeKey(NameColorSelection.Mode mode) {
        return switch (mode) {
            case NONE -> "gui.namecolor-mode-off";
            case SOLID -> "gui.namecolor-mode-solid";
            case GRADIENT -> "gui.namecolor-mode-gradient";
        };
    }

    private static net.kyori.adventure.text.format.TextColor modeColor(NameColorSelection.Mode mode) {
        return switch (mode) {
            case NONE -> UiTheme.MUTED;
            case SOLID -> UiTheme.VALUE;
            case GRADIENT -> UiTheme.HEADER;
        };
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action) {
        if ("back_settings".equals(action) || "close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if (settingsGui != null) {
                settingsGui.open(player);
            }
            return;
        }
        if (!nameColorService.isVipPlus(player.getUniqueId())) {
            return;
        }

        if ("target:toggle".equals(action)) {
            boolean secondary = Boolean.TRUE.equals(session.get(KEY_SECONDARY, Boolean.class));
            session.put(KEY_SECONDARY, !secondary);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }

        NameColorSelection current = nameColorService.selection(player.getUniqueId());

        if ("mode:cycle".equals(action)) {
            NameColorSelection.Mode next = switch (current.mode()) {
                case NONE -> NameColorSelection.Mode.SOLID;
                case SOLID -> NameColorSelection.Mode.GRADIENT;
                case GRADIENT -> NameColorSelection.Mode.NONE;
            };
            applyChange(player, session, inventory, current.withMode(next));
            return;
        }

        if (action.startsWith("pick:")) {
            String hex = NameColorSelection.normalizeHex(action.substring("pick:".length()));
            if (hex.isEmpty()) {
                return;
            }
            boolean secondary = Boolean.TRUE.equals(session.get(KEY_SECONDARY, Boolean.class));
            NameColorSelection next = secondary ? current.withSecondary(hex) : current.withPrimary(hex);
            // Auto-switch to the solid color when the player is in single-color mode.
            if (next.mode() == NameColorSelection.Mode.NONE) {
                next = next.withMode(NameColorSelection.Mode.SOLID);
            }
            applyChange(player, session, inventory, next);
        }
    }

    /** Enforces the 3-day cooldown, persists, re-applies the name and re-renders. */
    private void applyChange(Player player, GuiSession session, Inventory inventory,
                             NameColorSelection next) {
        NameColorSelection current = nameColorService.selection(player.getUniqueId());
        if (next.equals(current)) {
            sounds.play(player, "gui-click");
            return;
        }
        if (!nameColorService.canChange(player.getUniqueId())) {
            sounds.play(player, "error");
            Duration remaining = nameColorService.remainingCooldown(player.getUniqueId())
                    .orElse(Duration.ZERO);
            long days = remaining.toDays();
            long hours = remaining.toHoursPart();
            player.sendMessage(Component.text(
                    line(player, "gui.namecolor-cooldown-msg")
                            .replace("<days>", String.valueOf(days))
                            .replace("<hours>", String.valueOf(hours)),
                    UiTheme.WARNING).decoration(TextDecoration.ITALIC, false));
            return;
        }
        if (!nameColorService.save(player.getUniqueId(), next)) {
            sounds.play(player, "error");
            return;
        }
        nameColorService.applyToPlayer(player);
        sounds.play(player, "select");
        refresh(player, session, inventory);
    }
}
