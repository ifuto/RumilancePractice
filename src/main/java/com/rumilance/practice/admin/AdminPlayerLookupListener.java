package com.rumilance.practice.admin;

import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.menus.AdminPlayerDataGui;
import com.rumilance.practice.gui.menus.AdminStatsGui;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Captures admin chat input: the UUID / MCID lookup after "Player data" in the admin menu,
 * the add-to-whitelist input from the admin player-data screen, and the broadcast text from
 * the admin menu. Flags are read from the admin's {@link GuiSession}; one handler per flag.
 */
public final class AdminPlayerLookupListener implements Listener {

    /** Session flag set by the admin menu while waiting for the lookup input. */
    public static final String AWAIT_LOOKUP = "await_player_lookup";

    /**
     * Session value = target UUID (string): waiting for a name to ADD to that player's chat
     * whitelist. The literal {@code clear} wipes the whitelist instead.
     */
    public static final String AWAIT_WL_TARGET = "await_admin_wl_target";

    /** Session flag: waiting for the broadcast message text. */
    public static final String AWAIT_BROADCAST = "await_admin_broadcast";

    /**
     * Session value = "{@code <target-uuid>|<kit>}": waiting for an exact "wins losses"
     * pair (e.g. {@code 12 8}) to write into that player's ranked row for the kit.
     */
    public static final String AWAIT_STATS_TARGET = "await_admin_wl_target";

    private final Plugin plugin;
    private final GuiSessionRegistry guiSessions;
    private final PlayerRepository playerRepository;
    private AdminPlayerDataGui dataGui;
    private com.rumilance.practice.settings.SettingsService settingsService;
    private com.rumilance.practice.stats.StatsService statsService;
    private AdminStatsGui statsGui;

    public AdminPlayerLookupListener(Plugin plugin, GuiSessionRegistry guiSessions,
                                     PlayerRepository playerRepository) {
        this.plugin = plugin;
        this.guiSessions = guiSessions;
        this.playerRepository = playerRepository;
    }

    public void setDataGui(AdminPlayerDataGui dataGui) {
        this.dataGui = dataGui;
    }

    /** Needed to apply chat-whitelist edits for the {@link #AWAIT_WL_TARGET} flow. */
    public void setSettingsService(com.rumilance.practice.settings.SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** Needed to apply the exact W/L write for the {@link #AWAIT_STATS_TARGET} flow. */
    public void setStatsService(com.rumilance.practice.stats.StatsService statsService) {
        this.statsService = statsService;
    }

    /** Editor screen reopened after a successful W/L write. */
    public void setStatsGui(AdminStatsGui statsGui) {
        this.statsGui = statsGui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var sessionOpt = guiSessions.get(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        GuiSession session = sessionOpt.get();
        if (handleWhitelistInput(event, player, session)
                || handleBroadcastInput(event, player, session)
                || handleStatsInput(event, player, session)) {
            return;
        }
        if (!Boolean.TRUE.equals(session.get(AWAIT_LOOKUP, Boolean.class))) {
            return;
        }
        event.setCancelled(true);
        session.put(AWAIT_LOOKUP, Boolean.FALSE);
        String input = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message()).trim();
        if (input.isEmpty()) {
            return;
        }

        UUID resolved = resolve(input);
        if (resolved == null) {
            player.sendMessage(Component.text(
                    "No player found for: " + input, NamedTextColor.RED));
            return;
        }
        if (dataGui == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                dataGui.openFor(player, resolved);
            }
        });
    }

    /** Handles the whitelist-edit input. Returns true when the event was consumed. */
    private boolean handleWhitelistInput(AsyncChatEvent event, Player player, GuiSession session) {
        String targetRaw = session.get(AWAIT_WL_TARGET, String.class);
        if (targetRaw == null || targetRaw.isBlank()) {
            return false;
        }
        event.setCancelled(true);
        session.put(AWAIT_WL_TARGET, null);
        UUID target;
        try {
            target = UUID.fromString(targetRaw);
        } catch (IllegalArgumentException e) {
            return true;
        }
        String input = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message()).trim();
        if (input.isEmpty() || settingsService == null) {
            return true;
        }
        try {
            var settings = settingsService.get(target);
            String name;
            if (input.equalsIgnoreCase("clear")) {
                settingsService.update(settings.withChatWhitelist(java.util.Set.of()));
                name = "  (whitelist cleared)";
            } else {
                settingsService.update(settings.withChatWhitelistAdded(input));
                name = input;
            }
            player.sendMessage(Component.text(
                    "Whitelist updated for " + target.toString().substring(0, 8) + "...: "
                            + name, NamedTextColor.GREEN));
            sounds(player);
        } catch (RuntimeException e) {
            player.sendMessage(Component.text(
                    "Failed to update whitelist (player has no profile yet?).",
                    NamedTextColor.RED));
        }
        return true;
    }

    /** Handles the exact W/L input ("12 8" / "12:8" / "12/8"). Returns true when consumed. */
    private boolean handleStatsInput(AsyncChatEvent event, Player player, GuiSession session) {
        String raw = session.get(AWAIT_STATS_TARGET, String.class);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        event.setCancelled(true);
        session.put(AWAIT_STATS_TARGET, null);
        int sep = raw.indexOf('|');
        if (sep <= 0) {
            return true;
        }
        UUID target;
        try {
            target = UUID.fromString(raw.substring(0, sep));
        } catch (IllegalArgumentException e) {
            return true;
        }
        String kit = raw.substring(sep + 1);
        String input = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message()).trim();
        if (input.isEmpty()) {
            return true;
        }
        String[] parts = input.split("[^0-9]+");
        int wins;
        int losses;
        try {
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new NumberFormatException();
            }
            wins = Integer.parseInt(parts[0]);
            losses = Integer.parseInt(parts[1]);
            if (wins > 1_000_000 || losses > 1_000_000) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                    "Type W/L as two numbers, e.g. '12 8'.", NamedTextColor.RED));
            return true;
        }
        UUID done = target;
        String doneKit = kit;
        int doneWins = wins;
        int doneLosses = losses;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || statsService == null) {
                return;
            }
            try {
                statsService.setWinsLosses(done, doneKit, doneWins, doneLosses);
                player.sendMessage(Component.text("W/L set for " + doneKit + ": "
                        + doneWins + " / " + doneLosses, NamedTextColor.GREEN));
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                if (statsGui != null) {
                    statsGui.openFor(player, done);
                }
            } catch (Exception e) {
                player.sendMessage(Component.text("W/L update failed.", NamedTextColor.RED));
            }
        });
        return true;
    }

    /** Handles the broadcast text input. Returns true when the event was consumed. */
    private boolean handleBroadcastInput(AsyncChatEvent event, Player player, GuiSession session) {
        if (!Boolean.TRUE.equals(session.get(AWAIT_BROADCAST, Boolean.class))) {
            return false;
        }
        event.setCancelled(true);
        session.put(AWAIT_BROADCAST, Boolean.FALSE);
        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message()).trim();
        if (message.isEmpty()) {
            return true;
        }
        Component line = Component.text("[", NamedTextColor.YELLOW)
                .append(Component.text("お知らせ", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.YELLOW))
                .append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(message));
        for (org.bukkit.entity.Player viewer : com.rumilance.practice.util.RealPlayers.online()) {
            viewer.sendMessage(line);
        }
        player.sendMessage(Component.text("Broadcast sent.", NamedTextColor.GREEN));
        return true;
    }

    private void sounds(Player player) {
        try {
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        } catch (Exception ignored) {
            // sound feedback only
        }
    }

    /** Accepts a raw UUID, or an MCID/username looked up from stored profiles (or online). */
    private UUID resolve(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // fall through to name lookup
        }
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            return playerRepository.findByUsername(input)
                    .map(data -> data.uuid())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
