package com.rumilance.practice.listener;

import com.rumilance.practice.arrow.ArrowEffectService;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.spectator.SpectatorService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Set;

public final class PracticeSideListener implements Listener {

    private static final Set<String> MSG_COMMANDS = Set.of(
            "/msg", "/tell", "/w", "/whisper", "/reply", "/r", "/message", "/pm"
    );

    private final ChatBanService chatBanService;
    private final SettingsService settingsService;
    private final GuiSessionRegistry guiSessions;
    private final ArrowEffectService arrowEffectService;
    private final SpectatorService spectatorService;
    private final FfaService ffaService;
    private final OriginalKitService originalKitService;

    public PracticeSideListener(
            ChatBanService chatBanService,
            SettingsService settingsService,
            GuiSessionRegistry guiSessions,
            ArrowEffectService arrowEffectService,
            SpectatorService spectatorService,
            FfaService ffaService,
            OriginalKitService originalKitService
    ) {
        this.chatBanService = chatBanService;
        this.settingsService = settingsService;
        this.guiSessions = guiSessions;
        this.arrowEffectService = arrowEffectService;
        this.spectatorService = spectatorService;
        this.ffaService = ffaService;
        this.originalKitService = originalKitService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // NOTE: chat-ban enforcement lives in ChatBanGuardListener (LOWEST, runs even if
        // another plugin cancelled the event). This handler only covers GUI chat input
        // (whitelist / hide-chat input); it must not gate normal chat.
        Player player = event.getPlayer();
        guiSessions.get(player.getUniqueId()).ifPresent(session -> {
            if (Boolean.TRUE.equals(session.get("await_whitelist", Boolean.class))) {
                event.setCancelled(true);
                String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
                session.put("await_whitelist", Boolean.FALSE);
                var settings = settingsService.get(player);
                if (text.equalsIgnoreCase("clear")) {
                    settingsService.update(settings.withChatWhitelist(java.util.Set.of()));
                    player.sendMessage(net.kyori.adventure.text.Component.text("Whitelist cleared.",
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                } else {
                    settingsService.update(settings.withChatWhitelistAdded(text));
                    player.sendMessage(net.kyori.adventure.text.Component.text("Added to whitelist: " + text,
                            net.kyori.adventure.text.format.NamedTextColor.GREEN));
                }
            }
        });
        // hide other chat setting
        try {
            event.viewers().removeIf(audience -> {
                if (!(audience instanceof Player viewer) || viewer.getUniqueId().equals(player.getUniqueId())) {
                    return false;
                }
                var settings = settingsService.get(viewer);
                if (!settings.hideOtherChat()) {
                    return false;
                }
                return !settings.chatWhitelist().contains(player.getName().toLowerCase(Locale.ROOT));
            });
        } catch (Exception e) {
            // Never let an async chat event be killed by a settings/storage hiccup.
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        String base = msg.split(" ")[0];
        if (MSG_COMMANDS.contains(base) && chatBanService.isChatBanned(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("rumilance.punishment.bypass")) {
            event.setCancelled(true);
            chatBanService.blockIfBanned(event.getPlayer(), "private message");
        }
    }

    @EventHandler
    public void onArrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) {
            arrowEffectService.track(arrow, player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        settingsService.unload(event.getPlayer().getUniqueId());
        if (spectatorService.isSpectating(event.getPlayer().getUniqueId())) {
            spectatorService.leave(event.getPlayer(), false);
        }
        if (ffaService.isInFfa(event.getPlayer().getUniqueId())) {
            ffaService.leaveOnQuit(event.getPlayer());
        }
        if (originalKitService != null) {
            originalKitService.restoreOnQuit(event.getPlayer().getUniqueId());
        }
    }
}
