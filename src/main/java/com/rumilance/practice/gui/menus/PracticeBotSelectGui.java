package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.practice.PracticeType;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * ITEM 41: bot-room picker reachable from the Battle Menu. Lists every enabled practice
 * room that runs a combat bot (sword / crystal / mace) and joins it on click — the same
 * flow as {@code /prac <name>}, minus the typing.
 */
public final class PracticeBotSelectGui extends AbstractGui {

    private final PracticeService practiceService;

    public PracticeBotSelectGui(GuiSessionRegistry registry, SoundService sounds,
                                PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_SELECT, 6, true);
        this.practiceService = practiceService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.LIGHT_BLUE;
    }

    @Override
    protected Material titleIcon() {
        return Material.ARMOR_STAND;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.practice-select-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    private List<PracticeRoom> botRooms() {
        List<PracticeRoom> rooms = new ArrayList<>();
        for (PracticeRoom room : practiceService.enabled()) {
            if (room.type() == PracticeType.SWORD
                    || room.type() == PracticeType.CRYSTAL
                    || room.type() == PracticeType.MACE) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        List<PracticeRoom> rooms = botRooms();
        int page = session.page();
        int perPage = MenuScaffold.gridPageSize();
        int from = Math.min(page * perPage, rooms.size());
        int to = Math.min(from + perPage, rooms.size());
        int index = 0;
        for (int i = from; i < to; i++) {
            inventory.setItem(MenuScaffold.gridSlot(index++), roomIcon(player, rooms.get(i)));
        }
        if (rooms.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(10),
                    ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS)
                            .name(t(player, "gui.practice-none").color(UiTheme.MUTED))
                            .lore(UiTheme.line(line(player, "gui.practice-none-lore")))
                            .action("decorate").build());
        }

        paintPaging(player, inventory, page, Math.max(rooms.size(), 1));
        paintNav(player, session, inventory);
    }

    private ItemStack roomIcon(Player player, PracticeRoom room) {
        Material icon = switch (room.type()) {
            case SWORD -> Material.NETHERITE_SWORD;
            case CRYSTAL -> Material.END_CRYSTAL;
            case MACE -> Material.MACE;
            default -> Material.ARMOR_STAND;
        };
        String typeKey = switch (room.type()) {
            case SWORD -> "gui.room-type-sword";
            case CRYSTAL -> "gui.room-type-crystal";
            default -> "gui.room-type-mace";
        };
        String descKey = switch (room.type()) {
            case SWORD -> "gui.bot-room-lore-sword";
            case CRYSTAL -> "gui.bot-room-lore-crystal";
            default -> "gui.bot-room-lore-mace";
        };
        boolean busy = practiceService.isRoomBusy(room.id());
        ItemBuilder builder = ItemBuilder.of(icon)
                .name(Component.text(room.displayName(), busy ? UiTheme.MUTED : UiTheme.SUCCESS)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.room-type-label"), line(player, typeKey)),
                        UiTheme.line(line(player, descKey)),
                        UiTheme.blank());
        if (busy) {
            builder.lore(UiTheme.status(line(player, "gui.practice-room-busy"), UiTheme.WARNING),
                    UiTheme.blank(),
                    UiTheme.hint(line(player, "gui.practice-room-busy-hint")));
        } else {
            builder.lore(UiTheme.hint(line(player, "gui.practice-join-hint")));
        }
        builder.action(busy ? "locked:room" : "join:" + room.id());
        return builder.build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        if (action == null) {
            return;
        }
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "page:prev" -> {
                session.setPage(session.page() - 1);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            case "page:next" -> {
                session.setPage(session.page() + 1);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            default -> {
                if (action.startsWith("locked:")) {
                    sounds.play(player, "error");
                    return;
                }
                if (action.startsWith("join:")) {
                    String id = action.substring("join:".length());
                    sounds.play(player, "select");
                    player.closeInventory();
                    practiceService.join(player, id);
                }
            }
        }
    }
}
