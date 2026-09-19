package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuTile;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.practice.PracticeType;
import com.rumilance.practice.quantum.QuantumRuntime;
import com.rumilance.practice.herobot.HeroBotPlayer;
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
 * Battle-Menu bot entry — the refresh layout. The five fight modes the Quantum bot supports
 * (Crystal, Nethpot, Mace, Cart, Sword), each bound by an admin to a server kit, plus the
 * AFK "no move bot" crystal room below:
 *
 * <pre>
 *   ─────────────────────────────────────────────
 *   CRYSTAL  NETHPOT  MACE  CART  SWORD
 *
 *                    [ NO MOVE BOT ]
 *   ─────────────────────────────────────────────
 * </pre>
 *
 * <p>Every tile carries live capacity: free rooms vs the configured room cap
 * (10 parallel sessions by default — one BOT serves 10 players at once), kit and venue.
 * A mode whose bound kit owns arenas shows free ARENA instances instead.</p>
 */
public final class PracticeBotSelectGui extends AbstractGui {

    private static final PracticeType[] MODES = {
            PracticeType.CRYSTAL, PracticeType.NETHERITE_POT, PracticeType.MACE,
            PracticeType.CART, PracticeType.SWORD};

    private final PracticeService practiceService;
    private QuantumRuntime quantumRuntime;
    /** Opens the AFK BOT Crystal room (the "No move bot" tile). */
    private java.util.function.Consumer<Player> afkEntry;

    public PracticeBotSelectGui(GuiSessionRegistry registry, SoundService sounds,
                                PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_SELECT, 5, true);
        this.practiceService = practiceService;
    }

    /** Wires the real QuantumBOT runtime used by every mode tile. */
    public void setQuantumRuntime(QuantumRuntime quantumRuntime) {
        this.quantumRuntime = quantumRuntime;
    }

    /** Wires the AFK BOT Crystal entry (see {@link #afkEntry}). */
    public void setAfkEntry(java.util.function.Consumer<Player> afkEntry) {
        this.afkEntry = afkEntry;
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
        // Row 1 — the five modes, centred with breathing room.
        int[] cols = {1, 2, 4, 6, 7};
        for (int i = 0; i < MODES.length; i++) {
            inventory.setItem(GuiSlots.slot(1, cols[i]), modeTile(player, MODES[i]));
        }
        // Row 3 centre — the AFK BOT Crystal room ("No move bot"): a passive armored
        // sparring partner on a private 100x100 floor (crystal combos, and mace swings too).
        inventory.setItem(GuiSlots.slot(3, 4),
                ItemBuilder.of(Material.CHERRY_BUTTON)
                        .name(t(player, "gui.bot-nomove-name").color(UiTheme.SUCCESS)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.bot-nomove-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.practice-join-hint")))
                        .action("afkcrystal")
                        .build());
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
        // Kit-bound arenas win as the fight venue (BOT duels are NOT practice rooms);
        // availability then means an idle arena instance, not a free room. A mode whose
        // bound kit owns arenas is enterable even with zero same-type practice rooms.
        List<String> arenaPool = practiceService.botArenaPool(mode);
        boolean arenaVenue = arenaPool != null && !arenaPool.isEmpty();
        // The five selectable modes are real QuantumBOT instances. They are intentionally not
        // limited by the old PracticeService room cap: every click gets its own namespace and
        // can run concurrently with other players' BOT fights.
        boolean quantumReady = this.quantumRuntime != null && this.quantumRuntime.enabled();
        int free = practiceService.botFreeSessions(mode);
        String kit = practiceService.botKitFor(mode);
        String map = arenaVenue ? String.join(" / ", arenaPool) : practiceService.botRoomFor(mode);
        String action = quantumReady ? "mode:" + mode.name() : "locked:mode";

        MenuTile tile = MenuTile.of(player, this, icon, typeKey, UiTheme.SUCCESS, descKey, action);
        if (quantumReady || free > 0) {
            tile.glint(true);
        }
        tile.live(UiTheme.labelValue(line(player, "gui.bot-kit-label"),
                        kit == null || kit.isBlank() ? line(player, "gui.bot-kit-default") : kit),
                UiTheme.labelValue(line(player, "gui.bot-map-label"),
                        map == null || map.isBlank() ? "Quantum map" : map.replace('_', ' ')),
                UiTheme.status(line(player, "menu.bot-free")
                        .replace("<n>", quantumReady ? "∞" : String.valueOf(free)),
                        quantumReady || free > 0 ? UiTheme.SUCCESS : UiTheme.MUTED));
        String lockKey = quantumReady
                ? null : ((rooms.isEmpty() && !arenaVenue) ? "gui.practice-none" : "gui.practice-room-busy");
        // If Quantum is unavailable, do not leave a visually enabled tile whose action is
        // deliberately locked just because the legacy room list still has capacity.
        return tile.build(!quantumReady || free == 0, lockKey);
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
            case "afkcrystal" -> {
                if (afkEntry == null) {
                    sounds.play(player, "error");
                    return;
                }
                sounds.play(player, "select");
                player.closeInventory();
                afkEntry.accept(player);
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
                    if (this.quantumRuntime == null || !this.quantumRuntime.enabled()) {
                        sounds.play(player, "error");
                        player.sendMessage(Component.text("QuantumBOT is unavailable.", UiTheme.WARNING));
                        return;
                    }
                    sounds.play(player, "select");
                    player.closeInventory();
                    try {
                        HeroBotPlayer bot = this.quantumRuntime.spawnBot(player.getLocation(), player);
                        boolean configured = this.quantumRuntime.setOptionFor(bot, quantumOption(mode));
                        player.sendMessage(Component.text(
                                "QuantumBOT spawned: " + bot.profileName()
                                        + (configured ? " (" + mode.name() + ")" : ""),
                                UiTheme.SUCCESS));
                    } catch (RuntimeException error) {
                        player.sendMessage(Component.text(
                                "QuantumBOT could not be spawned: " + error.getMessage(),
                                UiTheme.WARNING));
                    }
                }
            }
        }
    }

    private static String quantumOption(PracticeType mode) {
        return switch (mode) {
            case CRYSTAL -> "crystal";
            case NETHERITE_POT -> "nethpot";
            case MACE -> "mace";
            case CART -> "cart";
            case SWORD -> "sword";
            default -> mode.name().toLowerCase(java.util.Locale.ROOT);
        };
    }
}
