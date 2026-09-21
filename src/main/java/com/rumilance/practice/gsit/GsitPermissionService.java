package com.rumilance.practice.gsit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.session.PlayerStateManager;
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
 * <p>This plugin does not implement sitting — the external <strong>GSit</strong> plugin owns
 * that interaction. What this bridge owns is GSit's <em>surface</em>: how much of it a player
 * may use, and when.</p>
 *
 * <p>GSit's nodes normally reach a player through a <strong>group</strong>, so deleting the
 * player's own nodes changes nothing. The shape that works is the one in
 * {@link GsitPolicy}: the player always carries an explicit {@code -gsit.*} deny (which vetoes
 * everything inherited, wildcards included), and in the hub additionally carries
 * {@code GSit.SitClick = true} — LuckPerms resolves the most specific node, so that exact grant
 * beats the wildcard deny and clicking a stair or slab sits you down. Nothing else GSit offers
 * survives: no {@code /sit}, no crawling, no belt.</p>
 *
 * <p>Outside the hub the grant is denied explicitly as well, so GSit is completely off during a
 * match. A reconciler walks the online players once a second and writes only when somebody's
 * data differs from the wanted state, so joins and mode changes cost one LuckPerms write at
 * most — never a per-tick database hit.</p>
 *
 * <p>Without LuckPerms installed the bridge logs once and does nothing.</p>
 */
public final class GsitPermissionService implements Listener {

    /** Fallback when config.yml has no usable value. */
    private static final String DEFAULT_GRANT = "GSit.SitClick";
    /** Reconciler interval: fast enough to catch a match start, slow enough to be free. */
    private static final long RECONCILE_TICKS = 20L;

    private final Plugin plugin;
    private final ConfigService configService;
    /** Players with a write in flight, so the reconciler cannot double-apply. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile LuckPerms luckPerms;
    private volatile PlayerStateManager stateManager;
    private boolean reportedMissing;

    public GsitPermissionService(Plugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    /** Needed to tell the hub from a match; without it everybody is treated as hub. */
    public void setStateManager(PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    // ------------------------------------------------------------------ configuration

    /** Resolves the LuckPerms API. Safe to call when LuckPerms is absent (logs on first join). */
    public void hook() {
        if (!enabled()) {
            return;
        }
        luckPerms = resolveApi();
        if (luckPerms != null) {
            plugin.getLogger().info("[GSit] permission bridge active: -" + GsitPolicy.WILDCARD
                    + " for everyone, +" + grantNode() + " in the hub only");
        }
    }

    /** Starts the once-a-second reconciler (hub = sit by click, everywhere else = off). */
    public void startReconciler() {
        if (!enabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileAll, RECONCILE_TICKS, RECONCILE_TICKS);
    }

    public void shutdown() {
        inFlight.clear();
        luckPerms = null;
    }

    /** True when the bridge is switched on in config.yml ({@code gsit.enabled}). */
    public boolean enabled() {
        return configService != null && configService.config().getBoolean("gsit.enabled", true);
    }

    /** The single GSit node players end up with in the hub. */
    public String grantNode() {
        String node = configService == null ? null : configService.config().getString("gsit.grant");
        return node == null || node.isBlank() ? DEFAULT_GRANT : node.trim();
    }

    /** @deprecated use {@link GsitPolicy#isGsitNode(String)}. */
    static boolean isGsitNode(String key) {
        return GsitPolicy.isGsitNode(key);
    }

    /** Hub states keep the click-to-sit grant; anything else (a match, queue, AFK) does not. */
    private GsitPolicy.Mode modeOf(UUID playerId) {
        PlayerStateManager manager = stateManager;
        if (manager == null) {
            return GsitPolicy.Mode.LOBBY;
        }
        return PracticeGuards.lobbyProtectedStates(manager.getState(playerId))
                ? GsitPolicy.Mode.LOBBY
                : GsitPolicy.Mode.MATCH;
    }

    // ------------------------------------------------------------------ reconciler

    private void reconcileAll() {
        if (!enabled()) {
            return;
        }
        LuckPerms api = luckPerms != null ? luckPerms : resolveApi();
        if (api == null) {
            return;
        }
        luckPerms = api;
        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcile(api, player);
        }
    }

    private void reconcile(LuckPerms api, Player player) {
        UUID id = player.getUniqueId();
        if (!inFlight.add(id)) {
            return; // a write for this player is already running
        }
        User user = api.getUserManager().getUser(id);
        if (user == null) {
            inFlight.remove(id);
            return; // not loaded yet; the join handler loads and applies it
        }
        apply(user, api.getUserManager(), id, player.getName(), modeOf(id));
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
        GsitPolicy.Mode mode = modeOf(id);
        UserManager users = api.getUserManager();
        User cached = users.getUser(id);
        if (cached != null) {
            // Normal case: LuckPerms loads users during login, so the data is already here and
            // the permission applies before the player can click anything.
            apply(cached, users, id, name, mode);
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
            apply(user, users, id, name, mode);
        });
    }

    private void apply(User user, UserManager users, UUID id, String name, GsitPolicy.Mode mode) {
        List<GsitPolicy.Owned> owned = new ArrayList<>();
        for (Node node : user.getNodes(NodeType.PERMISSION)) {
            owned.add(new GsitPolicy.Owned(node.getKey(), node.getValue()));
        }
        GsitPolicy.Plan plan = GsitPolicy.plan(owned, grantNode(), mode);
        if (plan.isNoop()) {
            inFlight.remove(id);
            return; // already exactly the wanted state: no write, no save
        }
        for (GsitPolicy.Owned wanted : plan.remove()) {
            for (Node node : new ArrayList<>(user.getNodes(NodeType.PERMISSION))) {
                if (node.getKey().equalsIgnoreCase(wanted.key()) && node.getValue() == wanted.value()) {
                    user.data().remove(node);
                }
            }
        }
        for (GsitPolicy.Owned wanted : plan.add()) {
            user.data().add(Node.builder(wanted.key()).value(wanted.value()).build());
        }
        users.saveUser(user).whenComplete((ignored, error) -> {
            inFlight.remove(id);
            if (error != null) {
                plugin.getLogger().warning("[GSit] saving LuckPerms data failed for " + name + ": "
                        + error.getMessage());
                return;
            }
            // LuckPerms recalculates on its own; this only makes sure an online player sees the
            // change before their next click.
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (online.isOnline()) {
                        online.recalculatePermissions();
                    }
                });
            }
            plugin.getLogger().info("[GSit] " + name + " (" + mode + "): added " + plan.add()
                    + ", removed " + plan.remove());
        });
    }
}
