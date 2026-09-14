package com.rumilance.practice.gui.menus;

import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.CrystalFfaStore;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * The {@code /k} quick picker for the crystal FFA: nine KIT buttons, one click equips.
 * Shown instead of any auto-given kit — crystal FFA hands out nothing on entry, so this
 * menu (or /k1../k9) is how a fighter gears up, and the clicked slot becomes their
 * selection for respawns and /regear. Kept deliberately tiny: row 1 = the nine KIT
 * buttons (bold aqua signs, glint = currently selected), row 2 = glass, row 3 = Close.
 */
public final class CrystalKitQuickGui extends AbstractGui {

    private final KitService kitService;
    private final KitLayoutRepository layoutRepository;
    private final KitLayoutCache layoutCache;
    private final CrystalFfaStore store;
    private final com.rumilance.practice.ffa.FfaService ffaService;

    public CrystalKitQuickGui(GuiSessionRegistry registry,
                              com.rumilance.practice.sound.SoundService sounds,
                              KitService kitService, KitLayoutRepository layoutRepository,
                              KitLayoutCache layoutCache, CrystalFfaStore store,
                              com.rumilance.practice.ffa.FfaService ffaService) {
        super(registry, sounds, GuiType.CRYSTAL_KIT_QUICK, 3, false);
        this.kitService = kitService;
        this.layoutRepository = layoutRepository;
        this.layoutCache = layoutCache;
        this.store = store;
        this.ffaService = ffaService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.END_CRYSTAL;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        var kit = ffaService.crystalFfaKitOf(player);
        return Component.text("Crystal FFA Kit"
                + (kit == null ? "" : ": " + com.rumilance.practice.util.KitNames.pretty(kit.name())),
                UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        UUID id = player.getUniqueId();
        int selected = store.selectedVariant(id);
        String kitId = crystalKitId(player);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler());
        }
        for (int n = 1; n <= CrystalFfaStore.SLOTS; n++) {
            boolean saved = hasSavedLayout(id, kitId, n);
            inventory.setItem(GuiSlots.slot(0, n - 1),
                    ItemBuilder.of(Material.OAK_SIGN)
                            .name(Component.text("KIT" + n, NamedTextColor.AQUA, TextDecoration.BOLD)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    UiTheme.divider(),
                                    saved ? UiTheme.status("Saved", UiTheme.SUCCESS)
                                          : UiTheme.status("Empty", UiTheme.MUTED),
                                    n == selected
                                            ? UiTheme.status("Selected for FFA", UiTheme.SUCCESS)
                                            : UiTheme.line("Click to equip"),
                                    UiTheme.blank(),
                                    UiTheme.hint("Equip: items replace your inventory")
                            )
                            .glint(n == selected)
                            .action("kit:" + n)
                            .build());
        }
        inventory.setItem(GuiSlots.slot(2, 4),
                ItemBuilder.action(UiTheme.CLOSE, Component.text("Close", UiTheme.VALUE), "close"));
    }

    private org.bukkit.inventory.ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .action("decorate")
                .build();
    }

    /** The declared crystal FFA kit's id, or null outside the crystal FFA. */
    private String crystalKitId(Player player) {
        return ffaService.crystalFfaKitOf(player) == null
                ? null : ffaService.crystalFfaKitOf(player).name();
    }

    /** True when the player saved a layout for this KIT slot (cache first, then DB). */
    private boolean hasSavedLayout(UUID id, String kitId, int variant) {
        if (kitId == null) {
            return false;
        }
        String key = CrystalFfaStore.variantKey(kitId, variant);
        if (layoutCache.get(id, key).isPresent()) {
            return true;
        }
        try {
            return layoutRepository.find(id, key).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action) {
        if (action == null || "decorate".equals(action)) {
            return;
        }
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action.startsWith("kit:")) {
            int variant;
            try {
                variant = Integer.parseInt(action.substring("kit:".length()));
            } catch (NumberFormatException e) {
                return;
            }
            if (ffaService.inCombat(player.getUniqueId())) {
                sounds.play(player, "error");
                player.sendMessage(Component.text("You can't use commands while in combat.",
                        NamedTextColor.RED));
                return;
            }
            if (ffaService.applyCrystalVariant(player, variant)) {
                sounds.play(player, "select");
                player.sendMessage(Component.text("Equipped KIT" + variant, NamedTextColor.AQUA));
                refresh(player, session, inventory);
            } else {
                sounds.play(player, "error");
            }
        }
    }
}
