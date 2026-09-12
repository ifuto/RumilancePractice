package com.rumilance.practice.integration;

import com.rumilance.practice.bootstrap.ServiceRegistry;
import com.rumilance.practice.font.IconFontService;
import com.rumilance.practice.font.RankIconNameTags;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.resourcepack.ResourcePackService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bridge for NEZNAMY/TAB (https://github.com/NEZNAMY/TAB).
 *
 * <p>TAB sorts the client tab list exclusively through its own virtual scoreboard teams
 * (see {@code scoreboard-teams.sorting-types} in TAB's config.yml). The vanilla client can
 * only hold ONE team per player entry, so any other plugin assigning entries to teams wins
 * the last-packet fight against TAB and the sorting dissolves into team-name order. That is
 * exactly what this plugin's rank-icon teams ({@code 2r*} via {@link RankIconNameTags}) and
 * the in-fight battle-order teams ({@code 0*} via {@code MatchTeamVisuals}) did: every
 * scoreboard refresh re-assigned every entry and silently destroyed TAB's ordering.</p>
 *
 * <p>When TAB is present this plugin therefore assigns NO scoreboard teams at all
 * ({@link #tabActive()} — consulted by {@code ScoreboardService} on every refresh, in every
 * context), and instead exposes the rank icons as TAB-native placeholders:</p>
 * <ul>
 *   <li>{@code %rml_rankicon%} — icon of the player holding the placeholder,</li>
 *   <li>{@code %rel_rml_rankicon%} — relational (viewer, target) icon; use this one inside
 *       TAB's {@code tablist-name-format} / nametag formats, e.g.
 *       {@code tablist-name-format: "%rel_rml_rankicon%%player%"}. It honours the viewer's
 *       resource-pack state: pack-less viewers get the text badges (N / N+ / OWNER) instead
 *       of glyph boxes.</li>
 * </ul>
 *
 * <p>Reflection is used throughout (TAB API classes only exist when TAB is installed), and
 * registration is retried on TAB's enable event, so load order never matters. The api used
 * ({@code TabAPI.getInstance().getPlaceholderManager().register*Placeholder}) is stable
 * across TAB 3.x/4.x/5.x.</p>
 */
public final class TabBridge implements Listener {

    private static final String TAB_PLUGIN_NAME = "TAB";

    /** Plain per-player placeholder: the rank icon of the player it resolves for. */
    public static final String PLAYER_PLACEHOLDER = "%rml_rankicon%";
    /** Relational placeholder (viewer, target) — the one to use in player-facing formats. */
    public static final String RELATIONAL_PLACEHOLDER = "%rel_rml_rankicon%";
    /** Placeholder refresh interval in milliseconds (TAB 4.x/5.x interpret ints as ms). */
    private static final int REFRESH_MS = 500;

    private final Plugin plugin;
    private final ServiceRegistry services;
    private final ResourcePackService packService;
    private final Logger logger;

    private volatile boolean tabActive;
    private volatile boolean placeholdersRegistered;

    public TabBridge(Plugin plugin, ServiceRegistry services, ResourcePackService packService) {
        this.plugin = plugin;
        this.services = services;
        this.packService = packService;
        this.logger = plugin.getLogger();
    }

    /** True while TAB is managing the tablist: this plugin must NOT touch scoreboard teams. */
    public boolean tabActive() {
        return tabActive;
    }

    /** Initial detection after bootstrapping (TAB is in plugin.yml {@code softdepend}). */
    public void detect() {
        Plugin tab = Bukkit.getPluginManager().getPlugin(TAB_PLUGIN_NAME);
        if (tab != null && tab.isEnabled()) {
            activate("startup");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginEnable(PluginEnableEvent event) {
        if (TAB_PLUGIN_NAME.equals(event.getPlugin().getName())) {
            activate("enable event");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (TAB_PLUGIN_NAME.equals(event.getPlugin().getName())) {
            tabActive = false;
            placeholdersRegistered = false;
            logger.info("TAB disabled — the scoreboard resumes its own rank-icon / fight teams.");
        }
    }

    private void activate(String trigger) {
        if (!tabActive) {
            tabActive = true;
            logger.info("TAB detected (" + trigger + ") — tablist sorting/names are delegated to TAB; "
                    + "rank icons are served via " + PLAYER_PLACEHOLDER + " and "
                    + RELATIONAL_PLACEHOLDER + " instead of scoreboard teams.");
        }
        registerPlaceholders();
    }

    private void registerPlaceholders() {
        if (placeholdersRegistered) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object manager = apiClass.getMethod("getPlaceholderManager").invoke(api);
            Class<?> managerIfc = Class.forName("me.neznamy.tab.api.placeholder.PlaceholderManager");
            BiFunction<Object, Object, String> relational = this::relationalRankIcon;
            managerIfc.getMethod("registerRelationalPlaceholder",
                    String.class, int.class, BiFunction.class)
                    .invoke(manager, RELATIONAL_PLACEHOLDER, REFRESH_MS, relational);
            Function<Object, String> playerIcon = this::playerRankIcon;
            managerIfc.getMethod("registerPlayerPlaceholder",
                    String.class, int.class, Function.class)
                    .invoke(manager, PLAYER_PLACEHOLDER, REFRESH_MS, playerIcon);
            placeholdersRegistered = true;
            logger.info("Registered TAB placeholders " + PLAYER_PLACEHOLDER + " / "
                    + RELATIONAL_PLACEHOLDER
                    + " — put %rel_rml_rankicon% in TAB's tablist-name-format to keep icons in TAB.");
        } catch (Throwable t) {
            // TAB without its api package (very old build or proxy-only flavour): sorting is
            // already fixed by stepping off the team packets; the icons just stay off TAB.
            logger.log(Level.WARNING, "TAB present but placeholder registration failed — "
                    + "tablist sorting is delegated to TAB, rank icons stay out of it.", t);
        }
    }

    /** {@code %rml_rankicon%}: icon for the owner of the TabPlayer (tab headers, chat, …). */
    private String playerRankIcon(Object tabPlayer) {
        return iconFor(uuidOf(tabPlayer), null);
    }

    /** {@code %rel_rml_rankicon%}: TAB resolves this per (viewer, target) pair in formats. */
    private String relationalRankIcon(Object viewer, Object target) {
        return iconFor(uuidOf(target), uuidOf(viewer));
    }

    private UUID uuidOf(Object tabPlayer) {
        if (tabPlayer == null) {
            return null;
        }
        try {
            Object id = Class.forName("me.neznamy.tab.api.TabPlayer")
                    .getMethod("getUniqueId").invoke(tabPlayer);
            return id instanceof UUID ? (UUID) id : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private String iconFor(UUID targetId, UUID viewerId) {
        if (targetId == null) {
            return "";
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            return "";
        }
        RankService ranks = services.find(RankService.class).orElse(null);
        IconFontService icons = services.find(IconFontService.class).orElse(null);
        if (ranks == null || icons == null || !icons.enabled()) {
            return "";
        }
        boolean viewerHasPack = true;
        if (viewerId != null && packService != null) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                viewerHasPack = packService.hasPack(viewer);
            }
        }
        Component icon = icons.rankIcon(RankIconNameTags.effectiveRank(ranks, target), viewerHasPack);
        if (icon.equals(Component.empty())) {
            return "";
        }
        try {
            // MiniMessage keeps the custom-font glyph intact through TAB's formatter
            // (TAB 5.x has components.minimessage-support: true by default).
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(icon);
        } catch (Throwable t) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(icon);
        }
    }
}
