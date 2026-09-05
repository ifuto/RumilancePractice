package com.rumilance.practice.admin;

import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.menus.AdminPlayerDataGui;
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
 * Captures the UUID / MCID typed after the admin chose "Player data" in the admin menu and
 * opens the data screen for the resolved player.
 */
public final class AdminPlayerLookupListener implements Listener {

    /** Session flag set by the admin menu while waiting for the lookup input. */
    public static final String AWAIT_LOOKUP = "await_player_lookup";

    private final Plugin plugin;
    private final GuiSessionRegistry guiSessions;
    private final PlayerRepository playerRepository;
    private AdminPlayerDataGui dataGui;

    public AdminPlayerLookupListener(Plugin plugin, GuiSessionRegistry guiSessions,
                                     PlayerRepository playerRepository) {
        this.plugin = plugin;
        this.guiSessions = guiSessions;
        this.playerRepository = playerRepository;
    }

    public void setDataGui(AdminPlayerDataGui dataGui) {
        this.dataGui = dataGui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var sessionOpt = guiSessions.get(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        GuiSession session = sessionOpt.get();
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
