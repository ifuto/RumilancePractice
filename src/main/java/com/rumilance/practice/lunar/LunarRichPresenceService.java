package com.rumilance.practice.lunar;

import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Soft integration with Lunar Client Apollo Rich Presence via reflection.
 * Discord shows game name {@code N ARENA} with a player-state label when Apollo is present.
 *
 * <p>For Discord friend activity / Play Stats listing with the official 1:1 logo, the server
 * must also be submitted to Lunar Client
 * <a href="https://www.lunarclient.com/news/what-is-lunar-clients-server-mappings">Server Mappings</a>
 * (see {@code server-mappings/} in this repository). Apollo alone does not set the listing icon.</p>
 */
public final class LunarRichPresenceService implements Listener {

    private static final String GAME_NAME = "N ARENA";
    private static final long REFRESH_TICKS = 30L * 20L;

    private final Plugin plugin;
    private final PlayerStateManager stateManager;
    private final ConcurrentMap<UUID, Boolean> tracked = new ConcurrentHashMap<>();

    private final boolean available;
    private final Object richPresenceModule;
    private final Method getPlayerManager;
    private final Method getApolloPlayer;
    private final Method overridePresence;
    private final Method resetPresence;
    private final Method presenceBuilder;
    private final Method builderGameName;
    private final Method builderGameState;
    private final Method builderPlayerState;
    private final Method builderBuild;

    private BukkitTask refreshTask;
    private boolean loggedMissingIcon;

    public LunarRichPresenceService(Plugin plugin, PlayerStateManager stateManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;

        Object module = null;
        Method getPm = null;
        Method getAp = null;
        Method override = null;
        Method reset = null;
        Method builder = null;
        Method gameName = null;
        Method gameState = null;
        Method playerState = null;
        Method build = null;
        boolean ok = false;

        try {
            Class<?> apolloClass = Class.forName("com.lunarclient.apollo.Apollo");
            Class<?> moduleClass = Class.forName("com.lunarclient.apollo.module.richpresence.RichPresenceModule");
            Class<?> presenceClass = Class.forName("com.lunarclient.apollo.module.richpresence.ServerRichPresence");
            Class<?> apolloPlayerClass = Class.forName("com.lunarclient.apollo.player.ApolloPlayer");

            Method getModuleManager = apolloClass.getMethod("getModuleManager");
            Object moduleManager = getModuleManager.invoke(null);
            Method getModule = moduleManager.getClass().getMethod("getModule", Class.class);
            module = getModule.invoke(moduleManager, moduleClass);

            getPm = apolloClass.getMethod("getPlayerManager");
            Object playerManager = getPm.invoke(null);
            getAp = playerManager.getClass().getMethod("getPlayer", UUID.class);

            override = moduleClass.getMethod("overrideServerRichPresence", apolloPlayerClass, presenceClass);
            reset = moduleClass.getMethod("resetServerRichPresence", apolloPlayerClass);

            builder = presenceClass.getMethod("builder");
            Object builderProbe = builder.invoke(null);
            Class<?> builderClass = builderProbe.getClass();
            gameName = builderClass.getMethod("gameName", String.class);
            gameState = builderClass.getMethod("gameState", String.class);
            playerState = builderClass.getMethod("playerState", String.class);
            build = builderClass.getMethod("build");

            ok = module != null;
            if (ok) {
                plugin.getLogger().info("[Lunar] Apollo Rich Presence soft-integration enabled (N ARENA).");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("[Lunar] Apollo not present — rich presence disabled.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[Lunar] Failed to initialize Apollo rich presence", t);
        }

        this.available = ok;
        this.richPresenceModule = module;
        this.getPlayerManager = getPm;
        this.getApolloPlayer = getAp;
        this.overridePresence = override;
        this.resetPresence = reset;
        this.presenceBuilder = builder;
        this.builderGameName = gameName;
        this.builderGameState = gameState;
        this.builderPlayerState = playerState;
        this.builderBuild = build;
    }

    public boolean isAvailable() {
        return available;
    }

    public void start() {
        warnMissingIconOnce();
        if (!available) {
            return;
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllTracked, REFRESH_TICKS, REFRESH_TICKS);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        tracked.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!available) {
            return;
        }
        Player player = event.getPlayer();
        tracked.put(player.getUniqueId(), Boolean.TRUE);
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(player), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracked.remove(event.getPlayer().getUniqueId());
    }

    public void update(Player player) {
        if (!available || player == null || !player.isOnline()) {
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        String label = labelFor(state);
        apply(player, label, label);
    }

    private void refreshAllTracked() {
        for (UUID id : tracked.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                update(player);
            } else {
                tracked.remove(id);
            }
        }
    }

    static String labelFor(PlayerState state) {
        if (state == null) {
            return "Lobby";
        }
        return switch (state) {
            case LOBBY, IDLE, OPENING_GUI, REQUESTING_DUEL -> "Lobby";
            case QUEUED_RANKED, QUEUED_UNRANKED -> "In Queue";
            case PREPARING_MATCH, COUNTDOWN, FIGHTING, ENDING -> "In Match";
            case FFA -> "FFA";
            case PRACTICE_WAIT, PRACTICE_ACTIVE, EDITING_KIT -> "Practice";
            case SPECTATING -> "Spectating";
        };
    }

    private void apply(Player player, String playerState, String gameState) {
        try {
            Object playerManager = getPlayerManager.invoke(null);
            Object opt = getApolloPlayer.invoke(playerManager, player.getUniqueId());
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) {
                return;
            }
            Object apolloPlayer = optional.get();
            Object builder = presenceBuilder.invoke(null);
            builder = builderGameName.invoke(builder, GAME_NAME);
            builder = builderPlayerState.invoke(builder, playerState);
            builder = builderGameState.invoke(builder, gameState);
            Object presence = builderBuild.invoke(builder);
            overridePresence.invoke(richPresenceModule, apolloPlayer, presence);
        } catch (Throwable t) {
            // Soft-fail: Apollo may briefly lack the player after join.
        }
    }

    private void warnMissingIconOnce() {
        if (loggedMissingIcon) {
            return;
        }
        loggedMissingIcon = true;
        if (plugin.getResource("branding/server-icon.png") == null) {
            plugin.getLogger().warning(
                    "[Branding] branding/server-icon.png missing. Lunar ServerMappings listing icon "
                            + "must still be uploaded separately in the Lunar dashboard.");
        }
    }
}
