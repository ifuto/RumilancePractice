package com.rumilance.practice.ban;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class BanLoginListener implements Listener {

    private final BanService banService;

    public BanLoginListener(BanService banService) {
        this.banService = banService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        BanRecord ban = banService.activeBan(event.getUniqueId());
        if (ban == null) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                BanScreens.banned(ban.reason(),
                        ban.permanent() ? "Permanent"
                                : BanDuration.remaining(ban.expiresAtEpochMilli(), System.currentTimeMillis())));
    }
}
