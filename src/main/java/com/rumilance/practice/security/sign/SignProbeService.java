package com.rumilance.practice.security.sign;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.rumilance.practice.ban.BanDuration;
import com.rumilance.practice.ban.BanService;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.model.AuditLogEntry;
import com.rumilance.practice.util.AsyncExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Active, DonutSMP-style mod detector built on the sign translation-key vulnerability (MC-265322).
 *
 * <p>The scan is entirely client-side: a fake sign is shown to a single player via packets (no real
 * world block is ever placed or removed), the sign is loaded with mod translation keys, the sign
 * editor is force-opened, and a couple of ticks later the fake block is reverted so the editor
 * auto-closes and the client echoes back the resolved text via a serverbound sign-update packet.
 * A vanilla client renders an unknown translation key as the raw key string; a modded client renders
 * the mod's own text, so any line whose response differs from the key it was sent proves the mod is
 * installed.</p>
 *
 * <p>Everything degrades gracefully: if ProtocolLib is missing, disabled in config, or the packet /
 * NMS shapes cannot be resolved on this server version, the detector simply turns itself off. It
 * never places real blocks and never crashes the server; auto-ban is opt-in to avoid false bans.</p>
 */
public final class SignProbeService {

    /** A single "Display Name -> translation key" mod signature. */
    public record Signature(String name, String key) {
    }

    private static final class PendingProbe {
        final BlockPosition pos;
        final BlockData realBlock;
        final List<Signature> lineSignatures; // index 0..3, may hold nulls for padded lines
        long deadlineMillis;

        PendingProbe(BlockPosition pos, BlockData realBlock, List<Signature> lineSignatures) {
            this.pos = pos;
            this.realBlock = realBlock;
            this.lineSignatures = lineSignatures;
        }
    }

    private final Plugin plugin;
    private final ConfigService configService;
    private final BanService banService;
    private final AuditLogRepository auditLogRepository;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;

    private final Map<UUID, PendingProbe> pending = new ConcurrentHashMap<>();
    private ProtocolManager protocolManager;
    private Object signBlockEntityType;
    private Class<?> blockEntityTypeClass;
    private boolean available;

    public SignProbeService(Plugin plugin, ConfigService configService, BanService banService,
                            AuditLogRepository auditLogRepository, AsyncExecutor asyncExecutor, Logger logger) {
        this.plugin = plugin;
        this.configService = configService;
        this.banService = banService;
        this.auditLogRepository = auditLogRepository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    /** Wire up ProtocolLib. Safe to call even when ProtocolLib is absent. */
    public void init() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            logger.info("[SignProbe] ProtocolLib not found - active mod detector disabled.");
            return;
        }
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            resolveSignBlockEntityType();
            registerResponseListener();
            available = true;
            logger.info("[SignProbe] Active mod detector ready (ProtocolLib hooked).");
        } catch (Throwable t) {
            available = false;
            logger.log(Level.WARNING, "[SignProbe] Failed to initialise; active mod detector disabled.", t);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private boolean configEnabled() {
        return configService.config().getBoolean("sign-guard.active-probe.enabled", false);
    }

    /** Load the configured signatures ("Name|key" strings). */
    public List<Signature> signatures() {
        List<String> raw = configService.config().getStringList("sign-guard.active-probe.signatures");
        List<Signature> out = new ArrayList<>(raw.size());
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            int sep = entry.indexOf('|');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            String name = entry.substring(0, sep).trim();
            String key = entry.substring(sep + 1).trim();
            if (!name.isEmpty() && !key.isEmpty()) {
                out.add(new Signature(name, key));
            }
        }
        return out;
    }

    /**
     * Runs a full scan against {@code target}, reporting results to {@code requester} (may be null
     * for silent/automatic checks). Returns false if the detector is unavailable/disabled.
     */
    public boolean probe(Player target, Player requester) {
        if (!available || !configEnabled() || target == null || !target.isOnline()) {
            return false;
        }
        List<Signature> signatures = signatures();
        if (signatures.isEmpty()) {
            return false;
        }
        int batchDelay = Math.max(4, configService.config().getInt("sign-guard.active-probe.batch-delay-ticks", 12));
        UUID requesterId = requester == null ? null : requester.getUniqueId();
        List<List<Signature>> batches = new ArrayList<>();
        for (int i = 0; i < signatures.size(); i += 4) {
            batches.add(signatures.subList(i, Math.min(i + 4, signatures.size())));
        }
        for (int b = 0; b < batches.size(); b++) {
            List<Signature> batch = batches.get(b);
            Bukkit.getScheduler().runTaskLater(plugin, () -> sendProbe(target, batch), (long) b * batchDelay);
        }
        if (requester != null) {
            requester.sendMessage(Component.text("[SignProbe] " + target.getName() + " を "
                    + signatures.size() + " 個のシグネチャで検査中…", NamedTextColor.GRAY));
        }
        // Remember who to report hits to for this target during the scan window.
        reportTargets.put(target.getUniqueId(), requesterId == null ? NO_REQUESTER : requesterId);
        long window = (long) batches.size() * batchDelay + 40L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> reportTargets.remove(target.getUniqueId()), window);
        return true;
    }

    private static final UUID NO_REQUESTER = new UUID(0L, 0L);
    private final Map<UUID, UUID> reportTargets = new ConcurrentHashMap<>();

    private void sendProbe(Player target, List<Signature> batch) {
        if (!target.isOnline()) {
            return;
        }
        try {
            var loc = target.getLocation();
            // Head-level block: usually air, never disturbs the ground; only sent to this client.
            BlockPosition pos = new BlockPosition(loc.getBlockX(), Math.min(loc.getBlockY() + 1, 318), loc.getBlockZ());
            BlockData realBlock = target.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getBlockData();

            List<Signature> lines = new ArrayList<>(4);
            String[] messages = new String[4];
            for (int i = 0; i < 4; i++) {
                if (i < batch.size()) {
                    lines.add(batch.get(i));
                    messages[i] = translateJson(batch.get(i).key());
                } else {
                    lines.add(null);
                    messages[i] = "{\"text\":\"\"}";
                }
            }

            sendPacket(target, blockChange(pos, WrappedBlockData.createData(Material.OAK_SIGN)));
            sendPacket(target, tileEntityData(pos, messages));
            sendPacket(target, openSignEditor(pos));

            pending.put(target.getUniqueId(), new PendingProbe(pos, realBlock, lines));

            int revert = Math.max(1, configService.config().getInt("sign-guard.active-probe.revert-delay-ticks", 3));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isOnline()) {
                    sendPacket(target, blockChange(pos, WrappedBlockData.createData(realBlock)));
                }
            }, revert);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[SignProbe] Probe send failed for " + target.getName(), t);
        }
    }

    private void registerResponseListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.UPDATE_SIGN) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleResponse(event);
            }
        });
    }

    private void handleResponse(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        PendingProbe probe = pending.get(player.getUniqueId());
        if (probe == null) {
            return;
        }
        BlockPosition pos;
        String[] lines;
        try {
            pos = event.getPacket().getBlockPositionModifier().read(0);
            lines = event.getPacket().getStringArrays().read(0);
        } catch (Throwable t) {
            return;
        }
        if (pos == null || lines == null || !pos.equals(probe.pos)) {
            return; // not our probe sign
        }
        pending.remove(player.getUniqueId());
        // Swallow this client packet so the fake edit never reaches the vanilla sign handling.
        event.setCancelled(true);

        List<String> detected = new ArrayList<>();
        for (int i = 0; i < lines.length && i < probe.lineSignatures.size(); i++) {
            Signature sig = probe.lineSignatures.get(i);
            if (sig == null) {
                continue;
            }
            String returned = lines[i] == null ? "" : lines[i].trim();
            if (returned.isEmpty()) {
                continue;
            }
            // Vanilla echoes the raw key; a mod echoes its translated text.
            if (!returned.equalsIgnoreCase(sig.key())) {
                detected.add(sig.name());
            }
        }
        if (!detected.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> onDetected(player, detected));
        }
    }

    /** Drop all scan state for a player (call on quit). Without this a disconnect during the
     *  short scan window leaks a {@link PendingProbe} that never gets a response and is never
     *  reverted, and lingers in the map keyed by that player. */
    public void abandon(UUID playerId) {
        if (playerId == null) {
            return;
        }
        pending.remove(playerId);
        reportTargets.remove(playerId);
    }

    private void onDetected(Player player, List<String> mods) {
        String joined = String.join(", ", mods);
        auditAsync(player, "SIGN_PROBE_HIT", "mods=" + joined);

        UUID requesterId = reportTargets.get(player.getUniqueId());
        if (requesterId != null && !requesterId.equals(NO_REQUESTER)) {
            Player requester = Bukkit.getPlayer(requesterId);
            if (requester != null) {
                requester.sendMessage(Component.text("[SignProbe] " + player.getName()
                        + " から検知: " + joined, NamedTextColor.RED));
            }
        }
        Bukkit.getConsoleSender().sendMessage(Component.text("[SignProbe] " + player.getName()
                + " detected mods: " + joined, NamedTextColor.RED));

        if (configService.config().getBoolean("sign-guard.active-probe.auto-ban", false)) {
            autoBan(player, joined);
        }
    }

    private void autoBan(Player player, String mods) {
        String token = configService.config().getString("sign-guard.active-probe.auto-ban-duration", "auto");
        Duration duration;
        String durationToken;
        if (token == null || token.equalsIgnoreCase("auto")) {
            int offense = banService.banCount(player.getUniqueId()) + 1;
            duration = BanDuration.forOffenseNumber(offense);
            durationToken = BanDuration.autoToken(offense);
        } else {
            duration = BanDuration.parse(token).orElse(null);
            durationToken = token;
        }
        banService.ban(player.getUniqueId(), player.getName(), "Disallowed client mod (" + mods + ")",
                duration, durationToken == null ? "auto" : durationToken, "SignProbe");
    }

    private void auditAsync(Player player, String action, String details) {
        asyncExecutor.execute(() -> {
            try {
                auditLogRepository.insert(AuditLogEntry.of(player.getUniqueId(), action, details));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to log sign probe event", e);
            }
        });
    }

    // -------------------------------------------------------------------------------------------
    // Packet builders

    private PacketContainer blockChange(BlockPosition pos, WrappedBlockData data) {
        PacketContainer p = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
        p.getBlockPositionModifier().write(0, pos);
        p.getBlockData().write(0, data);
        return p;
    }

    private PacketContainer openSignEditor(BlockPosition pos) {
        PacketContainer p = protocolManager.createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
        p.getBlockPositionModifier().write(0, pos);
        // 1.20+: boolean selecting the front side of the sign.
        if (!p.getBooleans().getFields().isEmpty()) {
            p.getBooleans().write(0, true);
        }
        return p;
    }

    private PacketContainer tileEntityData(BlockPosition pos, String[] messages) {
        PacketContainer p = protocolManager.createPacket(PacketType.Play.Server.TILE_ENTITY_DATA);
        p.getBlockPositionModifier().write(0, pos);
        if (signBlockEntityType != null && blockEntityTypeClass != null) {
            p.getModifier().withType(blockEntityTypeClass).write(0, signBlockEntityType);
        }
        NbtCompound nbt = buildSignNbt(pos, messages);
        p.getNbtModifier().write(0, nbt);
        return p;
    }

    @SuppressWarnings("unchecked")
    private NbtCompound buildSignNbt(BlockPosition pos, String[] messages) {
        NbtCompound root = NbtFactory.ofCompound("");
        root.put("id", "minecraft:sign");
        root.put("x", pos.getX());
        root.put("y", pos.getY());
        root.put("z", pos.getZ());
        root.put("is_waxed", (byte) 0);
        root.put(textSide("front_text", messages));
        root.put(textSide("back_text", new String[]{
                "{\"text\":\"\"}", "{\"text\":\"\"}", "{\"text\":\"\"}", "{\"text\":\"\"}"}));
        return root;
    }

    private NbtCompound textSide(String name, String[] messages) {
        NbtCompound side = NbtFactory.ofCompound(name);
        side.put("has_glowing_text", (byte) 0);
        side.put("color", "black");
        side.put(NbtFactory.ofList("messages", messages[0], messages[1], messages[2], messages[3]));
        return side;
    }

    private static String translateJson(String key) {
        return "{\"translate\":\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private void sendPacket(Player player, PacketContainer packet) {
        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Throwable t) {
            logger.log(Level.FINE, "[SignProbe] sendServerPacket failed", t);
        }
    }

    private void resolveSignBlockEntityType() {
        try {
            blockEntityTypeClass = MinecraftReflection.getMinecraftClass(
                    "world.level.block.entity.BlockEntityType",
                    "world.level.block.entity.TileEntityTypes");
        } catch (Throwable t) {
            blockEntityTypeClass = null;
            return;
        }
        // Paper ships Mojang mappings at runtime, so the static field is literally "SIGN".
        for (Field field : blockEntityTypeClass.getDeclaredFields()) {
            if (!blockEntityTypeClass.isAssignableFrom(field.getType())) {
                continue;
            }
            if (field.getName().equalsIgnoreCase("SIGN")) {
                try {
                    field.setAccessible(true);
                    signBlockEntityType = field.get(null);
                    return;
                } catch (Throwable ignored) {
                    // fall through
                }
            }
        }
        logger.warning("[SignProbe] Could not resolve the SIGN block-entity type; probes may be ignored by clients.");
    }
}
