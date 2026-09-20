package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class KitSelectGui extends AbstractGui {

    private final KitService kitService;
    private DuelRequestGui duelRequestGui;

    public KitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_SELECT, 6, true);
        this.kitService = kitService;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    public void openFor(Player player, GuiSession parent) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedKit(parent.selectedKit());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        session.setFromBattleMenu(parent.fromBattleMenu());
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.kit-select-title").color(UiTheme.PRIMARY);
    }

    /**
     * Two-step picker. The first screen is only the two wooden category buttons (Main Kits /
     * Sub Kits) — 木時差式ボタン: pressing one holds it on the cursor for 0.3s, then the release
     * click opens that category's kit list. The old layout crammed a header icon plus every
     * kit of both categories onto one screen, which nobody could read.
     */
    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        if (session.kitCategory() == null) {
            renderChooser(player, inventory);
            return;
        }
        renderCategory(player, session, inventory, session.kitCategory());
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    /** MAIN KITS / SUB KITS — the two wooden buttons of the first screen. */
    private void renderChooser(Player player, Inventory inventory) {
        int mainCount = kitService.enabled(com.rumilance.practice.model.KitCategory.MAIN).size();
        int subCount = kitService.enabled(com.rumilance.practice.model.KitCategory.SUB).size();
        inventory.setItem(MenuScaffold.gridSlot(9),
                categoryButton(player, "gui.kit-main-button", "gui.kit-main-button-lore",
                        mainCount, UiTheme.SUCCESS, "cat:MAIN"));
        inventory.setItem(MenuScaffold.gridSlot(11),
                categoryButton(player, "gui.kit-sub-button", "gui.kit-sub-button-lore",
                        subCount, UiTheme.SECONDARY, "cat:SUB"));
    }

    private ItemStack categoryButton(Player player, String nameKey, String loreKey, int count,
                                     net.kyori.adventure.text.format.TextColor color, String action) {
        return ItemBuilder.of(Material.OAK_BUTTON)
                .name(t(player, nameKey).color(color))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, loreKey)),
                        UiTheme.blank(),
                        UiTheme.labelValue(line(player, "gui.kit-count-label"), String.valueOf(count)),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.kit-button-hint"))
                )
                .action(com.rumilance.practice.gui.DelayedButton.wrap(action))
                .build();
    }

    /** One category's kits, paginated over the standard content grid. */
    private void renderCategory(Player player, GuiSession session, Inventory inventory, String category) {
        List<KitDefinition> kits = kitService.enabled(
                "SUB".equalsIgnoreCase(category)
                        ? com.rumilance.practice.model.KitCategory.SUB
                        : com.rumilance.practice.model.KitCategory.MAIN);
        String current = session.selectedKit();
        int pageSize = MenuScaffold.gridPageSize();
        int pages = Math.max(1, (kits.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(0, session.page()), pages - 1);
        int from = page * pageSize;
        for (int i = 0; i < pageSize && from + i < kits.size(); i++) {
            inventory.setItem(MenuScaffold.gridSlot(i),
                    kitIcon(player, session, kits.get(from + i), current));
        }
        paintPaging(player, inventory, page, kits.size());
    }

    private ItemStack kitIcon(Player player, GuiSession session, KitDefinition kit, String current) {
        Material mat = Material.matchMaterial(kit.icon());
        boolean selected = kit.name().equalsIgnoreCase(current);
        return ItemBuilder.of(mat == null ? Material.DIAMOND_SWORD : mat)
                .name(Component.text(KitNames.pretty(kit.name()),
                        selected ? UiTheme.SUCCESS : UiTheme.VALUE))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(kit.prettyDisplayName()),
                        UiTheme.blank(),
                        selected
                                ? UiTheme.status(line(player, "gui.kit-selected"), UiTheme.SUCCESS)
                                : UiTheme.hint(line(player, "gui.kit-click-select"))
                )
                .glint(selected)
                .action("pick:" + kit.name())
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        // 木時差式ボタン already consumed the 0.3s press/release, so this runs on the release.
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
                            ? com.rumilance.practice.model.KitCategory.SUB
                            : com.rumilance.practice.model.KitCategory.MAIN);
            int pages = Math.max(1, (kits.size() + MenuScaffold.gridPageSize() - 1)
                    / MenuScaffold.gridPageSize());
            int page = "page:next".equals(action) ? session.page() + 1 : session.page() - 1;
            session.setPage(Math.min(Math.max(0, page), pages - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("back".equals(action) || "close".equals(action)) {
            if (session.kitCategory() != null) {
                // Back from a category returns to the two wooden buttons, not to the duel.
                session.setKitCategory(null);
                session.setPage(0);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
                return;
            }
            sounds.play(player, "gui-back");
            returnToDuel(player, session);
            return;
        }
        if (action != null && action.startsWith("pick:")) {
            session.setSelectedKit(action.substring(5));
            sounds.play(player, "select");
            returnToDuel(player, session);
        }
    }

    private void returnToDuel(Player player, GuiSession session) {
        Player target = session.targetPlayer() == null ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        String kit = session.selectedKit();
        String map = session.selectedMap();
        int bestOf = session.bestOf();
        boolean ranked = session.ranked();
        boolean fromBattle = session.fromBattleMenu();
        player.closeInventory();
        // Pass the choices into openFor so they are applied to the new session BEFORE render.
        duelRequestGui.openFor(player, target, ranked, kit, map, bestOf);
        registry.get(player.getUniqueId()).ifPresent(s -> s.setFromBattleMenu(fromBattle));
    }
}
