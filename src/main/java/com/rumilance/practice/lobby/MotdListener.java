package com.rumilance.practice.lobby;

import com.rumilance.practice.ban.BanScreens;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public final class MotdListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ServerListPingEvent event) {
        event.motd(BanScreens.motd());
    }
}
