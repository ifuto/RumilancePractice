package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Admin sub-GUI: block placement / breaking rules for a single kit.
 * Keeps the block-related toggles and the breakable-block exception list
 * off the main kit config screen so each panel stays easy to read.
 */
public final class KitBlockRulesGui extends AbstractGui {

    private final KitService kitService;
    private BiConsumer<Player, String> returnTo = (p, kit) -> { };

    public KitBlockRulesGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_BLOCK_RULES, 6, false);
        this.kitService = kitService;
    }

    public void setReturnTo(BiConsumer<Player, String> returnTo) {
        this.returnTo = returnTo == null ? (p, kit) -> { } : returnTo;
    }

    public void open(Player player, String kitId) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit() == null ? "" : session.selectedKit();
        return t(player, "gui.kit-admin-block-rules-title",
                com.rumilance.practice.locale.MessageService.tags("kit", kit)).color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        KitDefinition kit = kitOf(session);
        if (kit == null) {
            inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                    t(player, "menu.back"), "back"));
            return;
        }
        inventory.setItem(GuiSlots.slot(0, 4), header(player, kit));
        inventory.setItem(GuiSlots.slot(2, 2), toggle(player, "admin-gui.block-place", kit.blockPlace(),
                "toggle:blockplace", Material.BRICKS));
        inventory.setItem(GuiSlots.slot(2, 4), toggle(player, "admin-gui.block-break", kit.blockBreak(),
                "toggle:blockbreak", Material.IRON_PICKAXE));
        inventory.setItem(GuiSlots.slot(2, 6), toggle(player, "admin-gui.break-player-placed",
                kit.breakPlayerPlacedOnly(), "toggle:breakplayerplaced", Material.OAK_PLANKS));
        inventory.setItem(GuiSlots.slot(3, 4), canBreakItem(player, kit));
        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                t(player, "menu.back"), "back"));
    }

    private ItemStack header(Player player, KitDefinition kit) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(kit.prettyDisplayName(), UiTheme.SECONDARY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(t(player, "admin-gui.click-hint").color(UiTheme.MUTED)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack toggle(Player player, String labelKey, boolean state, String action, Material material) {
        return GuiDecorator.button(material,
                Component.text(line(player, labelKey) + ": ", UiTheme.MUTED)
                        .append(Component.text(line(player, state ? "admin-gui.on" : "admin-gui.off"),
                                state ? UiTheme.SUCCESS : UiTheme.DANGER))
                        .decoration(TextDecoration.ITALIC, false),
                action);
    }

    /** Breakable-block exception list, edited with the block held in hand. */
    private ItemStack canBreakItem(Player player, KitDefinition kit) {
        List<Component> lore = new ArrayList<>();
        if (kit.canBreak().isEmpty()) {
            lore.add(t(player, "gui.kit-admin-canbreak-empty").color(UiTheme.MUTED));
        } else {
            int shown = 0;
            for (String material : kit.canBreak()) {
                if (shown++ >= 8) {
                    lore.add(Component.text("+ " + (kit.canBreak().size() - shown + 1) + " ...", UiTheme.MUTED)
                            .decoration(TextDecoration.ITALIC, false));
                    break;
                }
                lore.add(Component.text("- " + material, UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.text("", UiTheme.MUTED));
        lore.add(t(player, "gui.kit-admin-canbreak-hint-1").color(UiTheme.MUTED));
        lore.add(t(player, "gui.kit-admin-canbreak-hint-2").color(UiTheme.MUTED));
        lore.add(t(player, "gui.kit-admin-canbreak-hint-3").color(UiTheme.MUTED));
        return ItemBuilder.of(Material.STONE_PICKAXE)
                .name(Component.text(line(player, "gui.kit-admin-canbreak")
                                + (kit.canBreak().isEmpty() ? "" : " (" + kit.canBreak().size() + ")"),
                        UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(lore.toArray(new Component[0]))
                .action("canbreak:edit")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-back");
            returnTo.accept(player, session.selectedKit());
            return;
        }
        if ("noop".equals(action)) {
            return;
        }
        KitDefinition kit = kitOf(session);
        if (kit == null) {
            return;
        }
        KitDefinition updated = KitAdminGui.applyConfigChange(kit, action);
        if (updated != null) {
            kitService.save(updated);
            sounds.play(player, updated.equals(kit) ? "gui-click" : "select");
            refresh(player, session, inventory);
        }
    }

    /** ClickType-aware overload: edits the breakable-block list with the held block. */
    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        if ("canbreak:edit".equals(action) && session.selectedKit() != null) {
            KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
            if (kit == null) {
                return;
            }
            Material held = player.getInventory().getItemInMainHand().getType();
            boolean shift = click.isShiftClick();
            List<String> list = new ArrayList<>(kit.canBreak());
            if (shift) {
                list.clear();
            } else if (held.isAir() || !held.isBlock()) {
                sounds.play(player, "error");
                player.sendMessage(t(player, "gui.kit-admin-canbreak-hold").color(NamedTextColor.RED));
                return;
            } else if (click == ClickType.RIGHT) {
                list.removeIf(m -> m.equalsIgnoreCase(held.name()));
            } else {
                boolean present = list.stream().anyMatch(m -> m.equalsIgnoreCase(held.name()));
                if (!present) {
                    list.add(held.name());
                }
            }
            kitService.save(kit.toBuilder().canBreak(list).build());
            sounds.play(player, "select");
            refresh(player, session, inventory);
            return;
        }
        handleClick(player, session, inventory, slot, action);
    }

    private KitDefinition kitOf(GuiSession session) {
        return session.selectedKit() == null ? null : kitService.get(session.selectedKit()).orElse(null);
    }
}
