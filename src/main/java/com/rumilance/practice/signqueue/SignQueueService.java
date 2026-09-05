package com.rumilance.practice.signqueue;

import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.OriginalKitSnapshot;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.team.OriginalKitRef;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unranked 1v1 queue joined from placed queue signs.
 *
 * <p>OPs take queue-sign items from the admin GUI (one per kit — the kit is FIXED on the
 * sign). Placing one binds the block via its tile PDC and writes the sign layout:</p>
 *
 * <pre>
 *   Unranked Queue
 *   {kit}
 *
 *   ▶ {waiting} / 2
 * </pre>
 *
 * <p>Left- or right-clicking the sign joins the queue (clicking again leaves). The queue
 * holds at most two players; when the second joins, the duel starts immediately with the
 * sign's kit. Custom kits: every waiter brings their latest saved ORIGINAL kit, and the
 * match loadout for BOTH fighters is the FIRST waiter's original kit (rules stay with the
 * sign's base kit) — the party-battle original-kit mechanic.</p>
 */
public final class SignQueueService implements Listener {

    private static final int CAPACITY = 2;

    private final Plugin plugin;
    private final KitService kitService;
    private final QueueService queueService;
    private final MatchService matchService;
    private final PlayerStateManager stateManager;
    private final LobbyService lobbyService;
    private final SoundService soundService;
    private final MessageService messageService;
    private final OriginalKitService originalKitService;
    private final RuntimeFlags runtimeFlags;
    private volatile FfaService ffaService;
    private volatile TeamService teamService;

    /** Per-kit waiter queue (kit id lowercased). All access happens on the main thread. */
    private final Map<String, Deque<Waiter>> queues = new ConcurrentHashMap<>();
    /** Locations of placed queue signs (self-healing: rebuilt by the periodic chunk scan). */
    private final Set<Location> boundSigns = ConcurrentHashMap.newKeySet();
    private BukkitTask tickTask;
    private BukkitTask scanTask;

    private record Waiter(UUID id, String kitId, int originalSlot, Instant joinedAt) {
    }

    public SignQueueService(Plugin plugin, KitService kitService, QueueService queueService,
                            MatchService matchService, PlayerStateManager stateManager,
                            LobbyService lobbyService, SoundService soundService,
                            MessageService messageService, OriginalKitService originalKitService,
                            RuntimeFlags runtimeFlags) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.kitService = Objects.requireNonNull(kitService, "kitService");
        this.queueService = Objects.requireNonNull(queueService, "queueService");
        this.matchService = Objects.requireNonNull(matchService, "matchService");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.soundService = Objects.requireNonNull(soundService, "soundService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.originalKitService = Objects.requireNonNull(originalKitService, "originalKitService");
        this.runtimeFlags = Objects.requireNonNull(runtimeFlags, "runtimeFlags");
    }

    public void setFfaService(FfaService ffaService) {
        this.ffaService = ffaService;
    }

    public void setTeamService(TeamService teamService) {
        this.teamService = teamService;
    }

    public void start() {
        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
        if (scanTask == null) {
            // Rebuild the bound-sign registry after restarts and heal stale sign lines.
            scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanLoadedChunks,
                    200L, 600L);
        }
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        queues.clear();
    }

    // ------------------------------------------------------------------ sign item / placement

    /** The admin-GUI queue-sign item for {@code kitId} (unlimited supply). */
    public ItemStack createSignItem(Player viewer, String kitId) {
        ItemStack item = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.translatable("block.minecraft.oak_sign")
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        String kitLabel = kitService.get(kitId)
                .map(KitDefinition::prettyDisplayName).orElse(kitId);
        meta.lore(java.util.List.of(
                Component.text(kitLabel, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text(messageService.raw(viewer, "gui.sign-kit-item-hint"),
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ItemKeys.queueSignKit(), PersistentDataType.STRING,
                kitId.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String kitId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.queueSignKit(), PersistentDataType.STRING);
        if (kitId == null || kitId.isBlank()) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        if (!isSignBlock(block)) {
            return;
        }
        sign.getPersistentDataContainer().set(ItemKeys.queueSignKit(), PersistentDataType.STRING,
                kitId.toLowerCase(Locale.ROOT));
        writeLines(sign, kitId);
        sign.update(true, false);
        boundSigns.add(block.getLocation());
    }

    /** Queue signs drop nothing — they are created from the admin GUI only. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isSignBlock(block)) {
            return;
        }
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        String kitId = sign.getPersistentDataContainer()
                .get(ItemKeys.queueSignKit(), PersistentDataType.STRING);
        if (kitId == null) {
            return;
        }
        event.setDropItems(false);
        boundSigns.remove(block.getLocation());
    }

    // ------------------------------------------------------------------ click to join / leave

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND
                && action == Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !isSignBlock(block)) {
            return;
        }
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        String kitId = sign.getPersistentDataContainer()
                .get(ItemKeys.queueSignKit(), PersistentDataType.STRING);
        if (kitId == null || kitId.isBlank()) {
            return;
        }
        // Stop the sign editor (and any held-item interaction) from opening too.
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        boundSigns.add(block.getLocation());
        handleClick(event.getPlayer(), kitId);
    }

    public boolean isQueued(UUID playerId) {
        for (Deque<Waiter> waiters : queues.values()) {
            for (Waiter waiter : waiters) {
                if (waiter.id().equals(playerId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleClick(Player player, String kitId) {
        if (isQueued(player.getUniqueId())) {
            leave(player);
            return;
        }
        join(player, kitId);
    }

    private void join(Player player, String kitId) {
        String normalized = kitId.toLowerCase(Locale.ROOT);
        if (teamService != null && teamService.teamOf(player.getUniqueId()).isPresent()) {
            messageService.send(player, "party.solo-only");
            return;
        }
        if (runtimeFlags.maintenance() && !player.hasPermission("rumilance.admin")) {
            messageService.send(player, "queue.maintenance");
            return;
        }
        if (!kitService.isQueueEnabled(normalized)
                || kitService.get(normalized).filter(KitDefinition::enabled).isEmpty()) {
            messageService.send(player, "queue.kit-disabled");
            return;
        }
        FfaService ffa = ffaService;
        if (ffa != null && ffa.isInFfa(player.getUniqueId())) {
            messageService.send(player, "queue.cannot-join");
            return;
        }
        // Switching over from the regular queue is allowed; drop the old entry first.
        if (queueService.isQueued(player.getUniqueId())) {
            queueService.leave(player.getUniqueId());
            stateManager.resetToLobby(player.getUniqueId());
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            messageService.send(player, "queue.cannot-join");
            return;
        }
        Deque<Waiter> waiters = queues.computeIfAbsent(normalized, k -> new ArrayDeque<>());
        if (waiters.size() >= CAPACITY) {
            messageService.send(player, "queue.sign-full");
            return;
        }
        // Custom kits: bring your latest saved original kit into the queue.
        int originalSlot = originalKitService.latestSaved(player.getUniqueId())
                .map(OriginalKitSnapshot::slot).orElse(-1);
        waiters.addLast(new Waiter(player.getUniqueId(), normalized, originalSlot, Instant.now()));
        try {
            stateManager.transition(player.getUniqueId(), PlayerState.QUEUED_UNRANKED);
        } catch (Exception e) {
            waiters.removeIf(w -> w.id().equals(player.getUniqueId()));
            messageService.send(player, "queue.cannot-join");
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        giveLeaveItem(player);
        soundService.play(player, "queue-joined");
        messageService.send(player, "queue.joined", MessageService.tags(
                "mode", messageService.modeWord(player, false), "kit", normalized));
        refreshSigns(normalized);
        if (waiters.size() >= CAPACITY) {
            startMatch(normalized);
        }
    }

    /** Public leave used by the leave item / quit hooks. */
    public void leave(Player player) {
        Waiter removed = removeWaiter(player.getUniqueId());
        if (removed == null) {
            return;
        }
        stateManager.resetToLobby(player.getUniqueId());
        lobbyService.applyLobbyInventory(player);
        soundService.play(player, "queue-leave");
        messageService.send(player, "queue.left");
        refreshSigns(removed.kitId());
    }

    /**
     * Pulls the player out of the sign queue WITHOUT lobby messages — used when they join
     * the regular queue or another activity instead.
     */
    public boolean leaveIfQueued(Player player) {
        Waiter removed = removeWaiter(player.getUniqueId());
        if (removed == null) {
            return false;
        }
        stateManager.resetToLobby(player.getUniqueId());
        lobbyService.applyLobbyInventory(player);
        refreshSigns(removed.kitId());
        return true;
    }

    private Waiter removeWaiter(UUID playerId) {
        for (Deque<Waiter> waiters : queues.values()) {
            java.util.Iterator<Waiter> it = waiters.iterator();
            while (it.hasNext()) {
                Waiter waiter = it.next();
                if (waiter.id().equals(playerId)) {
                    it.remove();
                    return waiter;
                }
            }
        }
        return null;
    }

    private void startMatch(String kitId) {
        Deque<Waiter> waiters = queues.get(kitId);
        if (waiters == null) {
            return;
        }
        Waiter first = waiters.pollFirst();
        Waiter second = waiters.pollFirst();
        if (first == null || second == null) {
            return;
        }
        refreshSigns(kitId);
        // Fight with the first waiter's kit: their original-kit layout becomes BOTH
        // fighters' loadout; rules stay with the sign's base kit.
        OriginalKitRef ref = first.originalSlot() >= 0
                ? new OriginalKitRef(first.id(), first.originalSlot())
                : null;
        matchService.startDuel(first.id(), second.id(), kitId, MatchMode.UNRANKED, 1,
                Map.of(), null, null, ref);
    }

    // ------------------------------------------------------------------ signs

    private boolean isSignBlock(Block block) {
        return Tag.SIGNS.isTagged(block.getType()) || Tag.WALL_SIGNS.isTagged(block.getType());
    }

    private void writeLines(Sign sign, String kitId) {
        String kitLabel = kitService.get(kitId)
                .map(KitDefinition::prettyDisplayName).orElse(kitId);
        int count = waitingCount(kitId);
        sign.getSide(Side.FRONT).line(0, Component.text("Unranked Queue",
                NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        sign.getSide(Side.FRONT).line(1, Component.text(kitLabel, NamedTextColor.AQUA));
        sign.getSide(Side.FRONT).line(2, Component.empty());
        sign.getSide(Side.FRONT).line(3, Component.text(
                "\u25B6 " + count + " / " + CAPACITY,
                count >= CAPACITY ? NamedTextColor.RED : NamedTextColor.GREEN));
    }

    /** Updates every known sign bound to {@code kitId}. */
    public void refreshSigns(String kitId) {
        String normalized = kitId.toLowerCase(Locale.ROOT);
        for (Location location : Set.copyOf(boundSigns)) {
            World world = location.getWorld();
            if (world == null) {
                boundSigns.remove(location);
                continue;
            }
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            Block block = location.getBlock();
            if (!(block.getState() instanceof Sign sign)) {
                boundSigns.remove(location);
                continue;
            }
            String boundKit = sign.getPersistentDataContainer()
                    .get(ItemKeys.queueSignKit(), PersistentDataType.STRING);
            if (boundKit == null) {
                boundSigns.remove(location);
                continue;
            }
            if (!boundKit.equalsIgnoreCase(normalized)) {
                continue;
            }
            writeLines(sign, boundKit);
            sign.update();
        }
    }

    /** Rebuilds {@link #boundSigns} from loaded chunks (restart self-heal). */
    private void scanLoadedChunks() {
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (org.bukkit.block.BlockState tile : chunk.getTileEntities()) {
                        if (!(tile instanceof Sign sign)) {
                            continue;
                        }
                        String kitId = sign.getPersistentDataContainer()
                                .get(ItemKeys.queueSignKit(), PersistentDataType.STRING);
                        if (kitId == null) {
                            continue;
                        }
                        boundSigns.add(tile.getLocation());
                        writeLines(sign, kitId);
                        sign.update();
                    }
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Queue-sign scan failed: " + e);
        }
    }

    public int waitingCount(String kitId) {
        Deque<Waiter> waiters = queues.get(kitId.toLowerCase(Locale.ROOT));
        return waiters == null ? 0 : waiters.size();
    }

    // ------------------------------------------------------------------ periodic upkeep

    private void tick() {
        Instant now = Instant.now();
        for (Map.Entry<String, Deque<Waiter>> entry : queues.entrySet()) {
            Deque<Waiter> waiters = entry.getValue();
            boolean changed = waiters.removeIf(w -> Bukkit.getPlayer(w.id()) == null);
            if (changed) {
                refreshSigns(entry.getKey());
            }
            for (Waiter waiter : waiters) {
                Player player = Bukkit.getPlayer(waiter.id());
                if (player == null) {
                    continue;
                }
                long waited = now.getEpochSecond() - waiter.joinedAt().getEpochSecond();
                player.sendActionBar(Component.text("Queue " + entry.getKey() + " | "
                        + waited + "s | " + waiters.size() + " / " + CAPACITY, NamedTextColor.AQUA));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Waiter removed = removeWaiter(event.getPlayer().getUniqueId());
        if (removed != null) {
            refreshSigns(removed.kitId());
        }
    }

    private void giveLeaveItem(Player player) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Leave Queue", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ItemKeys.leaveQueue(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().setItem(4, item);
    }
}
