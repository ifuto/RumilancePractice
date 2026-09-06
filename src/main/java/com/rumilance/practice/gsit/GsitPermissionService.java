package com.rumilance.practice.gsit;

import com.rumilance.practice.config.ConfigService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GSit permission bridge (soft dependency on LuckPerms).
 *
 * <p>This plugin no longer implements sitting itself — the external <strong>GSit</strong> plugin
 * owns that interaction, and the built-in lobby seats were removed so the two can never fight
 * over the same right-click (both used to cancel {@code PlayerInteractEvent} and spawn their own
 * seat armour stand for the very same stair/slab click).</p>
 *
 * <p>To keep GSit's surface exactly as small as this server wants it, every joining player is
 * normalised in LuckPerms:</p>
 * <ol>
 *   <li>ALL {@code GSit.*} nodes are removed from the player's own data — including the wildcard
 *       node {@code gsit.*} and negated variants ({@code -gsit.sit}), so nothing can re-grant or
 *       veto the rest;</li>
 *   <li>exactly {@code GSit.SitClick} is granted, i.e. "sit down by clicking a block" and nothing
 *       else (no {@code /sit} command, no crawling, no belt/seat extras).</li>
 * </ol>
 *
 * <p>The edit is applied to the LuckPerms user object, which recalculates the player's
 * permissions immediately, and is then persisted asynchronously. When the player already is in
 * the wanted state nothing is written, so joins stay free of database churn. If LuckPerms is not
 * installed the bridge logs once and does nothing — GSit then works with whatever permissions the
 * server gives it.</p>
 */
public final class GsitPermissionService implements Listener {

    /** Prefix of every GSit permission node (matched case-insensitively). */
    private static final String GSIT_PREFIX = "gsit.";
    /** Root node: {@code gsit} alone is LuckPerms' wildcard for the whole plugin. */
    private static final String GSIT_ROOT = "gsit";
    /** Fallback when config.yml has no usable value. */
    private static final String DEFAULT_GRANT = "GSit.SitClick";

    private final Plugin plugin;
    private final ConfigService configService;
    /** Players whose normalisation is in flight, so a reconnect cannot double-apply. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile LuckPerms luckPerms;
    private boolean reportedMissing;

    public GsitPermissionService(Plugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    // ------------------------------------------------------------------ configuration

    /** Resolves the LuckPerms API. Safe to call when LuckPerms is absent (logs on first join). */
    public void hook() {
        if (!enabled()) {
            return;
        }
        luckPerms = resolveApi();
        if (luckPerms != null) {
            plugin.getLogger().info("[GSit] permission bridge active: every join strips GSit.* and grants "
                    + grantNode());
        }
    }

    public void shutdown() {
        inFlight.clear();
        luckPerms = null;
    }

    /** True when the bridge is switched on in config.yml ({@code gsit.enabled}). */
    public boolean enabled() {
        return configService != null && configService.config().getBoolean("gsit.enabled", true);
    }

    /** The single GSit node players end up with. */
    public String grantNode() {
        String node = configService == null ? null : configService.config().getString("gsit.grant");
        return node == null || node.isBlank() ? DEFAULT_GRANT : node.trim();
    }

    // ------------------------------------------------------------------ node classification

    /**
     * True for every node this bridge owns: {@code gsit.*}, the bare {@code gsit} wildcard, and
     * any negated variant of those. Group inheritance nodes ({@code inheritance.*}) and meta are
     * NOT touched — removing a group would change far more than sitting.
     */
    static boolean isGsitNode(String key) {
        if (key == null) {
            return false;
        }
        String trimmed = key.trim();
        // LuckPerms writes negations as a leading '-' ("--" for an explicit false), and a
        // negated GSit node is still a GSit node: leaving "-gsit.*" behind would keep the
        // wildcard in the player's data.
        int from = 0;
        while (from < trimmed.length() && trimmed.charAt(from) == '-') {
            from++;
        }
        String plain = trimmed.substring(from);
        return plain.equalsIgnoreCase(GSIT_ROOT)
                || plain.regionMatches(true, 0, GSIT_PREFIX, 0, GSIT_PREFIX.length());
    }

    /**
     * The wanted end state for one player: which of their permission nodes have to go, and
     * whether the single granted node is already present.
     *
     * @param permissionNodes the player's own permission node keys (no inheritance, no meta)
     * @param grant           the node to keep / add ({@code GSit.SitClick})
     */
    static Normalisation plan(List<String> permissionNodes, String grant) {
        List<String> remove = new ArrayList<>();
        boolean granted = false;
        for (String key : permissionNodes) {
            if (key == null) {
                continue;
            }
            if (key.trim().equalsIgnoreCase(grant)) {
                granted = true;
                continue;
            }
            if (isGsitNode(key)) {
                remove.add(key);
            }
        }
        return new Normalisation(remove, granted);
    }

    /** Result of {@link #plan}: nodes to remove, and whether the grant already exists. */
    record Normalisation(List<String> remove, boolean granted) {
        /** True when nothing has to be written at all. */
        boolean isNoop() {
            return remove.isEmpty() && granted;
        }
    }

    // ------------------------------------------------------------------ join handling

    private LuckPerms resolveApi() {
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            // Not registered through the provider yet - fall back to the service manager.
        } catch (NoClassDefFoundError e) {
            return null; // LuckPerms API not on the classpath at all
        }
        RegisteredServiceProvider<LuckPerms> registration =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        return registration == null ? null : registration.getProvider();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) {
            return;
        }
        LuckPerms api = luckPerms != null ? luckPerms : resolveApi();
        if (api == null) {
            if (!reportedMissing) {
                reportedMissing = true;
                plugin.getLogger().warning("[GSit] LuckPerms is not installed - cannot normalise GSit "
                        + "permissions on join. Install LuckPerms or set gsit.enabled: false.");
            }
            return;
        }
        luckPerms = api;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        String name = player.getName();
        if (!inFlight.add(id)) {
            return;
        }
        UserManager users = api.getUserManager();
        User cached = users.getUser(id);
        if (cached != null) {
            // Normal case: LuckPerms loads users during login, so the data is already here and
            // the permission applies before the player can click anything.
            apply(cached, users, id, name);
            return;
        }
        users.loadUser(id).whenComplete((user, error) -> {
            if (error != null || user == null) {
                inFlight.remove(id);
                plugin.getLogger().warning("[GSit] could not load LuckPerms data for " + name + ": "
                        + (error == null ? "no user" : error.getMessage()));
                return;
            }
            // Permission edits are thread safe; nothing here touches the Bukkit API.
            apply(user, users, id, name);
        });
    }

    private void apply(User user, UserManager users, UUID id, String name) {
        String grant = grantNode();
        List<String> keys = new ArrayList<>();
        for (Node node : user.getNodes(NodeType.PERMISSION)) {
            keys.add(node.getKey());
        }
        Normalisation plan = plan(keys, grant);
        if (plan.isNoop()) {
            inFlight.remove(id);
            return; // already exactly the wanted state: no write, no save
        }
        for (Node node : user.getNodes(NodeType.PERMISSION)) {
            if (plan.remove().contains(node.getKey())) {
                user.data().remove(node);
            }
        }
        if (!plan.granted()) {
            user.data().add(Node.builder(grant).build());
        }
        users.saveUser(user).whenComplete((ignored, error) -> {
            inFlight.remove(id);
            if (error != null) {
                plugin.getLogger().warning("[GSit] saving LuckPerms data failed for " + name + ": "
                        + error.getMessage());
                return;
            }
            plugin.getLogger().info("[GSit] " + name + ": removed " + plan.remove() + ", granted " + grant);
        });
    }
}
