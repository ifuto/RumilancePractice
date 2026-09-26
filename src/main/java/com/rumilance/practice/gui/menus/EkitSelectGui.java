package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitCategory;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * /ekit entry: click a kit to edit immediately. Original kits sit on the bottom-left.
 */
public final class EkitSelectGui extends AbstractGui {

    private final KitService kitService;
    private EditKitGui editKitGui;
    private OriginalKitGui originalKitGui;
    private CrystalKitSlotsGui crystalKitSlotsGui;

    public EkitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.EKIT_SELECT, 6, false);
        this.kitService = kitService;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public void setOriginalKitGui(OriginalKitGui originalKitGui) {
        this.originalKitGui = originalKitGui;
    }

    public void setCrystalKitSlotsGui(CrystalKitSlotsGui crystalKitSlotsGui) {
        this.crystalKitSlotsGui = crystalKitSlotsGui;
    }

    /** Opens the official-kit picker in read-only mode for a tester inspecting another player. */
    public void openViewer(Player viewer, UUID targetId, String targetName) {
        openWithSession(viewer, session -> {
            session.put("mode", "viewer-picker");
            session.setTargetPlayer(targetId);
            session.put("viewer-target-name", targetName == null ? "?" : targetName);
        });
    }

    private static boolean isViewer(GuiSession session) {
        return session != null && "viewer-picker".equals(session.get("mode", String.class));
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.CRAFTING_TABLE;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        if (isViewer(session)) {
            return Component.text("Kit View: "
                            + session.get("viewer-target-name", String.class), UiTheme.PRIMARY)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return t(player, "gui.kit-edit-title").color(UiTheme.PRIMARY);
    }

    /** 開き直すたびに「Main / Sub」の2択から始める（前のカテゴリを持ち越さない）。 */
    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setKitCategory(null);
        session.setPage(0);
    }

    /**
     * Two-step picker (Queue と同じ構成): first screen is just the two wooden category
     * buttons (Main Kits / Sub Kits); pressing one plays the wooden-button sting, holds for
     * 0.2s, then opens that category's kit list. Originl Kit の紙は2択画面からも出しておく。
     */
    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        if (session.kitCategory() == null) {
            renderChooser(player, inventory);
            if (!isViewer(session)) {
                inventory.setItem(GuiSlots.slot(5, 1), originalPaper(player));
            }
            paintNav(player, session, inventory);
            return;
        }
        renderCategory(player, session, inventory, session.kitCategory());
        if (!isViewer(session)) {
            inventory.setItem(GuiSlots.slot(5, 1), originalPaper(player));
        }
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    /** MAIN KITS / SUB KITS — the two wooden buttons of the first screen. */
    private void renderChooser(Player player, Inventory inventory) {
        int mainCount = kitService.enabled(KitCategory.MAIN).size();
        int subCount = kitService.enabled(KitCategory.SUB).size();
        inventory.setItem(MenuScaffold.gridSlot(9),
                com.rumilance.practice.gui.KitSections.categoryButton(
                        KitCategory.MAIN,
                        t(player, "gui.kit-main-button").color(UiTheme.SUCCESS),
                        java.util.List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-main-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(mainCount)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-button-hint")))));
        inventory.setItem(MenuScaffold.gridSlot(11),
                com.rumilance.practice.gui.KitSections.categoryButton(
                        KitCategory.SUB,
                        t(player, "gui.kit-sub-button").color(UiTheme.SECONDARY),
                        java.util.List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-sub-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(subCount)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-button-hint")))));
    }

    /** One category's kits, paginated over the standard content grid. */
    private void renderCategory(Player player, GuiSession session, Inventory inventory, String category) {
        List<KitDefinition> kits = kitService.enabled(
                "SUB".equalsIgnoreCase(category) ? KitCategory.SUB : KitCategory.MAIN);
        int pageSize = MenuScaffold.gridPageSize();
        int pages = Math.max(1, (kits.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(0, session.page()), pages - 1);
        int from = page * pageSize;
        for (int i = 0; i < pageSize && from + i < kits.size(); i++) {
            inventory.setItem(MenuScaffold.gridSlot(i),
                    kitIcon(player, kits.get(from + i), isViewer(session)));
        }
        if (kits.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.kit-none").color(UiTheme.MUTED))
                            .lore(UiTheme.line(line(player, "gui.kit-none-lore")))
                            .action("decorate")
                            .build());
        }
        paintPaging(player, inventory, page, kits.size());
    }

    private ItemStack kitIcon(Player player, KitDefinition kit, boolean viewer) {
        Material material = Material.matchMaterial(kit.icon());
        return ItemBuilder.of(material == null ? Material.DIAMOND_SWORD : material)
                .name(MiniMessage.miniMessage().deserialize(kit.prettyDisplayName())
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        kit.crystalFfa()
                                ? UiTheme.status("Crystal FFA Kit", UiTheme.SUCCESS)
                                : UiTheme.line(line(player, viewer ? "gui.kit-view-only" : "gui.kit-edit-hint")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.kit-button-hint")))
                // Keep the wooden-button delay for the normal picker; viewer actions are still
                // delayed, but use a separate prefix so the editor can remain read-only.
                .action(com.rumilance.practice.gui.DelayedButton.wrap(
                        (viewer ? "viewkit:" : "kit:") + kit.name()))
                .build();
    }

    private ItemStack originalPaper(Player player) {
        return ItemBuilder.of(Material.PAPER)
                .name(t(player, "gui.original-kit").color(UiTheme.DANGER))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "gui.original-kit-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click")))
                .action("original")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action != null && action.startsWith("cat:")) {
            session.setKitCategory(action.substring(4));
            session.setPage(0);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("page:")) {
            List<KitDefinition> kits = kitService.enabled(
                    "SUB".equalsIgnoreCase(session.kitCategory())
                            ? KitCategory.SUB : KitCategory.MAIN);
            int pages = Math.max(1, (kits.size() + MenuScaffold.gridPageSize() - 1)
                    / MenuScaffold.gridPageSize());
            int page = "page:next".equals(action) ? session.page() + 1 : session.page() - 1;
            session.setPage(Math.min(Math.max(0, page), pages - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            if (session.kitCategory() != null) {
                // Back from a category returns to the two wooden buttons, not out of /ekit.
                session.setKitCategory(null);
                session.setPage(0);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
                return;
            }
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("original".equals(action)) {
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            if (originalKitGui != null) {
                originalKitGui.open(player);
            }
            return;
        }
        if (action != null && action.startsWith("viewkit:")) {
            String kitId = action.substring("viewkit:".length());
            UUID targetId = session.targetPlayer();
            if (targetId == null || editKitGui == null) {
                return;
            }
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            editKitGui.openKitViewer(player, targetId,
                    session.get("viewer-target-name", String.class), kitId);
            return;
        }
        if (action != null && action.startsWith("kit:")) {
            String kitId = action.substring(4);
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            // The declared crystal FFA kit edits through the KIT1..9 slot picker instead of
            // the plain editor: each slot keeps its own layout of the same kit.
            boolean crystal = kitService.get(kitId).map(KitDefinition::crystalFfa).orElse(false);
            if (crystal && crystalKitSlotsGui != null) {
                crystalKitSlotsGui.openPicker(player, kitId);
            } else if (editKitGui != null) {
                editKitGui.openKitEditor(player, kitId);
            }
        }
    }
}
