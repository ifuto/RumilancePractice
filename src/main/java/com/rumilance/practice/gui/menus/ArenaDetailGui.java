package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.chat.PendingInput;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.function.BiConsumer;

/**
 * Arena detail page — opened by left-clicking an arena in {@link ArenaAdminGui}.
 * Replaces the old multi-click/shift-click model with a unified detail view.
 */
public final class ArenaDetailGui extends AbstractGui {

    private final ArenaTemplateStore arenaStore;
    private final ArenaService arenaService;
    private BiConsumer<Player, String> partyIconPrompt = (p, n) -> { };
    private final String arenaName;

    public ArenaDetailGui(GuiSessionRegistry registry, SoundService sounds,
                          ArenaTemplateStore arenaStore, ArenaService arenaService,
                          String arenaName) {
        super(registry, sounds, GuiType.ARENA_ADMIN, 6, false);
        this.arenaStore = arenaStore;
        this.arenaService = arenaService;
        this.arenaName = arenaName;
    }

    public void setPartyIconPrompt(BiConsumer<Player, String> partyIconPrompt) {
        this.partyIconPrompt = partyIconPrompt == null ? (p, n) -> { } : partyIconPrompt;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.GRASS_BLOCK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text(com.rumilance.practice.util.NameDisplay.pretty(arenaName), UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        ArenaTemplate t = arenaStore.findExact(arenaName).orElse(null);
        if (t == null) {
            inventory.setItem(GuiSlots.slot(2, 4), ItemBuilder.of(Material.BARRIER)
                    .name(Component.text("Arena not found", NamedTextColor.RED))
                    .action("decorate")
                    .build());
            return;
        }

        Material icon = Material.matchMaterial(t.iconMaterial() == null ? "" : t.iconMaterial());
        if (icon == null || icon.isAir()) {
            icon = t.enabled() ? Material.GRASS_BLOCK : Material.BARRIER;
        }
        inventory.setItem(GuiSlots.slot(0, 4), ItemBuilder.of(icon)
                .name(Component.text(com.rumilance.practice.util.NameDisplay.pretty(t.name()), UiTheme.VALUE)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.arena-id"), t.name()),
                        UiTheme.labelValue(line(player, "gui.arena-type"), t.type().name())
                )
                .action("decorate")
                .build());

        // Toggle: Enabled
        inventory.setItem(GuiSlots.slot(2, 2), toggleItem(player,
                t.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                line(player, t.enabled() ? "gui.arena-enabled" : "gui.arena-disabled"),
                t.enabled(),
                line(player, "gui.arena-left-toggle"),
                "toggle:enabled"));

        // Toggle: Type cycle
        inventory.setItem(GuiSlots.slot(2, 3), ItemBuilder.of(Material.MAP)
                .name(Component.text("Type: " + t.type().name(), UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.arena-right-type")),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("cycle:type")
                .build());

        // Toggle: Party map
        inventory.setItem(GuiSlots.slot(2, 5), toggleItem(player,
                t.party() ? Material.TNT : Material.GRASS_BLOCK,
                line(player, t.party() ? "gui.arena-party-map" : "gui.arena-normal"),
                t.party(),
                line(player, "gui.arena-shift-party"),
                "toggle:party"));

        // Rename
        inventory.setItem(GuiSlots.slot(2, 6), ItemBuilder.of(Material.NAME_TAG)
                .name(Component.text("Rename", NamedTextColor.YELLOW))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.arena-shift-rename")),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("rename")
                .build());

        // Delete
        inventory.setItem(GuiSlots.slot(4, 6), ItemBuilder.of(Material.BARRIER)
                .name(Component.text("Delete", NamedTextColor.RED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.arena-q-delete")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("delete")
                .build());

        // Back
        inventory.setItem(GuiSlots.slot(5, 0), ItemBuilder.of(Material.ARROW)
                .name(t(player, "menu.back").color(UiTheme.MUTED))
                .action("back")
                .build());

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private org.bukkit.inventory.ItemStack toggleItem(Player player, Material mat, String name,
                                                       boolean state, String lore, String action) {
        String word = state ? line(player, "gui.toggle-on") : line(player, "gui.toggle-off");
        return ItemBuilder.of(mat)
                .name(Component.text(name, UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(lore),
                        UiTheme.status(word, state ? UiTheme.SUCCESS : UiTheme.DANGER),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.toggle-hint"))
                )
                .glint(state)
                .action(action)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action) {
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        ArenaTemplate t = arenaStore.findExact(arenaName).orElse(null);
        if (t == null) {
            sounds.play(player, "error");
            return;
        }
        if ("toggle:enabled".equals(action)) {
            arenaStore.setEnabled(arenaName, !t.enabled());
            arenaService.setTemplates(arenaStore.templates());
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("cycle:type".equals(action)) {
            ArenaType[] values = ArenaType.values();
            ArenaType next = values[(t.type().ordinal() + 1) % values.length];
            arenaStore.setType(arenaName, next);
            arenaService.setTemplates(arenaStore.templates());
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("toggle:party".equals(action)) {
            boolean next = !t.party();
            arenaStore.setParty(arenaName, next);
            arenaService.setTemplates(arenaStore.templates());
            if (next) {
                player.sendMessage(t(player, "gui.arena-party-on"));
                partyIconPrompt.accept(player, arenaName);
            }
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("rename".equals(action)) {
            player.closeInventory();
            player.sendMessage(t(player, "gui.arena-rename-prompt"));
            PendingInput.await(player, text -> {
                if (text.equalsIgnoreCase("cancel") || text.isBlank()) {
                    player.sendMessage(t(player, "gui.arena-rename-cancel"));
                } else {
                    ArenaTemplateStore.RenameResult r = arenaStore.rename(arenaName, text);
                    arenaService.setTemplates(arenaStore.templates());
                    player.sendMessage(t(player, r == ArenaTemplateStore.RenameResult.OK
                            ? "gui.arena-renamed" : "gui.arena-rename-fail",
                            MessageService.tags(
                                    r == ArenaTemplateStore.RenameResult.OK ? "name" : "code",
                                    r == ArenaTemplateStore.RenameResult.OK ? text : r.name()))
                            .color(r == ArenaTemplateStore.RenameResult.OK ? UiTheme.SUCCESS : UiTheme.DANGER));
                }
                open(player);
            });
            return;
        }
        if ("delete".equals(action)) {
            arenaStore.delete(arenaName);
            arenaService.setTemplates(arenaStore.templates());
            sounds.play(player, "delete");
            player.closeInventory();
        }
    }
}