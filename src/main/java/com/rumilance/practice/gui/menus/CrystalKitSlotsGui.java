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
        int selected = store.selectedVariant(id);

        // Row 1: gray stained glass panes.
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(0, col), filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        // Row 2: KIT1..KIT9 signs; clicking one selects the slot this player spawns with.
        for (int n = 1; n <= CrystalFfaStore.SLOTS; n++) {
            boolean saved = hasSavedLayout(id, kitId, n);
            inventory.setItem(GuiSlots.slot(1, n - 1),
                    ItemBuilder.of(Material.OAK_SIGN)
                            .name(Component.text("KIT" + n, NamedTextColor.AQUA, TextDecoration.BOLD)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(
                                    UiTheme.divider(),
                                    saved
                                            ? UiTheme.status("Saved", UiTheme.SUCCESS)
                                            : UiTheme.status("Empty", UiTheme.MUTED),
                                    n == selected
                                            ? UiTheme.status("Selected for FFA", UiTheme.SUCCESS)
                                            : UiTheme.line("Click to select"),
                                    UiTheme.blank(),
                                    UiTheme.hint("Ender eye below edits KIT" + n)
                            )
                            .glint(n == selected)
                            .action("pick:" + n)
                            .build());
        }
        // Row 3: ender eye in the middle — edits the selected slot's layout.
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(2, col), filler(Material.GLASS_PANE));
        }
        inventory.setItem(GuiSlots.slot(2, 4),
                ItemBuilder.of(Material.ENDER_EYE)
                        .name(Component.text("Edit Kit", NamedTextColor.AQUA, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.line("Starts editing KIT" + selected),
                                UiTheme.blank(),
                                UiTheme.hint("Click to edit")
                        )
                        .action("edit")
                        .build());
        // Row 4: glass panes, with navigation in the corners.
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(3, col), filler(Material.GLASS_PANE));
        }
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
        if ("edit".equals(action)) {
            int variant = store.selectedVariant(player.getUniqueId());
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            if (editKitGui != null) {
                editKitGui.openKitEditor(player, kitId, null, variant);
            }
        }
    }
}
