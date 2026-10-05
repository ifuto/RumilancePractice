package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.util.TpsTracker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /tps} — server TPS as a percentage, plus any dip to {@value TpsTracker#DIP_THRESHOLD}
 * or below during the last 24 hours.
 *
 * <p>Replaces the built-in command: the {@code rumilance.tps} node is declared {@code op} by
 * default, so normal players cannot run it and LuckPerms decides who can.</p>
 */
public final class TpsCommand implements CommandExecutor {

    /** Anti-spam: how long a player must wait between two readings. */
    public static final long COOLDOWN_MS = 10_000L;

    private final TpsTracker tracker;
    private final MessageService messages;
    private final Map<UUID, Long> lastUsed = new ConcurrentHashMap<>();
    private final DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private final ZoneId zone = ZoneId.systemDefault();

    public TpsCommand(TpsTracker tracker, MessageService messages) {
        this.tracker = tracker;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        long now = System.currentTimeMillis();
        if (sender instanceof Player player) {
            Long previous = lastUsed.get(player.getUniqueId());
            if (previous != null && now - previous < COOLDOWN_MS) {
                long seconds = (COOLDOWN_MS - (now - previous) + 999L) / 1000L;
                messages.send(player, "tps.cooldown",
                        MessageService.tags("secs", String.valueOf(seconds)));
                return true;
            }
            lastUsed.put(player.getUniqueId(), now);
        }

        messages.send(sender, "tps.header",
                MessageService.tags(
                        "percent", String.format(Locale.ROOT, "%.1f", tracker.averagePercent()),
                        "tps", String.format(Locale.ROOT, "%.2f", tracker.averageTps())));

        List<TpsTracker.Dip> dips = tracker.recentDips(now);
        if (dips.isEmpty()) {
            messages.send(sender, "tps.no-dip",
                    MessageService.tags("threshold",
                            String.format(Locale.ROOT, "%.1f", TpsTracker.DIP_THRESHOLD)));
            return true;
        }
        messages.send(sender, "tps.dip-title",
                MessageService.tags("threshold",
                        String.format(Locale.ROOT, "%.1f", TpsTracker.DIP_THRESHOLD)));
        for (TpsTracker.Dip dip : dips) {
            messages.send(sender, "tps.dip-line",
                    MessageService.tags(
                            "from", time(dip.startMillis()),
                            "to", dip.ongoing() ? "…" : time(dip.endMillis())));
        }
        return true;
    }

    private String time(long epochMillis) {
        return clock.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}
