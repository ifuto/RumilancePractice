package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Map picker for unranked player duels. Random (ender eye) is the default; named maps use
 * grass blocks. The map system will grow later — this UI already reads the kit's arena pool.
 */
public final class DuelMapSelectGui extends AbstractGui {

    private final KitService kitService;
    private final DuelRequestGui duelRequestGui;
    private final MessageService messageService;

    public DuelMapSelectGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            DuelRequestGui duelRequestGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.DUEL_MAP, 6, true);
        this.kitService = kitService;
        this.duelRequestGui = duelRequestGui;
        this.messageService = messageService;
    }

    public void openFor(Player player, GuiSession parent) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedKit(parent.selectedKit());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        session.setFromBattleMenu(parent.fromBattleMenu());
        session.setFromGameMenu(parent.fromGameMenu());
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return text(player, "duel-gui.map-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        String current = normalizeMap(session.selectedMap());
        boolean randomSelected = "random".equals(current);

        inventory.setItem(MenuScaffold.gridSlot(0), ItemBuilder.of(Material.ENDER_EYE)
                .name(text(player, "duel-gui.map-random").color(UiTheme.PRIMARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(raw(player, "duel-gui.map-random-lore")),
                        UiTheme.blank(),
                        randomSelected
                                ? UiTheme.status(raw(player, "duel-gui.selected"), UiTheme.SUCCESS)
                                : UiTheme.hint(raw(player, "menu.click"))
                )
                .glint(randomSelected)
                .action("map:random")
                .build());

        List<String> pool = arenaPool(session.selectedKit());
        int index = 1;
        for (String arena : pool) {
            if (index >= MenuScaffold.gridPageSize() - 1) {
                break;
            }
            boolean selected = arena.equalsIgnoreCase(current);
            inventory.setItem(MenuScaffold.gridSlot(index++), ItemBuilder.of(Material.GRASS_BLOCK)
                    .name(Component.text(KitNames.pretty(arena), UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.line(raw(player, "duel-gui.map-named-lore")),
                            UiTheme.blank(),
                            selected
                                    ? UiTheme.status(raw(player, "duel-gui.selected"), UiTheme.SUCCESS)
                                    : UiTheme.hint(raw(player, "menu.click"))
                    )
                    .glint(selected)
                    .action("map:" + arena)
                    .build());
        }

        inventory.setItem(MenuScaffold.gridSlot(MenuScaffold.gridPageSize() - 1), ItemBuilder.of(Material.BOOK)
                .name(text(player, "duel-gui.map-note").color(UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(raw(player, "duel-gui.map-coming")),
                        pool.isEmpty()
                                ? UiTheme.line(raw(player, "duel-gui.map-any-lore"))
                                : UiTheme.blank()
                )
                .action("decorate")
                .build());

        MenuScaffold.returnButton(inventory);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            returnToDuel(player, session);
            return;
        }
        if (action != null && action.startsWith("map:")) {
            String map = action.substring(4);
            session.setSelectedMap("random".equalsIgnoreCase(map) ? "random" : map.toLowerCase());
            sounds.play(player, "select");
            returnToDuel(player, session);
        }
    }

    private void returnToDuel(Player player, GuiSession session) {
        Player target = session.targetPlayer() == null ? null : Bukkit.getPlayer(session.targetPlayer());
        if (target == null) {
            player.closeInventory();
            return;
        }
        String kit = session.selectedKit();
        String map = session.selectedMap();
        int bestOf = session.bestOf();
        boolean ranked = session.ranked();
        boolean fromBattle = session.fromBattleMenu();
        duelRequestGui.openFor(player, target, ranked);
        registry.get(player.getUniqueId()).ifPresent(s -> {
            s.setSelectedKit(kit);
            s.setSelectedMap(map);
            s.setBestOf(bestOf);
            s.setFromBattleMenu(fromBattle);
        });
    }

    private List<String> arenaPool(String kitId) {
        if (kitId == null) {
            return List.of();
        }
        return kitService.get(kitId).map(KitDefinition::arenas).orElse(List.of());
    }

    static String normalizeMap(String map) {
        if (map == null || map.isBlank() || "random".equalsIgnoreCase(map)) {
            return "random";
        }
        return map.toLowerCase();
    }

    private Component text(Player player, String key) {
        return messageService.render(messageService.resolveLocale(player), key);
    }

    private String raw(Player player, String key) {
        return messageService.localeService().rawMessage(messageService.resolveLocale(player), key);
    }
}
