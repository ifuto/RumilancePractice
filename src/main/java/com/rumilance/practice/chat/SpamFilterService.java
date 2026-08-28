package com.rumilance.practice.chat;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.SpamDetectionRepository;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.punishment.SpamBanDuration;
import com.rumilance.practice.util.AsyncExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rate/duplication spam filter. Detections are counted persistently; once a player accumulates
 * enough detections they receive an escalating ChatBan (2 weeks -> 1 month -> 3 months). The
 * short in-memory window only decides whether a single message counts as spam; the long-term
 * escalation state lives in the database so restarts never wipe a spammer's record.
 */
public final class SpamFilterService {

    private static final class Recent {
        final Deque<Long> timestamps = new ArrayDeque<>();
        String lastMessage = "";
        int repeatCount;
    }

    private final ConfigService configService;
    private final SpamDetectionRepository repository;
    private final ChatBanService chatBanService;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;
    private final Map<UUID, Recent> recents = new ConcurrentHashMap<>();

    public SpamFilterService(ConfigService configService, SpamDetectionRepository repository,
                             ChatBanService chatBanService, AsyncExecutor asyncExecutor, Logger logger) {
        this.configService = configService;
        this.repository = repository;
        this.chatBanService = chatBanService;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    private boolean enabled() {
        return configService.config().getBoolean("spam-filter.enabled", true);
    }

    private int maxMessages() {
        return configService.config().getInt("spam-filter.max-messages", 5);
    }

    private long windowMillis() {
        return configService.config().getLong("spam-filter.window-seconds", 10L) * 1000L;
    }

    private int minLength() {
        return configService.config().getInt("spam-filter.min-message-length", 2);
    }

    private int maxRepeat() {
        return configService.config().getInt("spam-filter.max-repeat", 3);
    }

    private int banThreshold() {
        return configService.config().getInt("spam-filter.ban-threshold", 10);
    }

    /**
     * @return true when the message is spam and should be cancelled. Detection counting and any
     *         resulting escalation ChatBan are dispatched asynchronously.
     */
    public boolean isSpam(Player player, String message) {
        if (!enabled() || player.hasPermission("rumilance.punishment.bypass")) {
            return false;
        }
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < minLength()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Recent recent = recents.computeIfAbsent(player.getUniqueId(), ignored -> new Recent());
        boolean spam;
        synchronized (recent) {
            long window = windowMillis();
            recent.timestamps.addLast(now);
            while (!recent.timestamps.isEmpty() && recent.timestamps.peekFirst() < now - window) {
                recent.timestamps.removeFirst();
            }
            boolean tooFast = recent.timestamps.size() > maxMessages();
            if (normalized.equals(recent.lastMessage)) {
                recent.repeatCount++;
            } else {
                recent.repeatCount = 0;
                recent.lastMessage = normalized;
            }
            boolean repeating = recent.repeatCount >= maxRepeat();
            spam = tooFast || repeating;
        }
        if (spam) {
            onDetected(player);
        }
        return spam;
    }

    private void onDetected(Player player) {
        UUID uuid = player.getUniqueId();
        player.sendMessage(Component.text("スパムと判定されたためメッセージを送信できません。",
                NamedTextColor.RED));
        int threshold = banThreshold();
        asyncExecutor.execute(() -> {
            try {
                SpamDetectionRepository.Counts counts = repository.incrementDetection(uuid);
                if (counts.detections >= threshold && !chatBanService.isChatBanned(uuid)) {
                    int offense = counts.autoBans + 1;
                    Duration duration = SpamBanDuration.forOffense(offense);
                    chatBanService.issue(uuid, null, "CHATBAN",
                            "Spam filter (" + counts.detections + " detections)", duration, false);
                    repository.incrementAutoBan(uuid);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Spam filter detection failed", e);
            }
        });
    }

    public void unload(UUID uuid) {
        recents.remove(uuid);
    }
}
