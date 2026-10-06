package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ban.BanService;
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
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.model.KitLayoutSnapshot;
import com.rumilance.practice.model.PlayerData;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.model.PunishmentRecord;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.stats.StatsResetService;
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
 * Admin panel with EVERYTHING stored for one player — and every value editable in place:
 * rank (click cycles up / shift down), settings toggles, chat whitelist (chat-input add,
 * shift clears), ekits (this player / everyone), original kits (clear slots), name color,
 * ranked stats (reset this player / everyone), punishments (lift all), kick and force-end,
 * plus the shift-gated full wipe. This is the GUI face of /urank, /practiceadmin statsreset
 * and the old read-only data screen combined.
 */
public final class AdminPlayerDataGui extends AbstractGui {

    static final String KEY_TARGET = "admin_data_target";
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
    /** Opens the per-kit W/L editor for the current target (wired from FeatureBootstrap). */
    private java.util.function.BiConsumer<Player, UUID> openWlEditor;
    private StatsResetService statsResetService;
    private ChatBanService chatBanService;
    private BanService banService;
    private com.rumilance.practice.match.MatchService matchService;
    private com.rumilance.practice.team.TeamService teamService;
    private org.bukkit.plugin.Plugin plugin;

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

    /** Wires the party service so staff can force-disband a party from here. */
    public void setTeamService(com.rumilance.practice.team.TeamService teamService) {
        this.teamService = teamService;
    }

    /** Wires the W/L editor screen opened by the ranked-stats tile. */
    public void setOpenWlEditor(java.util.function.BiConsumer<Player, UUID> openWlEditor) {
        this.openWlEditor = openWlEditor;
    }

    /** Enables the ranked-stats reset actions (/practiceadmin statsreset path). */
    public void setStatsResetService(StatsResetService statsResetService) {
        this.statsResetService = statsResetService;
    }

    /** Enables the punishment lift action (cache-aware unban). */
    public void setChatBanService(ChatBanService chatBanService) {
        this.chatBanService = chatBanService;
    }

    /** Enables the kick action in this panel. */
    public void setBanService(BanService banService) {
        this.banService = banService;
    }

    /** Enables the force-end action in this panel. */
    public void setMatchService(com.rumilance.practice.match.MatchService matchService) {
        this.matchService = matchService;
    }

    /** Plugin handle for off-main-thread stats resets. */
    public void setPlugin(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
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
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.PLAYER_HEAD;
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
        paintFrame(player, session, inventory);
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

        // --- rank (editable: click cycles up, shift cycles down) ---
        PlayerRank rank = rankService == null ? PlayerRank.NORM : rankService.get(target);
        inventory.setItem(GuiSlots.slot(1, 1),
                ItemBuilder.of(Material.GOLDEN_HELMET)
                        .name(Component.text("Rank: " + rank.name(),
                                rank.isVipPlusOrAbove() ? NamedTextColor.LIGHT_PURPLE
                                        : rank.isVipOrAbove() ? NamedTextColor.GREEN
                                        : rank == PlayerRank.PRO ? NamedTextColor.AQUA : UiTheme.MUTED))
                        .lore(UiTheme.labelValue("Order", "NORM < PRO < VIP < VIP+ < ADMIN"),
                                UiTheme.blank(),
                                UiTheme.hint("Click: promote one step"),
                                UiTheme.hint("Shift-click: demote one step"))
                        .glint(rank != PlayerRank.NORM)
                        .action("act:rank").build());

        // --- settings (editable toggles) ---
        try {
            PlayerSettings settings = settingsService.get(target);
            inventory.setItem(GuiSlots.slot(1, 2),
                    ItemBuilder.of(Material.BOOK)
                            .name(Component.text("Settings", UiTheme.PRIMARY))
                            .lore(UiTheme.labelValue("Locale", settings.locale()),
                                    UiTheme.labelValue("Sounds", settings.soundsEnabled() ? "on" : "off"),
                                    UiTheme.labelValue("Scoreboard", settings.scoreboardEnabled() ? "on" : "off"),
                                    UiTheme.labelValue("Duel requests", settings.acceptDuelRequests() ? "on" : "off"),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click: reset locale to auto"),
                                    UiTheme.hint("Shift-click: toggle sounds"),
                                    UiTheme.hint("Right-click: toggle scoreboard"))
                            .action("act:settings").build());
        } catch (RuntimeException e) {
            errorTile(inventory, GuiSlots.slot(1, 2), "Settings");
        }

        // --- chat whitelist (editable) ---
        List<String> whitelist = List.of();
        try {
            whitelist = List.copyOf(settingsService.get(target).chatWhitelist());
        } catch (RuntimeException e) {
            // no profile yet — render an empty list
        }
        List<Component> wlLore = new ArrayList<>();
        if (whitelist.isEmpty()) {
            wlLore.add(UiTheme.line("empty — sees every chat"));
        } else {
            int shown = 0;
            for (String entry : whitelist) {
                if (shown++ >= 8) {
                    wlLore.add(UiTheme.line("+ " + (whitelist.size() - shown + 1) + " more"));
                    break;
                }
                wlLore.add(UiTheme.line("- " + entry));
            }
        }
        wlLore.add(UiTheme.blank());
        wlLore.add(UiTheme.hint("Click: add by typing name in chat"));
        wlLore.add(UiTheme.hint("(type 'clear' to wipe it)"));
        wlLore.add(UiTheme.hint("Shift-click: clear whitelist now"));
        inventory.setItem(GuiSlots.slot(1, 3),
                ItemBuilder.of(whitelist.isEmpty() ? Material.PAPER : Material.WRITABLE_BOOK)
                        .name(Component.text("Chat whitelist: " + whitelist.size(),
                                UiTheme.PRIMARY))
                        .lore(wlLore.toArray(new Component[0]))
                        .action("act:whitelist").build());

        // --- ekit layouts ---
        List<KitLayoutSnapshot> layouts = safeList(() -> kitLayoutRepository.findAllForPlayer(target));
        List<Component> ekitLore = new ArrayList<>(List.of(layoutLore(layouts)));
        ekitLore.add(UiTheme.blank());
        ekitLore.add(UiTheme.hint("Click: reset THIS player's ekits"));
        ekitLore.add(UiTheme.hint("Shift-click: reset EVERY player's ekits"));
        inventory.setItem(GuiSlots.slot(1, 4),
                ItemBuilder.of(Material.ENDER_CHEST, Math.max(1, layouts.size()))
                        .name(Component.text("Ekit layouts: " + layouts.size(), UiTheme.PRIMARY))
                        .lore(ekitLore.toArray(new Component[0]))
                        .action("act:reset_ekits").build());

        // --- original kits (clearable) ---
        List<String> originalSlots = new ArrayList<>();
        if (originalKitService != null) {
            for (int slot = 0; slot < 9; slot++) {
                if (originalKitService.hasSaved(target, slot)) {
                    originalSlots.add("#" + (slot + 1));
                }
            }
        }
        inventory.setItem(GuiSlots.slot(1, 5),
                ItemBuilder.of(Material.NETHER_STAR)
                        .name(Component.text("Original kits: " + originalSlots.size(), UiTheme.PRIMARY))
                        .lore(originalSlots.isEmpty()
                                        ? UiTheme.line("none saved")
                                        : UiTheme.line(String.join(", ", originalSlots)),
                                UiTheme.blank(),
                                UiTheme.hint("Shift-click: delete ALL of this"),
                                UiTheme.hint("player's original kit slots"))
                        .action("act:original_kits").build());

        // --- name color ---
        NameColorSelection color = nameColorService == null
                ? NameColorSelection.DEFAULT : nameColorService.selection(target);
        inventory.setItem(GuiSlots.slot(1, 6),
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

        // --- ranked stats (resettlable) ---
        List<RankedKitStats> stats = safeList(() -> statsService.allKits(target));
        long wins = stats.stream().mapToLong(RankedKitStats::wins).sum();
        long losses = stats.stream().mapToLong(RankedKitStats::losses).sum();
        List<Component> statsLore = new ArrayList<>();
        statsLore.add(UiTheme.labelValue("Kits played", String.valueOf(stats.size())));
        statsLore.add(UiTheme.labelValue("Wins / Losses", wins + " / " + losses));
        statsLore.addAll(List.of(topPtLines(stats)));
        statsLore.add(UiTheme.blank());
        statsLore.add(UiTheme.hint("Click: open the W/L editor"));
        statsLore.add(UiTheme.hint("(edit wins/losses per kit)"));
        statsLore.add(UiTheme.hint("Shift-click: reset EVERY player's"));
        inventory.setItem(GuiSlots.slot(1, 7),
                ItemBuilder.of(Material.IRON_SWORD)
                        .name(Component.text("Ranked stats (W/L)", UiTheme.PRIMARY))
                        .lore(statsLore.toArray(new Component[0]))
                        .action("act:reset_stats").build());

        // --- punishments (liftable) ---
        List<PunishmentRecord> active = safeList(() -> punishmentRepository.findActiveForPlayer(target));
        List<Component> punishLore = new ArrayList<>(List.of(punishmentLore(active)));
        if (!active.isEmpty()) {
            punishLore.add(UiTheme.blank());
            punishLore.add(UiTheme.hint("Click: lift ALL active punishments"));
        }
        inventory.setItem(GuiSlots.slot(2, 2),
                ItemBuilder.of(active.isEmpty() ? Material.LIME_DYE : Material.RED_DYE)
                        .name(Component.text("Active punishments: " + active.size(),
                                active.isEmpty() ? UiTheme.SUCCESS : UiTheme.DANGER))
                        .lore(punishLore.toArray(new Component[0]))
                        .glint(!active.isEmpty())
                        .action("act:lift_punishments").build());

        // --- kick (online only) ---
        inventory.setItem(GuiSlots.slot(2, 4),
                ItemBuilder.of(online == null ? Material.GRAY_DYE : Material.IRON_BOOTS)
                        .name(Component.text("Kick", online == null ? UiTheme.MUTED : UiTheme.DANGER))
                        .lore(online == null
                                        ? UiTheme.line("target is offline")
                                        : UiTheme.labelValue("Reason", "Kicked by staff"),
                                UiTheme.blank(),
                                UiTheme.hint(online == null ? "-" : "Click: kick now"))
                        .action(online == null ? "decorate" : "act:kick").build());

        // --- force-end ---
        boolean inMatch = matchService != null && matchService.busyReason(target) != null;
        inventory.setItem(GuiSlots.slot(2, 6),
                ItemBuilder.of(inMatch ? Material.TNT_MINECART : Material.MINECART)
                        .name(Component.text("Force-end match",
                                inMatch ? UiTheme.DANGER : UiTheme.MUTED))
                        .lore(inMatch
                                        ? UiTheme.labelValue("State", matchService.busyReason(target))
                                        : UiTheme.line("not in a live match"),
                                UiTheme.blank(),
                                UiTheme.hint("Click: draw-end their match"))
                        .glint(inMatch)
                        .action("act:forceend").build());

        // --- full wipe ---
        inventory.setItem(GuiSlots.slot(2, 7),
                ItemBuilder.of(Material.TNT)
                        .name(Component.text("FULL WIPE this player", UiTheme.DANGER))
                        .lore(UiTheme.line("Rank→NORM, stats, ekits, original kits,"),
                                UiTheme.line("whitelist, locale, name color,"),
                                UiTheme.line("active punishments — everything."),
                                UiTheme.blank(),
                                UiTheme.hint("Shift-click: execute"))
                        .glint(true)
                        .action("act:full_wipe").build());

        // --- force disband party ---
        com.rumilance.practice.team.Team team =
                teamService == null ? null : teamService.teamOf(target);
        inventory.setItem(GuiSlots.slot(3, 4),
                ItemBuilder.of(team == null ? Material.GRAY_DYE : Material.RED_BED)
                        .name(Component.text("Force disband party",
                                team == null ? UiTheme.MUTED : UiTheme.DANGER))
                        .lore(team == null
                                        ? UiTheme.line("not in a party")
                                        : UiTheme.labelValue("Party", team.name()),
                                team == null ? UiTheme.line("")
                                        : UiTheme.labelValue("Members",
                                                String.valueOf(team.members().size())),
                                UiTheme.blank(),
                                UiTheme.hint(team == null ? "-" : "Click: disband for everyone"))
                        .glint(team != null)
                        .action(team == null ? "decorate" : "act:disband_party").build());

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
            if (shown++ >= 8) {
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

    private Component[] topPtLines(List<RankedKitStats> stats) {
        return stats.stream()
                .sorted((a, b) -> Integer.compare(b.pt(), a.pt()))
                .limit(3)
                .map(s -> UiTheme.labelValue(s.kit(), String.valueOf(s.pt())))
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
        if (target == null || action == null || !action.startsWith("act:")) {
            return;
        }
        boolean shift = clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
        boolean right = clickType == org.bukkit.event.inventory.ClickType.RIGHT
                || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
        Player online = Bukkit.getPlayer(target);

        switch (action) {
            case "act:rank" -> {
                if (rankService != null) {
                    PlayerRank current = rankService.get(target);
                    PlayerRank[] order = PlayerRank.values();
                    int index = current.ordinal();
                    PlayerRank next = shift
                            ? order[Math.max(0, index - 1)]
                            : order[Math.min(order.length - 1, index + 1)];
                    if (next != current) {
                        rankService.setRank(target, next);
                        player.sendMessage(Component.text("Rank of " + displayName(target)
                                + ": " + current + " → " + next, NamedTextColor.GREEN));
                    }
                    sounds.play(player, "select");
                }
                refresh(player, session, inventory);
            }
            case "act:settings" -> {
                try {
                    PlayerSettings settings = settingsService.get(target);
                    PlayerSettings next;
                    String note;
                    if (shift) {
                        next = settings.withSoundsEnabled(!settings.soundsEnabled());
                        note = "sounds " + (next.soundsEnabled() ? "on" : "off");
                    } else if (right) {
                        next = settings.withScoreboardEnabled(!settings.scoreboardEnabled());
                        note = "scoreboard " + (next.scoreboardEnabled() ? "on" : "off");
                    } else {
                        next = settings.withLocale(PlayerSettings.LOCALE_AUTO);
                        note = "locale reset to auto";
                    }
                    settingsService.update(next);
                    if (online != null) {
                        online.sendMessage(Component.text(
                                "A setting was changed by an admin (" + note + ").",
                                NamedTextColor.YELLOW));
                    }
                    player.sendMessage(Component.text(
                            displayName(target) + ": " + note + ".", NamedTextColor.GREEN));
                    sounds.play(player, "select");
                } catch (RuntimeException e) {
                    sounds.play(player, "error");
                }
                refresh(player, session, inventory);
            }
            case "act:whitelist" -> {
                if (shift) {
                    try {
                        settingsService.update(settingsService.get(target)
                                .withChatWhitelist(java.util.Set.of()));
                        player.sendMessage(Component.text(
                                "Chat whitelist cleared for " + displayName(target) + ".",
                                NamedTextColor.YELLOW));
                        sounds.play(player, "select");
                    } catch (RuntimeException e) {
                        sounds.play(player, "error");
                    }
                    refresh(player, session, inventory);
                } else {
                    sounds.play(player, "gui-click");
                    session.put(com.rumilance.practice.admin.AdminPlayerLookupListener.AWAIT_WL_TARGET,
                            target.toString());
                    player.closeInventory();
                    player.sendMessage(Component.text(
                            "Type a name to ADD to " + displayName(target)
                                    + "'s chat whitelist, or 'clear' to wipe it.",
                            NamedTextColor.LIGHT_PURPLE));
                }
            }
            case "act:reset_ekits" -> {
                if (shift) {
                    resetAllEkits(player);
                } else {
                    try {
                        int removed = kitLayoutRepository.deleteAllForPlayer(target);
                        kitLayoutCache.unload(target);
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
            case "act:original_kits" -> {
                if (originalKitService != null) {
                    if (shift) {
                        int removed = originalKitService.deleteAllForPlayer(target);
                        player.sendMessage(Component.text("Deleted " + removed
                                + " original-kit slot(s) of " + displayName(target) + ".",
                                NamedTextColor.YELLOW));
                        sounds.play(player, "select");
                    } else {
                        sounds.play(player, "error");
                        player.sendMessage(Component.text(
                                "Shift-click to delete this player's original kit slots.",
                                NamedTextColor.YELLOW));
                    }
                }
                refresh(player, session, inventory);
            }
            case "act:clear_namecolor" -> {
                if (nameColorService != null) {
                    nameColorService.save(target, NameColorSelection.DEFAULT);
                    if (online != null) {
                        nameColorService.applyToPlayer(online);
                    }
                    sounds.play(player, "select");
                }
                refresh(player, session, inventory);
            }
            case "act:reset_stats" -> {
                if (shift) {
                    if (statsResetService == null) {
                        sounds.play(player, "error");
                        refresh(player, session, inventory);
                        return;
                    }
                    adminResetStats(player, null);
                    refresh(player, session, inventory);
                } else if (openWlEditor != null) {
                    sounds.play(player, "gui-click");
                    player.closeInventory();
                    openWlEditor.accept(player, target);
                } else {
                    sounds.play(player, "error");
                }
            }
            case "act:lift_punishments" -> {
                List<PunishmentRecord> active = safeList(() -> punishmentRepository.findActiveForPlayer(target));
                if (active.isEmpty()) {
                    sounds.play(player, "error");
                    refresh(player, session, inventory);
                    return;
                }
                int lifted = 0;
                for (PunishmentRecord record : active) {
                    try {
                        punishmentRepository.revoke(record.id());
                        lifted++;
                    } catch (Exception e) {
                        // keep lifting the rest
                    }
                }
                if (chatBanService != null) {
                    chatBanService.unban(target); // evicts the cached chat-ban record
                }
                player.sendMessage(Component.text("Lifted " + lifted + " punishment(s) for "
                        + displayName(target) + ".", NamedTextColor.GREEN));
                sounds.play(player, "select");
                refresh(player, session, inventory);
            }
            case "act:kick" -> {
                if (online == null) {
                    sounds.play(player, "error");
                    refresh(player, session, inventory);
                    return;
                }
                if (banService != null) {
                    banService.kick(online, player.getName(), "Kicked by staff");
                } else {
                    online.kick(Component.text("Kicked by staff", NamedTextColor.RED));
                }
                player.sendMessage(Component.text("Kicked " + online.getName() + ".",
                        NamedTextColor.GREEN));
                sounds.play(player, "select");
                refresh(player, session, inventory);
            }
            case "act:forceend" -> {
                if (matchService == null || !matchService.forceEndMatch(target)) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            displayName(target) + " is not in a live match.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text(
                            "Force-ended the match of " + displayName(target) + ".",
                            NamedTextColor.GREEN));
                    sounds.play(player, "select");
                }
                refresh(player, session, inventory);
            }
            case "act:disband_party" -> {
                if (teamService == null) {
                    return;
                }
                com.rumilance.practice.team.Team disbanded = teamService.teamOf(target);
                if (disbanded == null) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            displayName(target) + " is not in a party.", NamedTextColor.RED));
                    refresh(player, session, inventory);
                    break;
                }
                String partyName = disbanded.name();
                int members = disbanded.members().size();
                if (teamService.forceDisband(target) == com.rumilance.practice.team.TeamService.Result.OK) {
                    sounds.play(player, "select");
                    player.sendMessage(Component.text("Disbanded party '" + partyName
                            + "' (" + members + " member(s)).", NamedTextColor.GREEN));
                } else {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            "Could not disband that party.", NamedTextColor.RED));
                }
                refresh(player, session, inventory);
            }
            case "act:full_wipe" -> {
                if (!shift) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            "Shift-click the TNT to really wipe " + displayName(target) + ".",
                            NamedTextColor.YELLOW));
                    refresh(player, session, inventory);
                    return;
                }
                // rank → NORM
                if (rankService != null && rankService.get(target) != PlayerRank.NORM) {
                    rankService.setRank(target, PlayerRank.NORM);
                }
                // ekits
                try {
                    kitLayoutRepository.deleteAllForPlayer(target);
                    kitLayoutCache.unload(target);
                } catch (Exception ignored) {
                    // continue the wipe
                }
                // original kits
                if (originalKitService != null) {
                    originalKitService.deleteAllForPlayer(target);
                }
                // whitelist + locale
                try {
                    settingsService.update(settingsService.get(target)
                            .withChatWhitelist(java.util.Set.of())
                            .withLocale(PlayerSettings.LOCALE_AUTO));
                } catch (RuntimeException ignored) {
                    // no profile yet
                }
                // name color
                if (nameColorService != null) {
                    nameColorService.save(target, NameColorSelection.DEFAULT);
                    if (online != null) {
                        nameColorService.applyToPlayer(online);
                    }
                }
                // punishments
                for (PunishmentRecord record : safeList(() -> punishmentRepository.findActiveForPlayer(target))) {
                    try {
                        punishmentRepository.revoke(record.id());
                    } catch (Exception ignored) {
                        // continue
                    }
                }
                if (chatBanService != null) {
                    chatBanService.unban(target);
                }
                // ranked stats
                adminResetStats(player, target);
                if (online != null) {
                    online.sendMessage(Component.text(
                            "Your data was reset by an admin.", NamedTextColor.RED));
                }
                player.sendMessage(Component.text(
                        "FULL WIPE executed for " + displayName(target) + ".", NamedTextColor.RED));
                sounds.play(player, "match-found");
                refresh(player, session, inventory);
            }
            default -> { }
        }
    }

    private String displayName(UUID target) {
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            return online.getName();
        }
        PlayerData data = safe(() -> playerRepository.findByUuid(target).orElse(null));
        return data != null && data.username() != null ? data.username() : target.toString();
    }

    /** Same async shape as /practiceadmin statsreset: DB work off-thread, report on main. */
    void adminResetStats(Player admin, UUID target) {
        if (statsResetService == null) {
            sounds.play(admin, "error");
            return;
        }
        UUID wiped = target;
        boolean async = plugin != null && plugin.isEnabled();
        Runnable report = () -> {
            admin.sendMessage(Component.text(wiped == null
                    ? "ALL stats & ratings reset."
                    : "Stats & rating reset for " + displayName(wiped) + ".", NamedTextColor.GREEN));
            sounds.play(admin, wiped == null ? "match-found" : "select");
        };
        Runnable failure = () -> {
            admin.sendMessage(Component.text("Stats reset failed.", NamedTextColor.RED));
            sounds.play(admin, "error");
        };
        Runnable work = () -> {
            try {
                if (wiped == null) {
                    statsResetService.resetAll();
                } else {
                    statsResetService.resetPlayer(wiped);
                }
                if (async) {
                    Bukkit.getScheduler().runTask(plugin, report);
                } else {
                    report.run();
                }
            } catch (Exception e) {
                if (async) {
                    Bukkit.getScheduler().runTask(plugin, failure);
                } else {
                    failure.run();
                }
            }
        };
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, work);
        } else {
            work.run();
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
