package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
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
import java.util.function.BiConsumer;

/**
 * Admin sub-GUI: item and combat behaviour rules for a single kit
 * (regen, food, pearls, totems, shield breaking, timeout).
 */
public final class KitItemRulesGui extends AbstractGui {

    private final KitService kitService;
    private BiConsumer<Player, String> returnTo = (p, kit) -> { };

    public KitItemRulesGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_ITEM_RULES, 6, false);
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
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.PAPER;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit() == null ? "" : session.selectedKit();
        return t(player, "gui.kit-admin-item-rules-title",
                com.rumilance.practice.locale.MessageService.tags("kit", kit)).color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        KitDefinition kit = kitOf(session);
        if (kit == null) {
            inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                    t(player, "menu.back"), "back"));
            return;
        }
        inventory.setItem(GuiSlots.slot(0, 4), header(player, kit));
        inventory.setItem(GuiSlots.slot(2, 2), toggle(player, "admin-gui.health-regen", kit.naturalHealthRegen(),
                "toggle:autoregen", Material.GOLDEN_APPLE));
        inventory.setItem(GuiSlots.slot(2, 4), toggle(player, "admin-gui.auto-food", kit.autoFood(),
                "toggle:autofood", Material.COOKED_BEEF));
        inventory.setItem(GuiSlots.slot(2, 6), toggle(player, "admin-gui.ender-pearl", kit.pearl(),
                "toggle:pearl", Material.ENDER_PEARL));
        inventory.setItem(GuiSlots.slot(3, 2), toggle(player, "admin-gui.totem", kit.totem(),
                "toggle:totem", Material.TOTEM_OF_UNDYING));
        inventory.setItem(GuiSlots.slot(3, 4), toggle(player, "admin-gui.shield-break", kit.swordShieldBreak(),
                "toggle:swordshieldbreak", Material.SHIELD));
        inventory.setItem(GuiSlots.slot(3, 6), GuiDecorator.button(Material.CLOCK,
                Component.text(line(player, "admin-gui.timeout") + ": " + kit.timeoutSeconds() + "s",
                        UiTheme.WARNING)
                        .decoration(TextDecoration.ITALIC, false), "noop"));
        inventory.setItem(GuiSlots.slot(4, 2), bedExplosionToggle(player, kit));
        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                t(player, "menu.back"), "back"));
    }

    /**
     * "Bed Explosion" kit rule: beds placed in the fight detonate on right click (Nether / End
     * behaviour, power 5) instead of setting a spawn point. Lore explains the self-damage trade.
     */
    private ItemStack bedExplosionToggle(Player player, KitDefinition kit) {
        boolean on = kit.bedExplosion();
        return ItemBuilder.of(Material.RED_BED)
                .name(Component.text(line(player, "admin-gui.bed-explosion") + ": ", UiTheme.MUTED)
                        .append(Component.text(line(player, on ? "admin-gui.on" : "admin-gui.off"),
                                on ? UiTheme.SUCCESS : UiTheme.DANGER))
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "admin-gui.bed-explosion-lore")),
                        UiTheme.line(line(player, "admin-gui.bed-explosion-lore2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "admin-gui.click-hint")))
                .glintIf(on)
                .action("toggle:bedexplosion")
                .build();
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

    private KitDefinition kitOf(GuiSession session) {
        return session.selectedKit() == null ? null : kitService.get(session.selectedKit()).orElse(null);
    }
}
