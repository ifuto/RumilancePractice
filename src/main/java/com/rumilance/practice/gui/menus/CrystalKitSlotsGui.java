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
 * Crystal FFA kit variant picker (the 4-row GUI). Shown instead of the plain editor when the
 * kit being edited is THE declared crystal FFA kit:
 * <ol>
 *   <li>row 1: gray stained glass panes (decoration),</li>
 *   <li>row 2: nine signs — bold aqua KIT1..KIT9, each keeping its own saved layout,</li>
 *   <li>row 3: ender eye — starts editing the layout of the selected KIT slot,</li>
 *   <li>row 4: glass panes (plus Back / Close for navigation).</li>
 * </ol>
 * Clicking a sign selects the slot (glint + persisted); crystal FFA spawns the player with
 * the selected variant's layout.
 */
public final class CrystalKitSlotsGui extends AbstractGui {

    private final KitService kitService;
    private final KitLayoutRepository layoutRepository;
    private final KitLayoutCache layoutCache;
    private final CrystalFfaStore store;
    private EditKitGui editKitGui;
    private EkitSelectGui ekitSelectGui;

    /** Kit id handed to openPicker and parked until the session exists. */
    private String pendingKitId;

    public CrystalKitSlotsGui(com.rumilance.practice.gui.GuiSessionRegistry registry,
                              com.rumilance.practice.sound.SoundService sounds,
                              com.rumilance.practice.kit.KitService kitService,
                              com.rumilance.practice.database.repository.KitLayoutRepository layoutRepository,
                              com.rumilance.practice.kit.KitLayoutCache layoutCache,
                              com.rumilance.practice.kit.CrystalFfaStore store) {
        super(registry, sounds, GuiType.CRYSTAL_KIT_SLOTS, 4, false);
        this.kitService = kitService;
        this.layoutRepository = layoutRepository;
        this.layoutCache = layoutCache;
        this.store = store;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public void setEkitSelectGui(EkitSelectGui ekitSelectGui) {
        this.ekitSelectGui = ekitSelectGui;
    }

    /** Opens the picker for one kit (the kit must be the declared crystal FFA kit). */
    public void openPicker(Player player, String kitId) {
        this.pendingKitId = kitId;
        open(player);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        if (pendingKitId != null) {
            session.put("crystalKit", pendingKitId);
            pendingKitId = null;
        }
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kitId = session.get("crystalKit", String.class);
        return Component.text("Crystal FFA Kit"
                        + (kitId == null ? "" : ": " + com.rumilance.practice.util.KitNames.pretty(kitId)),
                UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        String kitId = session.get("crystalKit", String.class);
        if (kitId == null) {
            return;
        }
        UUID id = player.getUniqueId();

        // Rows 1, 2 and 4 are flat gray stained glass; only row 3 carries items: one ender
        // eye per KIT slot. Clicking eye N opens the KIT N editor directly.
        for (int row = 0; row < 4; row++) {
            if (row == 2) {
                continue;
            }
            for (int col = 0; col < 9; col++) {
                inventory.setItem(GuiSlots.slot(row, col), filler(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        for (int n = 1; n <= CrystalFfaStore.SLOTS; n++) {
            inventory.setItem(GuiSlots.slot(2, n - 1),
                    ItemBuilder.of(Material.ENDER_EYE)
                            .name(Component.text("Edit KIT" + n, NamedTextColor.AQUA,
                                            TextDecoration.BOLD)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    UiTheme.divider(),
                                    UiTheme.line("Edits the KIT" + n + " layout"),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click to edit")
                            )
                            .action("edit:" + n)
                            .build());
        }
        // Navigation stays in the bottom corners.
        inventory.setItem(GuiSlots.slot(3, 0),
                ItemBuilder.action(UiTheme.BACK, Component.text("Back", UiTheme.VALUE), "back"));
        inventory.setItem(GuiSlots.slot(3, 8),
                ItemBuilder.action(UiTheme.CLOSE, Component.text("Close", UiTheme.VALUE), "close"));
    }

    private org.bukkit.inventory.ItemStack filler(Material material) {
        return ItemBuilder.of(material)
                .name(Component.text(" "))
                .action("decorate")
                .build();
    }

    /** True when the player saved a layout for this kit variant (cache first, then DB). */
    private boolean hasSavedLayout(UUID id, String kitId, int variant) {
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
        String kitId = session.get("crystalKit", String.class);
        if (action == null || "decorate".equals(action)) {
            return;
        }
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-back");
            session.setNavigatingAway(true);
            if (ekitSelectGui != null) {
                ekitSelectGui.open(player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (kitId == null) {
            return;
        }
        if (action.startsWith("pick:")) {
            try {
                int variant = Integer.parseInt(action.substring("pick:".length()));
                store.selectVariant(player.getUniqueId(), variant);
            } catch (NumberFormatException e) {
                return;
            }
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("edit:")) {
            try {
                int variant = Integer.parseInt(action.substring("edit:".length()));
                sounds.play(player, "select");
                session.setNavigatingAway(true);
                if (editKitGui != null) {
                    editKitGui.openKitEditor(player, kitId, null, variant);
                }
            } catch (NumberFormatException e) {
                // bad payload: ignore
            }
        }
    }
}
