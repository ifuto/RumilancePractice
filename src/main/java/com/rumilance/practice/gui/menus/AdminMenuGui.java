package com.rumilance.practice.gui.menus;

import com.rumilance.practice.admin.AdminPlayerLookupListener;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * OP setup hub — the GUI face of every /practiceadmin subcommand. One-shot switches run
 * through the command bridge so behaviour can never drift from the typed command; screens
 * with their own data (players / matches / toggles / KB default / alt flags) open as
 * dedicated GUIs.
 */
public final class AdminMenuGui extends AbstractGui {

    /** Executes a /practiceadmin subcommand as the admin (wired from FeatureBootstrap). */
    public interface CommandBridge {
        void run(Player admin, String... args);
    }

    private Consumer<Player> openKitAdmin = p -> { };
    private Consumer<Player> openPresetAdmin = p -> { };
    private Consumer<Player> openEkitAdmin = p -> { };
    private java.util.function.Supplier<Boolean> packRequired = () -> false;
    private Consumer<Player> togglePackPolicy = p -> { };
    private Consumer<Player> openSignKitSelect = p -> { };
    private Consumer<Player> openFfaSettings = p -> { };
    private Consumer<Player> openArenaSource = p -> { };
    private Consumer<Player> openPlayers = p -> { };
    private Consumer<Player> openMatches = p -> { };
    private Consumer<Player> openToggles = p -> { };
    private Consumer<Player> openKbDefault = p -> { };
    private Consumer<Player> openAltFlags = p -> { };
    private CommandBridge bridge = (p, args) -> { };
    private Supplier<String> statusLine = () -> "status unavailable";
    private Supplier<Boolean> maintenanceOn = () -> false;
    private Supplier<String> rankedQueueState = () -> "unknown";
    private Supplier<String> ffaGateState = () -> "unknown";

    /** Time-of-day tokens cycled by the sun/moon tile (mirrors /practiceadmin time). */
    private static final List<String> TIME_TOKENS = List.of("day", "noon", "night", "midnight", "sun");

    public AdminMenuGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.ADMIN_MENU, 6, false);
    }

    public void setOpenKitAdmin(Consumer<Player> openKitAdmin) {
        this.openKitAdmin = openKitAdmin == null ? p -> { } : openKitAdmin;
    }

    public void setOpenPresetAdmin(Consumer<Player> openPresetAdmin) {
        this.openPresetAdmin = openPresetAdmin == null ? p -> { } : openPresetAdmin;
    }

    public void setOpenEkitAdmin(Consumer<Player> openEkitAdmin) {
        this.openEkitAdmin = openEkitAdmin == null ? p -> { } : openEkitAdmin;
    }

    /** Resource-pack policy controls (required = kick on decline / recommended = join anyway). */
    public void setPackPolicy(java.util.function.Supplier<Boolean> packRequired,
                              Consumer<Player> togglePackPolicy) {
        this.packRequired = packRequired == null ? () -> false : packRequired;
        this.togglePackPolicy = togglePackPolicy == null ? p -> { } : togglePackPolicy;
    }

    /** Opens the per-arena FFA settings browser. */
    public void setOpenFfaSettings(Consumer<Player> opener) {
        this.openFfaSettings = opener == null ? p -> { } : opener;
    }

    /** Opens the arena/FFA source teleport browser. */
    public void setOpenArenaSource(Consumer<Player> opener) {
        this.openArenaSource = opener == null ? p -> { } : opener;
    }

    /** Opens the queue-sign kit picker (unlimited queue-sign supply). */
    public void setOpenSignKitSelect(Consumer<Player> openSignKitSelect) {
        this.openSignKitSelect = openSignKitSelect == null ? p -> { } : openSignKitSelect;
    }

    /** Opens the online-player browser (kick / force-end / full data editor). */
    public void setOpenPlayers(Consumer<Player> opener) {
        this.openPlayers = opener == null ? p -> { } : opener;
    }

    /** Opens the live-match browser (force-end). */
    public void setOpenMatches(Consumer<Player> opener) {
        this.openMatches = opener == null ? p -> { } : opener;
    }

    /** Opens the queue/map switchboard. */
    public void setOpenToggles(Consumer<Player> opener) {
        this.openToggles = opener == null ? p -> { } : opener;
    }

    /** Opens the default KB profile picker. */
    public void setOpenKbDefault(Consumer<Player> opener) {
        this.openKbDefault = opener == null ? p -> { } : opener;
    }

    /** Opens the alt-flag browser (dismiss). */
    public void setOpenAltFlags(Consumer<Player> opener) {
        this.openAltFlags = opener == null ? p -> { } : opener;
    }

    /** Wires the /practiceadmin command bridge used by every one-shot switch tile. */
    public void setCommandBridge(CommandBridge bridge) {
        this.bridge = bridge == null ? (p, args) -> { } : bridge;
    }

    /** Live "/practiceadmin status" line for the status tile lore. */
    public void setStatusLine(Supplier<String> statusLine) {
        this.statusLine = statusLine == null ? () -> "status unavailable" : statusLine;
    }

    /** Live maintenance flag for the maintenance tile. */
    public void setMaintenanceState(Supplier<Boolean> maintenanceOn) {
        this.maintenanceOn = maintenanceOn == null ? () -> false : maintenanceOn;
    }

    /** Live ranked-queue summary ("ON | auto-unlock ON | 12/50"). */
    public void setRankedQueueState(Supplier<String> rankedQueueState) {
        this.rankedQueueState = rankedQueueState == null ? () -> "unknown" : rankedQueueState;
    }

    /** Live FFA command-gate summary ("OFF" or "ON · /kit, /spawn"). */
    public void setFfaGateState(Supplier<String> ffaGateState) {
        this.ffaGateState = ffaGateState == null ? () -> "unknown" : ffaGateState;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.COMMAND_BLOCK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.admin-title").color(NamedTextColor.AQUA);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        // ---- row 1: content management ----
        inventory.setItem(GuiSlots.slot(1, 1), ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(t(player, "gui.admin-kits").color(NamedTextColor.AQUA))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-kits-lore-1")),
                        UiTheme.line(line(player, "gui.admin-kits-lore-2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("kits")
                .build());

        inventory.setItem(GuiSlots.slot(1, 2), ItemBuilder.of(Material.CHEST)
                .name(t(player, "gui.admin-presets").color(NamedTextColor.YELLOW))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-presets-lore-1")),
                        UiTheme.line(line(player, "gui.admin-presets-lore-2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("presets")
                .build());

        inventory.setItem(GuiSlots.slot(1, 3), ItemBuilder.of(Material.BOOK)
                .name(t(player, "gui.admin-original").color(NamedTextColor.GREEN))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-original-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("ekitadmin")
                .build());

        inventory.setItem(GuiSlots.slot(1, 5), ItemBuilder.of(Material.OAK_SIGN)
                .name(t(player, "gui.admin-sign").color(NamedTextColor.YELLOW))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-sign-lore-1")),
                        UiTheme.line(line(player, "gui.admin-sign-lore-2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("signkit")
                .build());

        // ---- row 2: live state screens ----
        inventory.setItem(GuiSlots.slot(2, 1), ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(Component.text("Live matches", NamedTextColor.RED))
                .lore(UiTheme.divider(),
                        UiTheme.line("Every active match with mode,"),
                        UiTheme.line("kit, participants and state."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: open (force-end inside)"))
                .action("matches")
                .build());

        inventory.setItem(GuiSlots.slot(2, 3), ItemBuilder.of(Material.COMPARATOR)
                .name(t(player, "gui.admin-ffa-settings").color(NamedTextColor.RED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-ffa-settings-lore-1")),
                        UiTheme.line(line(player, "gui.admin-ffa-settings-lore-2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("ffasettings")
                .build());

        inventory.setItem(GuiSlots.slot(2, 5), ItemBuilder.of(Material.ENDER_EYE)
                .name(t(player, "gui.admin-arena-source").color(NamedTextColor.AQUA))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.admin-arena-source-lore-1")),
                        UiTheme.line(line(player, "gui.admin-arena-source-lore-2")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("arenasource")
                .build());

        inventory.setItem(GuiSlots.slot(2, 7), ItemBuilder.of(Material.LEVER)
                .name(Component.text("Queues & maps", NamedTextColor.YELLOW))
                .lore(UiTheme.divider(),
                        UiTheme.line("Enable/disable every kit queue"),
                        UiTheme.line("and every arena map."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: open switchboard"))
                .action("toggles")
                .build());

        // ---- row 3: player tools ----
        inventory.setItem(GuiSlots.slot(3, 1), ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text("Players", NamedTextColor.LIGHT_PURPLE))
                .lore(UiTheme.divider(),
                        UiTheme.line("Online players + chat lookup."),
                        UiTheme.line("Data editor / kick / force-end."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: open browser"))
                .action("players")
                .build());

        inventory.setItem(GuiSlots.slot(3, 2), ItemBuilder.of(Material.GOAT_HORN)
                .name(Component.text("Broadcast", NamedTextColor.YELLOW))
                .lore(UiTheme.divider(),
                        UiTheme.line("Send an [お知らせ] message to every"),
                        UiTheme.line("real player (legacy codes OK, &c)."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: type message in chat"))
                .action("broadcast")
                .build());

        inventory.setItem(GuiSlots.slot(3, 3), ItemBuilder.of(Material.IRON_HORSE_ARMOR)
                .name(Component.text("Default KB profile", NamedTextColor.AQUA))
                .lore(UiTheme.divider(),
                        UiTheme.line("Pick the default knockback profile"),
                        UiTheme.line("from kb/*.json (or off)."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: open picker"))
                .action("kbdefault")
                .build());

        inventory.setItem(GuiSlots.slot(3, 4), ItemBuilder.of(Material.REDSTONE_TORCH)
                .name(Component.text("Alt flags", NamedTextColor.RED))
                .lore(UiTheme.divider(),
                        UiTheme.line("Open alt-detection flags."),
                        UiTheme.line("Dismiss lifts the pair restriction."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: open list"))
                .action("altflags")
                .build());

        boolean required = packRequired.get();
        inventory.setItem(GuiSlots.slot(3, 5), ItemBuilder.of(required
                        ? Material.REDSTONE_TORCH : Material.LEVER)
                .name(t(player, "gui.admin-pack-policy").color(required
                        ? NamedTextColor.RED : NamedTextColor.GREEN))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, required
                                ? "gui.admin-pack-policy-now-required"
                                : "gui.admin-pack-policy-now-recommended"))
                                .color(required ? NamedTextColor.RED : NamedTextColor.GREEN),
                        UiTheme.blank(),
                        UiTheme.line(line(player, "gui.admin-pack-policy-lore-1")),
                        UiTheme.line(line(player, "gui.admin-pack-policy-lore-2")),
                        UiTheme.line(line(player, "gui.admin-pack-policy-lore-3")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .glint(required)
                .action("packpolicy")
                .build());

        inventory.setItem(GuiSlots.slot(3, 6), ItemBuilder.of(Material.CLOCK)
                .name(Component.text("Time / weather", NamedTextColor.YELLOW))
                .lore(UiTheme.divider(),
                        UiTheme.line("Cycles your world through:"),
                        UiTheme.line("day → noon → night → midnight → sun"),
                        UiTheme.blank(),
                        UiTheme.hint("Click: apply next setting"))
                .action("time")
                .build());

        inventory.setItem(GuiSlots.slot(3, 7), ItemBuilder.of(Material.ARMOR_STAND)
                .name(Component.text("Floating items", NamedTextColor.GREEN))
                .lore(UiTheme.divider(),
                        UiTheme.line("Spawn the queue item / Sword-FFA"),
                        UiTheme.line("indicator at your feet, or remove all."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: queue item"),
                        UiTheme.hint("Right-click: Sword-FFA indicator"),
                        UiTheme.hint("Shift-click: remove all"))
                .action("floatspawn")
                .build());

        // ---- row 4: server switches ----
        boolean maintenance = maintenanceOn.get();
        inventory.setItem(GuiSlots.slot(4, 1), ItemBuilder.of(maintenance
                        ? Material.ORANGE_STAINED_GLASS_PANE : Material.GLASS_PANE)
                .name(Component.text("Maintenance: " + (maintenance ? "ON" : "OFF"),
                        maintenance ? NamedTextColor.GOLD : UiTheme.MUTED))
                .lore(UiTheme.divider(),
                        maintenance ? UiTheme.line("Only OPs can join right now.")
                                : UiTheme.line("Server open to everyone."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: toggle"))
                .glint(maintenance)
                .action("maintenance")
                .build());

        inventory.setItem(GuiSlots.slot(4, 2), ItemBuilder.of(Material.EMERALD)
                .name(Component.text("Ranked queue", NamedTextColor.GREEN))
                .lore(UiTheme.divider(),
                        UiTheme.labelValue("State", rankedQueueState.get()),
                        UiTheme.blank(),
                        UiTheme.hint("Click: toggle on/off"),
                        UiTheme.hint("Shift-click: toggle auto-unlock"))
                .action("rankedqueue")
                .build());

        inventory.setItem(GuiSlots.slot(4, 3), ItemBuilder.of(Material.TNT)
                .name(Component.text("Reset ALL ranked stats", UiTheme.DANGER))
                .lore(UiTheme.divider(),
                        UiTheme.line("Wipes rating + all stats for EVERY"),
                        UiTheme.line("player (same as /practiceadmin statsreset)."),
                        UiTheme.blank(),
                        UiTheme.hint("Shift-click: execute"))
                .action("statsreset")
                .build());

        inventory.setItem(GuiSlots.slot(4, 4), ItemBuilder.of(Material.BARRIER)
                .name(Component.text("Cleanup matches", NamedTextColor.RED))
                .lore(UiTheme.divider(),
                        UiTheme.line("Force-cleans every match state"),
                        UiTheme.line("(/practiceadmin cleanup)."),
                        UiTheme.blank(),
                        UiTheme.hint("Shift-click: execute"))
                .action("cleanup")
                .build());

        inventory.setItem(GuiSlots.slot(4, 5), ItemBuilder.of(Material.ANVIL)
                .name(Component.text("Reload configs", NamedTextColor.YELLOW))
                .lore(UiTheme.divider(),
                        UiTheme.line("Reloads kits/arenas/practices/ffa/"),
                        UiTheme.line("lobby/sounds/scoreboard."),
                        UiTheme.blank(),
                        UiTheme.hint("Shift-click: execute"))
                .action("reload")
                .build());

        inventory.setItem(GuiSlots.slot(4, 6), ItemBuilder.of(Material.FIREWORK_ROCKET)
                .name(Component.text("FFA command gate", NamedTextColor.YELLOW))
                .lore(UiTheme.divider(),
                        UiTheme.labelValue("Gate", ffaGateState.get()),
                        UiTheme.blank(),
                        UiTheme.hint("Click: toggle gate on/off"))
                .action("ffagate")
                .build());

        inventory.setItem(GuiSlots.slot(4, 7), ItemBuilder.of(Material.RECOVERY_COMPASS)
                .name(Component.text("Status", NamedTextColor.AQUA))
                .lore(UiTheme.divider(),
                        UiTheme.line(statusLine.get()),
                        UiTheme.blank(),
                        UiTheme.hint("Click: refresh"))
                .action("status")
                .build());

        // ---- row 1 middle: player data chat lookup (kept from the old layout) ----
        inventory.setItem(GuiSlots.slot(1, 6), ItemBuilder.of(Material.NAME_TAG)
                .name(Component.text("Player data editor", NamedTextColor.LIGHT_PURPLE))
                .lore(UiTheme.divider(),
                        UiTheme.line("Type a UUID/MCID in chat to open"),
                        UiTheme.line("the full data screen (offline OK)."),
                        UiTheme.blank(),
                        UiTheme.hint("Click: type name in chat"))
                .action("playerdata")
                .build());

        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.of(Material.BARRIER)
                .name(t(player, "menu.close").color(NamedTextColor.RED))
                .action("close")
                .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || "decorate".equals(action)) {
            return;
        }
        switch (action) {
            case "close" -> player.closeInventory();
            case "kits" -> {
                sounds.play(player, "gui-click");
                openKitAdmin.accept(player);
            }
            case "presets" -> {
                sounds.play(player, "gui-click");
                openPresetAdmin.accept(player);
            }
            case "ekitadmin" -> {
                sounds.play(player, "gui-click");
                openEkitAdmin.accept(player);
            }
            case "packpolicy" -> {
                sounds.play(player, "gui-click");
                togglePackPolicy.accept(player);
            }
            case "ffasettings" -> {
                sounds.play(player, "gui-click");
                openFfaSettings.accept(player);
            }
            case "arenasource" -> {
                sounds.play(player, "gui-click");
                openArenaSource.accept(player);
            }
            case "signkit" -> {
                sounds.play(player, "gui-click");
                openSignKitSelect.accept(player);
            }
            case "players" -> {
                sounds.play(player, "gui-click");
                openPlayers.accept(player);
            }
            case "matches" -> {
                sounds.play(player, "gui-click");
                openMatches.accept(player);
            }
            case "toggles" -> {
                sounds.play(player, "gui-click");
                openToggles.accept(player);
            }
            case "kbdefault" -> {
                sounds.play(player, "gui-click");
                openKbDefault.accept(player);
            }
            case "altflags" -> {
                sounds.play(player, "gui-click");
                openAltFlags.accept(player);
            }
            case "playerdata" -> {
                sounds.play(player, "gui-click");
                session.put(AdminPlayerLookupListener.AWAIT_LOOKUP, Boolean.TRUE);
                player.closeInventory();
                player.sendMessage(Component.text(
                        "Type a UUID or MCID (player name) in chat to inspect their data.",
                        NamedTextColor.LIGHT_PURPLE));
            }
            case "broadcast" -> {
                sounds.play(player, "gui-click");
                session.put(AdminPlayerLookupListener.AWAIT_BROADCAST, Boolean.TRUE);
                player.closeInventory();
                player.sendMessage(Component.text(
                        "Type the broadcast message in chat (&-legacy codes OK).",
                        NamedTextColor.YELLOW));
            }
            case "maintenance" -> {
                sounds.play(player, "gui-click");
                bridge.run(player, "maintenance", maintenanceOn.get() ? "off" : "on");
                refresh(player, session, inventory);
            }
            case "rankedqueue" -> {
                boolean shift = lastClickShift(session);
                sounds.play(player, "gui-click");
                if (shift) {
                    bridge.run(player, "rankedqueue", "autounlock");
                } else {
                    bridge.run(player, "rankedqueue",
                            rankedQueueState.get().startsWith("ON") ? "off" : "on");
                }
                refresh(player, session, inventory);
            }
            case "statsreset" -> {
                if (lastClickShift(session)) {
                    sounds.play(player, "gui-click");
                    bridge.run(player, "statsreset");
                } else {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            "Shift-click the TNT to really reset EVERYONE's ranked stats.",
                            NamedTextColor.YELLOW));
                }
                refresh(player, session, inventory);
            }
            case "cleanup" -> {
                if (lastClickShift(session)) {
                    sounds.play(player, "gui-click");
                    bridge.run(player, "cleanup");
                } else {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            "Shift-click to force-cleanup all matches.",
                            NamedTextColor.YELLOW));
                }
                refresh(player, session, inventory);
            }
            case "reload" -> {
                if (lastClickShift(session)) {
                    sounds.play(player, "gui-click");
                    bridge.run(player, "reload");
                } else {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            "Shift-click to reload safe configs.", NamedTextColor.YELLOW));
                }
                refresh(player, session, inventory);
            }
            case "time" -> {
                Integer index = session.get("admin_time_idx", Integer.class);
                int next = index == null ? 0 : (index + 1) % TIME_TOKENS.size();
                session.put("admin_time_idx", next);
                sounds.play(player, "gui-click");
                bridge.run(player, "time", TIME_TOKENS.get(next));
                refresh(player, session, inventory);
            }
            case "floatspawn" -> {
                org.bukkit.event.inventory.ClickType type = lastClickType(session);
                sounds.play(player, "gui-click");
                if (type == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                        || type == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                    bridge.run(player, "floatingspawn", "removeall");
                } else if (type == org.bukkit.event.inventory.ClickType.RIGHT) {
                    bridge.run(player, "floatingspawn", "swordffa");
                } else {
                    bridge.run(player, "floatingspawn", "queue");
                }
                refresh(player, session, inventory);
            }
            case "ffagate" -> {
                sounds.play(player, "gui-click");
                bridge.run(player, "ffacommand",
                        ffaGateState.get().startsWith("ON") ? "off" : "on");
                refresh(player, session, inventory);
            }
            case "status" -> {
                sounds.play(player, "gui-click");
                bridge.run(player, "status");
                refresh(player, session, inventory);
            }
            default -> { }
        }
    }

    /**
     * The generic click hook receives no ClickType; submenus that need it override the
     * five-arg overload. For this menu the shift-sensitive tiles (ranked auto-unlock,
     * stats-reset-all, cleanup, reload) read the click type recorded by the dispatcher.
     */
    private boolean lastClickShift(GuiSession session) {
        org.bukkit.event.inventory.ClickType type = lastClickType(session);
        return type == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                || type == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
    }

    private org.bukkit.event.inventory.ClickType lastClickType(GuiSession session) {
        org.bukkit.event.inventory.ClickType type =
                session.get("admin_click_type", org.bukkit.event.inventory.ClickType.class);
        return type == null ? org.bukkit.event.inventory.ClickType.LEFT : type;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        session.put("admin_click_type", clickType == null
                ? org.bukkit.event.inventory.ClickType.LEFT : clickType);
        handleClick(player, session, inventory, slot, action);
    }
}
