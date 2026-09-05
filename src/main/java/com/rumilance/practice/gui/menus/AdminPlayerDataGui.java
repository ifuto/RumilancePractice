package com.rumilance.practice.gui.menus;

import com.rumilance.practice.cosmetic.namecolor.NameColorSelection;
import com.rumilance.practice.cosmetic.namecolor.NameColorService;
import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.database.repository.PunishmentRepository;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.model.KitLayoutSnapshot;
import com.rumilance.practice.model.PlayerData;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.model.PunishmentRecord;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin panel showing EVERYTHING stored for one player (search by UUID or MCID via the
 * admin menu prompt) plus data-edit actions: reset ekits (this player / everyone), clear the
 * name color, reset the locale to auto.
 */
public final class AdminPlayerDataGui extends AbstractGui {

    private static final String KEY_TARGET = "admin_data_target";
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final PlayerRepository playerRepository;
    private final RankService rankService;
    private final SettingsService settingsService;
    private final KitLayoutRepository kitLayoutRepository;
    private final KitLayoutCache kitLayoutCache;
    private final OriginalKitService originalKitService;
    private final NameColorService nameColorService;
    private final PunishmentRepository punishmentRepository;
    private final StatsService statsService;
    private java.util.function.Consumer<Player> backToAdminMenu = p -> { };

    public AdminPlayerDataGui(GuiSessionRegistry registry, SoundService sounds,
                              PlayerRepository playerRepository, RankService rankService,
                              SettingsService settingsService,
                              KitLayoutRepository kitLayoutRepository, KitLayoutCache kitLayoutCache,
                              OriginalKitService originalKitService, NameColorService nameColorService,
                              PunishmentRepository punishmentRepository, StatsService statsService) {
        super(registry, sounds, GuiType.ADMIN_PLAYER_DATA, 6, false);
        this.playerRepository = playerRepository;
        this.rankService = rankService;
        this.settingsService = settingsService;
        this.kitLayoutRepository = kitLayoutRepository;
        this.kitLayoutCache = kitLayoutCache;
        this.originalKitService = originalKitService;
        this.nameColorService = nameColorService;
        this.punishmentRepository = punishmentRepository;
        this.statsService = statsService;
    }

    public void setBackToAdminMenu(java.util.function.Consumer<Player> backToAdminMenu) {
        this.backToAdminMenu = backToAdminMenu == null ? p -> { } : backToAdminMenu;
    }

    /** Pending lookup targets handed into {@link #configureSession} (session is fresh there). */
    private final java.util.Map<UUID, UUID> pendingTargets = new java.util.concurrent.ConcurrentHashMap<>();

    /** Opens the data screen for {@code target} (may be offline). */
    public void openFor(Player admin, UUID target) {
        pendingTargets.put(admin.getUniqueId(), target);
        open(admin);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        UUID target = pendingTargets.remove(player.getUniqueId());
        if (target != null) {
            session.put(KEY_TARGET, target.toString());
        }
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.admin-data-title").color(NamedTextColor.AQUA);
    }

    private UUID targetOf(GuiSession session) {
        String raw = session.get(KEY_TARGET, String.class);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        UUID target = targetOf(session);
        if (target == null) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(Component.text("No target selected", UiTheme.DANGER))
                            .action("decorate").build());
            backToMenu(inventory, player);
            return;
        }

        PlayerData data = safe(() -> playerRepository.findByUuid(target).orElse(null));
        Player online = Bukkit.getPlayer(target);
        String name = online != null ? online.getName()
                : data != null ? data.username() : target.toString().substring(0, 8);

        // --- header: head + identity ---
        inventory.setItem(GuiSlots.slot(0, 4),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .skullOwner(org.bukkit.Bukkit.getOfflinePlayer(target))
                        .name(Component.text(name, UiTheme.HEADER)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue("UUID", target.toString()),
                                UiTheme.labelValue("Online", online == null ? "no" : "yes"),
                                data == null ? UiTheme.line("No profile stored yet")
                                        : UiTheme.labelValue("First join", TIME_FORMAT.format(data.firstJoin())),
                                data == null ? UiTheme.line("-")
                                        : UiTheme.labelValue("Last seen", TIME_FORMAT.format(data.lastSeen())))
                        .action("decorate").build());

        // --- rank ---
        PlayerRank rank = rankService == null ? PlayerRank.NORM : rankService.get(target);
        inventory.setItem(GuiSlots.slot(1, 1),
                ItemBuilder.of(Material.GOLDEN_HELMET)
                        .name(Component.text("Rank: " + rank.name(),
                                rank.isVipPlusOrAbove() ? NamedTextColor.LIGHT_PURPLE
                                        : rank.isVipOrAbove() ? NamedTextColor.GREEN : UiTheme.MUTED))
                        .lore(UiTheme.line("Change with /urank"),
                                UiTheme.hint("Read-only here"))
                        .action("decorate").build());

        // --- settings ---
        try {
            PlayerSettings settings = settingsService.get(target);
            inventory.setItem(GuiSlots.slot(1, 3),
                    ItemBuilder.of(Material.BOOK)
                            .name(Component.text("Settings", UiTheme.PRIMARY))
                            .lore(UiTheme.labelValue("Locale", settings.locale()),
                                    UiTheme.labelValue("Sounds", settings.soundsEnabled() ? "on" : "off"),
                                    UiTheme.labelValue("Scoreboard", settings.scoreboardEnabled() ? "on" : "off"),
                                    UiTheme.labelValue("Duel requests", settings.acceptDuelRequests() ? "on" : "off"),
                                    UiTheme.labelValue("Whitelist size", String.valueOf(settings.chatWhitelist().size())),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click: reset locale to auto"))
                            .action("act:reset_locale").build());
        } catch (RuntimeException e) {
            errorTile(inventory, GuiSlots.slot(1, 3), "Settings");
        }

        // --- ekit layouts ---
        List<KitLayoutSnapshot> layouts = safeList(() -> kitLayoutRepository.findAllForPlayer(target));
        List<Component> ekitLore = new ArrayList<>(List.of(layoutLore(layouts)));
        ekitLore.add(UiTheme.blank());
        ekitLore.add(UiTheme.hint("Click: reset THIS player's ekits"));
        ekitLore.add(UiTheme.hint("Shift-click: reset EVERY player's ekits"));
        inventory.setItem(GuiSlots.slot(1, 5),
                ItemBuilder.of(Material.ENDER_CHEST, Math.max(1, layouts.size()))
                        .name(Component.text("Ekit layouts: " + layouts.size(), UiTheme.PRIMARY))
                        .lore(ekitLore.toArray(new Component[0]))
                        .action("act:reset_ekits").build());

        // --- original kits ---
        List<String> originalSlots = new ArrayList<>();
        if (originalKitService != null) {
            for (int slot = 0; slot < 9; slot++) {
                if (originalKitService.hasSaved(target, slot)) {
                    originalSlots.add("#" + (slot + 1));
                }
            }
        }
        inventory.setItem(GuiSlots.slot(1, 7),
                ItemBuilder.of(Material.NETHER_STAR)
                        .name(Component.text("Original kits: " + originalSlots.size(), UiTheme.PRIMARY))
                        .lore(originalSlots.isEmpty()
                                ? UiTheme.line("none saved")
                                : UiTheme.line(String.join(", ", originalSlots)))
                        .action("decorate").build());

        // --- name color ---
        NameColorSelection color = nameColorService == null
                ? NameColorSelection.DEFAULT : nameColorService.selection(target);
        inventory.setItem(GuiSlots.slot(3, 1),
                ItemBuilder.of(Material.NAME_TAG)
                        .name(Component.text("Name color: " + color.mode().name().toLowerCase(),
                                color.active() ? UiTheme.SUCCESS : UiTheme.MUTED))
                        .lore(color.active()
                                        ? UiTheme.labelValue("Colors",
                                        color.primaryHex() + (color.mode() == NameColorSelection.Mode.GRADIENT
                                                ? " -> " + color.secondaryHex() : ""))
                                        : UiTheme.line("inactive"),
                                UiTheme.blank(),
                                UiTheme.hint("Click: clear name color"))
                        .action("act:clear_namecolor").build());

        // --- punishments ---
        List<PunishmentRecord> active = safeList(() -> punishmentRepository.findActiveForPlayer(target));
        inventory.setItem(GuiSlots.slot(3, 3),
                ItemBuilder.of(active.isEmpty() ? Material.LIME_DYE : Material.RED_DYE)
                        .name(Component.text("Active punishments: " + active.size(),
                                active.isEmpty() ? UiTheme.SUCCESS : UiTheme.DANGER))
                        .lore(punishmentLore(active))
                        .action("decorate").build());

        // --- ranked stats summary ---
        List<RankedKitStats> stats = safeList(() -> statsService.allKits(target));
        long wins = stats.stream().mapToLong(RankedKitStats::wins).sum();
        long losses = stats.stream().mapToLong(RankedKitStats::losses).sum();
        List<Component> statsLore = new ArrayList<>();
        statsLore.add(UiTheme.labelValue("Kits played", String.valueOf(stats.size())));
        statsLore.add(UiTheme.labelValue("Wins / Losses", wins + " / " + losses));
        statsLore.add(UiTheme.blank());
        statsLore.addAll(List.of(topEloLines(stats)));
        inventory.setItem(GuiSlots.slot(3, 5),
                ItemBuilder.of(Material.IRON_SWORD)
                        .name(Component.text("Ranked stats", UiTheme.PRIMARY))
                        .lore(statsLore.toArray(new Component[0]))
                        .action("decorate").build());

        // --- bulk tools ---
        inventory.setItem(GuiSlots.slot(3, 7),
                ItemBuilder.of(Material.TNT)
                        .name(Component.text("Reset ALL players' ekits", UiTheme.DANGER))
                        .lore(UiTheme.line("Deletes every saved ekit layout on the server."),
                                UiTheme.blank(),
                                UiTheme.hint("Shift-click: execute"))
                        .action("act:reset_ekits_all").build());

        backToMenu(inventory, player);
    }

    private void backToMenu(Inventory inventory, Player player) {
        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(UiTheme.BACK)
                        .name(Component.text("Back", UiTheme.WARNING))
                        .action("back_admin").build());
    }

    private Component[] layoutLore(List<KitLayoutSnapshot> layouts) {
        if (layouts.isEmpty()) {
            return new Component[]{UiTheme.line("none saved")};
        }
        List<Component> lines = new ArrayList<>();
        int shown = 0;
        for (KitLayoutSnapshot snapshot : layouts) {
            if (shown++ >= 10) {
                lines.add(UiTheme.line("+ " + (layouts.size() - shown + 1) + " more"));
                break;
            }
            lines.add(UiTheme.line("- " + snapshot.kit()));
        }
        return lines.toArray(new Component[0]);
    }

    private Component[] punishmentLore(List<PunishmentRecord> records) {
        if (records.isEmpty()) {
            return new Component[]{UiTheme.line("clean record")};
        }
        List<Component> lines = new ArrayList<>();
        for (PunishmentRecord record : records) {
            lines.add(UiTheme.line("- " + record.type() + ": " + record.reason()));
        }
        return lines.toArray(new Component[0]);
    }

    private Component[] topEloLines(List<RankedKitStats> stats) {
        return stats.stream()
                .sorted((a, b) -> Double.compare(b.elo(), a.elo()))
                .limit(3)
                .map(s -> UiTheme.labelValue(s.kit(), String.valueOf((int) s.elo())))
                .toArray(Component[]::new);
    }

    private void errorTile(Inventory inventory, int slot, String label) {
        inventory.setItem(slot, ItemBuilder.of(Material.BARRIER)
                .name(Component.text(label + " unavailable", UiTheme.DANGER))
                .action("decorate").build());
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static <T> T safe(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> List<T> safeList(ThrowingSupplier<List<T>> supplier) {
        try {
            List<T> result = supplier.get();
            return result == null ? List.of() : result;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("back_admin".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            backToAdminMenu.accept(player);
            return;
        }
        UUID target = targetOf(session);
        if (target == null) {
            return;
        }
        boolean shift = clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;

        switch (action) {
            case "act:reset_locale" -> {
                try {
                    PlayerSettings settings = settingsService.get(target);
                    settingsService.update(settings.withLocale(PlayerSettings.LOCALE_AUTO));
                    Player online = Bukkit.getPlayer(target);
                    if (online != null) {
                        online.sendMessage(Component.text(
                                "Your language setting was reset by an admin.", NamedTextColor.YELLOW));
                    }
                    sounds.play(player, "select");
                } catch (RuntimeException e) {
                    sounds.play(player, "error");
                }
                refresh(player, session, inventory);
            }
            case "act:reset_ekits" -> {
                if (shift) {
                    resetAllEkits(player);
                } else {
                    try {
                        int removed = kitLayoutRepository.deleteAllForPlayer(target);
                        kitLayoutCache.unload(target);
                        Player online = Bukkit.getPlayer(target);
                        if (online != null) {
                            online.sendMessage(Component.text(
                                    "Your ekit layouts were reset by an admin.", NamedTextColor.YELLOW));
                        }
                        player.sendMessage(Component.text(
                                "Reset " + removed + " ekit layouts for " + target + ".", NamedTextColor.GREEN));
                        sounds.play(player, "select");
                    } catch (Exception e) {
                        sounds.play(player, "error");
                    }
                }
                refresh(player, session, inventory);
            }
            case "act:reset_ekits_all" -> {
                if (shift) {
                    resetAllEkits(player);
                } else {
                    player.sendMessage(Component.text(
                            "Shift-click the TNT to really reset every player's ekits.",
                            NamedTextColor.YELLOW));
                }
                refresh(player, session, inventory);
            }
            case "act:clear_namecolor" -> {
                if (nameColorService != null) {
                    nameColorService.save(target, NameColorSelection.DEFAULT);
                    Player online = Bukkit.getPlayer(target);
                    if (online != null) {
                        nameColorService.applyToPlayer(online);
                    }
                    sounds.play(player, "select");
                }
                refresh(player, session, inventory);
            }
            default -> { }
        }
    }

    private void resetAllEkits(Player admin) {
        try {
            int removed = kitLayoutRepository.deleteAll();
            admin.sendMessage(Component.text(
                    "Reset ALL ekits (" + removed + " layouts deleted).", NamedTextColor.RED));
            sounds.play(admin, "match-found");
        } catch (Exception e) {
            sounds.play(admin, "error");
        }
    }
}
