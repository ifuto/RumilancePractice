package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.practice.PracticeType;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
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
 * ITEM 41: Battle-Menu bot entry. The five fight modes the Quantum bot supports — Crystal,
 * Netherite Pot, Mace, Cart PvP and Sword — each bound by an admin to a server kit.
 * Picking a mode joins the first free room of that type (players duel the bot like any
 * other opponent: countdown, difficulty, result).
 */
public final class PracticeBotSelectGui extends AbstractGui {

    private static final PracticeType[] MODES = {
            PracticeType.CRYSTAL, PracticeType.NETHERITE_POT, PracticeType.MACE,
            PracticeType.CART, PracticeType.SWORD};

    private final PracticeService practiceService;

    public PracticeBotSelectGui(GuiSessionRegistry registry, SoundService sounds,
                                PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_SELECT, 5, true);
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

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        // Row 2 — the five modes, centred with breathing room.
        int[] cols = {1, 2, 4, 6, 7};
        for (int i = 0; i < MODES.length; i++) {
            inventory.setItem(GuiSlots.slot(2, cols[i]), modeTile(player, MODES[i]));
        }
        paintNav(player, session, inventory);
    }

    private ItemStack modeTile(Player player, PracticeType mode) {
        Material icon = switch (mode) {
            case CRYSTAL -> Material.END_CRYSTAL;
            case NETHERITE_POT -> Material.SPLASH_POTION;
            case MACE -> Material.MACE;
            case CART -> Material.TNT_MINECART;
            default -> Material.NETHERITE_SWORD;
        };
        String typeKey = switch (mode) {
            case CRYSTAL -> "gui.room-type-crystal";
            case NETHERITE_POT -> "gui.room-type-nethpot";
            case CART -> "gui.room-type-cart";
            case MACE -> "gui.room-type-mace";
            default -> "gui.room-type-sword";
        };
        String descKey = switch (mode) {
            case CRYSTAL -> "gui.bot-room-lore-crystal";
            case NETHERITE_POT -> "gui.bot-room-lore-nethpot";
            case CART -> "gui.bot-room-lore-cart";
            case MACE -> "gui.bot-room-lore-mace";
            default -> "gui.bot-room-lore-sword";
        };
        List<PracticeRoom> rooms = roomsOf(mode);
        long free = rooms.stream().filter(r -> !practiceService.isRoomBusy(r.id())).count();
        String kit = practiceService.botKitFor(mode);
        ItemBuilder builder = ItemBuilder.of(icon)
                .name(t(player, typeKey).color(rooms.isEmpty() ? UiTheme.MUTED : UiTheme.SUCCESS)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, descKey)),
                        UiTheme.blank(),
                        UiTheme.labelValue(line(player, "gui.bot-kit-label"),
                                kit == null || kit.isBlank()
                                        ? line(player, "gui.bot-kit-default") : kit));
        if (rooms.isEmpty()) {
            builder.lore(UiTheme.blank(),
                    UiTheme.status(line(player, "gui.practice-none"), UiTheme.WARNING),
                    UiTheme.hint(line(player, "gui.practice-none-lore")));
            builder.action("locked:mode");
        } else if (free == 0) {
            builder.lore(UiTheme.blank(),
                    UiTheme.status(line(player, "gui.practice-room-busy"), UiTheme.WARNING),
                    UiTheme.hint(line(player, "gui.practice-room-busy-hint")));
            builder.action("locked:mode");
        } else {
            builder.lore(UiTheme.blank(), UiTheme.hint(line(player, "gui.practice-join-hint")));
            builder.action("mode:" + mode.name());
        }
        return builder.build();
    }

    private List<PracticeRoom> roomsOf(PracticeType mode) {
        List<PracticeRoom> out = new ArrayList<>();
        for (PracticeRoom room : practiceService.enabled()) {
            if (room.type() == mode) {
                out.add(room);
            }
        }
        return out;
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
            default -> {
                if (action.startsWith("locked:")) {
                    sounds.play(player, "error");
                    return;
                }
                if (action.startsWith("mode:")) {
                    PracticeType mode;
                    try {
                        mode = PracticeType.valueOf(action.substring(5));
                    } catch (IllegalArgumentException e) {
                        return;
                    }
                    PracticeRoom target = roomsOf(mode).stream()
                            .filter(r -> !practiceService.isRoomBusy(r.id()))
                            .findFirst().orElse(null);
                    if (target == null) {
                        sounds.play(player, "error");
                        player.sendMessage(t(player, "gui.practice-room-busy").color(UiTheme.WARNING));
                        return;
                    }
                    sounds.play(player, "select");
                    player.closeInventory();
                    practiceService.join(player, target.id());
                }
            }
        }
    }
}
