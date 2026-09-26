package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.NameDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Per-FFA settings (admin). First page lists every FFA arena; selecting one opens its detail
 * page with toggleable per-arena features (TPA, RTPQueue, FFA Bot — all default OFF) and the
 * arena's top-down size ({@code X blocks x Z blocks}). Persisted to {@code arenas.<id>.settings.*}
 * in ffa.yml. Reached via {@code /ffa settings <arena>} or the admin menu.
 *
 * <p>{@code FFA Bot} ({@code settings.bot}) is the opt-in switch for the mannequin training dummy:
 * while it is OFF, {@code /bot} inside that arena spawns nothing at all — neither the dummy nor the
 * Quantum combat bot — and tells the player where the switch is.</p>
 */
public final class FfaSettingsGui extends AbstractGui {

    private final FfaService ffaService;

    public FfaSettingsGui(GuiSessionRegistry registry, SoundService sounds, FfaService ffaService) {
        super(registry, sounds, GuiType.FFA_SETTINGS, 6, false);
        this.ffaService = ffaService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.COMPARATOR;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.ffa-settings-title").color(UiTheme.SECONDARY);
    }

    /** Arena id the next {@link #open()} call should pre-select, per player. */
    private final java.util.Map<java.util.UUID, String> pendingArena = new java.util.HashMap<>();

    /** Opens the detail page for a single arena directly (used by /ffa settings <arena>). */
    public void open(Player player, String arenaId) {
        if (arenaId == null) {
            pendingArena.remove(player.getUniqueId());
        } else {
            pendingArena.put(player.getUniqueId(), arenaId.toLowerCase(Locale.ROOT));
        }
        open(player);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setPage(0);
        String arenaId = pendingArena.remove(player.getUniqueId());
        session.setSelectedMap(arenaId);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        String arenaId = session.selectedMap();
        if (arenaId == null || ffaService.find(arenaId).isEmpty()) {
            renderArenaList(player, session, inventory);
        } else {
            renderArenaDetail(player, session, inventory, ffaService.find(arenaId).get());
        }
    }

    private void renderArenaList(Player player, GuiSession session, Inventory inventory) {
        List<FfaService.FfaArena> arenas = ffaService.list();
        int pageSize = MenuScaffold.gridPageSize();
        int offset = session.page() * pageSize;
        int placed = 0;
        for (int i = offset; i < arenas.size() && placed < pageSize; i++, placed++) {
            FfaService.FfaArena arena = arenas.get(i);
            Material icon = arena.enabled()
                    ? resolveIcon(arena)
                    : Material.BARRIER;
            inventory.setItem(MenuScaffold.gridSlot(placed), ItemBuilder.of(icon)
                    .name(Component.text(NameDisplay.pretty(arena.id()), UiTheme.SECONDARY))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.labelValue(line(player, "gui.ffa-kit"), arena.kitId()),
                            UiTheme.labelValue(line(player, "gui.ffa-settings-size-label"),
                                    arena.sizeX() + " x " + arena.sizeZ()),
                            UiTheme.blank(),
                            UiTheme.hint(line(player, "menu.click"))
                    )
                    .action("pick:" + arena.id())
                    .build());
        }
        if (arenas.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.ffa-settings-empty").color(UiTheme.MUTED))
                            .action("decorate")
                            .build());
        }
        paintPaging(player, inventory, session.page(), arenas.size());
        paintNav(player, session, inventory);
    }

    private void renderArenaDetail(Player player, GuiSession session, Inventory inventory,
                                   FfaService.FfaArena arena) {
        Material icon = resolveIcon(arena);
        inventory.setItem(MenuScaffold.gridSlot(4), ItemBuilder.of(icon)
                .name(Component.text(NameDisplay.pretty(arena.id()), UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.ffa-kit"), arena.kitId()),
                        UiTheme.labelValue(line(player, "gui.ffa-settings-world-label"), arena.world()),
                        UiTheme.labelValue(line(player, "gui.ffa-settings-size-label"),
                                arena.sizeX() + " x " + arena.sizeZ() + " "
                                        + line(player, "gui.ffa-settings-size-unit")),
                        UiTheme.blank()
                )
                .action("decorate")
                .build());

        inventory.setItem(MenuScaffold.gridSlot(12), toggleItem(player,
                Material.ENDER_PEARL,
                line(player, "gui.ffa-settings-tpa-name"),
                arena.tpaEnabled(),
                line(player, "gui.ffa-settings-tpa-lore"),
                "toggle:tpa"));

        inventory.setItem(MenuScaffold.gridSlot(13), toggleItem(player,
                Material.COMPASS,
                line(player, "gui.ffa-settings-rtp-name"),
                arena.rtpEnabled(),
                line(player, "gui.ffa-settings-rtp-lore"),
                "toggle:rtp"));

        inventory.setItem(MenuScaffold.gridSlot(14), toggleItem(player,
                Material.MAP,
                line(player, "gui.ffa-settings-rtpqueue-name"),
                arena.rtpQueueEnabled(),
                line(player, "gui.ffa-settings-rtpqueue-lore"),
                "toggle:rtpqueue"));

        // Arena-level block interaction rules (persisted on the arena, not the kit).
        inventory.setItem(MenuScaffold.gridSlot(15), toggleItem(player,
                Material.GRASS_BLOCK,
                "Block Place",
                arena.blockPlace(),
                "Allow placing blocks in this arena",
                "toggle:blockplace"));

        inventory.setItem(MenuScaffold.gridSlot(16), toggleItem(player,
                Material.IRON_PICKAXE,
                "Block Break",
                arena.blockBreak(),
                "Allow breaking blocks in this arena",
                "toggle:blockbreak"));

        inventory.setItem(MenuScaffold.gridSlot(17), toggleItem(player,
                Material.OAK_PLANKS,
                "Break Player-Placed Only",
                arena.breakPlayerPlacedOnly(),
                "Only player-placed blocks may be broken",
                "toggle:breakplayerplaced"));

        // FFA Bot（マネキン BOT）: 既定 OFF の任意 ON。このアリーナで /bot が
        // NARENA BOT マネキンを出すかどうかはここだけで決まる。
        inventory.setItem(MenuScaffold.gridSlot(18), toggleItem(player,
                Material.TOTEM_OF_UNDYING,
                line(player, "gui.ffa-settings-bot-name"),
                arena.botEnabled(),
                line(player, "gui.ffa-settings-bot-lore"),
                "toggle:bot"));

        inventory.setItem(MenuScaffold.gridSlot(22), ItemBuilder.of(Material.SPYGLASS)
                .name(t(player, "gui.ffa-settings-size-title").color(UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.ffa-settings-size-label"),
                                arena.sizeX() + " x " + arena.sizeZ() + " "
                                        + line(player, "gui.ffa-settings-size-unit")),
                        UiTheme.line(line(player, "gui.ffa-settings-size-lore")),
                        UiTheme.blank()
                )
                .action("decorate")
                .build());

        inventory.setItem(MenuScaffold.gridSlot(27), ItemBuilder.of(Material.ARROW)
                .name(t(player, "menu.back").color(UiTheme.MUTED))
                .action("nav:list")
                .build());
        paintNav(player, session, inventory);
    }

    private ItemStack toggleItem(Player player, Material material, String name, boolean state,
                                 String lore, String action) {
        String word = state ? line(player, "gui.toggle-on") : line(player, "gui.toggle-off");
        return ItemBuilder.of(material)
                .name(Component.text(name, UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(lore),
                        UiTheme.status(word, state ? UiTheme.SUCCESS : UiTheme.DANGER),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.toggle-hint"))
                )
                .glint(state)
                .action(action)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(session.page() - 1);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("page:next".equals(action)) {
            session.setPage(session.page() + 1);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("pick:")) {
            session.setSelectedMap(action.substring(5));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("nav:list".equals(action)) {
            session.setSelectedMap(null);
            session.setPage(0);
            sounds.play(player, "gui-back");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("toggle:")) {
            FfaService.FfaArena arena = ffaService.find(session.selectedMap()).orElse(null);
            if (arena == null) {
                return;
            }
            switch (action.substring(7)) {
                case "tpa" -> ffaService.setTpaEnabled(arena.id(), !arena.tpaEnabled());
                case "rtp" -> ffaService.setRtpEnabled(arena.id(), !arena.rtpEnabled());
                case "rtpqueue" -> ffaService.setRtpQueueEnabled(arena.id(), !arena.rtpQueueEnabled());
                case "blockplace" -> ffaService.setBlockPlace(arena.id(), !arena.blockPlace());
                case "blockbreak" -> ffaService.setBlockBreak(arena.id(), !arena.blockBreak());
                case "breakplayerplaced" ->
                        ffaService.setBreakPlayerPlacedOnly(arena.id(), !arena.breakPlayerPlacedOnly());
                case "bot" -> ffaService.setBotEnabled(arena.id(), !arena.botEnabled());
                default -> { }
            }
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
        }
    }

    private static Material resolveIcon(FfaService.FfaArena arena) {
        Material mat = Material.matchMaterial(arena.iconMaterial());
        return mat == null ? Material.IRON_SWORD : mat;
    }
}
