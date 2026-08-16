package com.rumilance.practice.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tiny one-shot prompt utility: when a player runs {@link #await}, their next chat message
 * is captured, cancelled, and delivered to the callback instead of being broadcast. Used by
 * GUIs that need a single line of text input without pulling in an AnvilGUI dependency.
 */
public final class PendingInput implements Listener {

    private static final Map<UUID, Consumer<String>> AWAITING = new ConcurrentHashMap<>();
    private static Plugin plugin;

    public static void init(Plugin plugin) {
        PendingInput.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new PendingInput(), plugin);
    }

    public static void await(Player player, Consumer<String> callback) {
        AWAITING.put(player.getUniqueId(), callback);
    }

    public static boolean isAwaiting(Player player) {
        return AWAITING.containsKey(player.getUniqueId());
    }

    public static void cancel(Player player) {
        AWAITING.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        AWAITING.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Consumer<String> callback = AWAITING.remove(id);
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(text));
    }
}
