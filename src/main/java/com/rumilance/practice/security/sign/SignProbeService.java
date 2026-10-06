package com.rumilance.practice.security.sign;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
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
 * <p>Everything degrades gracefully: if PacketEvents is missing, disabled in config, or the packet /
 * NMS shapes cannot be resolved on this server version, the detector simply turns itself off. It
 * never places real blocks and never crashes the server; auto-ban is opt-in to avoid false bans.</p>
 */
public final class SignProbeService implements PacketListener {

    /** A single "Display Name -> translation key" mod signature. */
    public record Signature(String name, String key) {
    }

    private static final class PendingProbe {
        final Vector3i pos;
        final BlockData realBlock;
        final List<Signature> lineSignatures; // index 0..3, may hold nulls for padded lines
        long deadlineMillis;

        PendingProbe(Vector3i pos, BlockData realBlock, List<Signature> lineSignatures) {
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

    /** Wire up PacketEvents. Safe to call even when PacketEvents is absent. */
    public void init() {
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            logger.info("[SignProbe] PacketEvents not found - active mod detector disabled.");
            return;
        }
        try {
            PacketEvents.getAPI().getEventManager()
                    .registerListener(this, PacketListenerPriority.NORMAL);
            available = true;
            logger.info("[SignProbe] Active mod detector ready (PacketEvents hooked).");
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
            Vector3i pos = new Vector3i(loc.getBlockX(), Math.min(loc.getBlockY() + 1, 318), loc.getBlockZ());
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

            sendPacket(target, blockChange(pos, Material.OAK_SIGN.createBlockData()));
            sendPacket(target, tileEntityData(pos, messages));
            sendPacket(target, openSignEditor(pos));

            pending.put(target.getUniqueId(), new PendingProbe(pos, realBlock, lines));

            int revert = Math.max(1, configService.config().getInt("sign-guard.active-probe.revert-delay-ticks", 3));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isOnline()) {
                    sendPacket(target, blockChange(pos, realBlock));
                }
            }, revert);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[SignProbe] Probe send failed for " + target.getName(), t);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.UPDATE_SIGN) {
            return;
        }
        Object sender = event.getPlayer();
        if (!(sender instanceof Player player)) {
            return;
        }
        PendingProbe probe = pending.get(player.getUniqueId());
        if (probe == null) {
            return;
        }
        Vector3i pos;
        String[] lines;
        try {
            WrapperPlayClientUpdateSign update = new WrapperPlayClientUpdateSign(event);
            pos = update.getBlockPosition();
            lines = update.getTextLines();
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

    private static WrapperPlayServerBlockChange blockChange(Vector3i pos, BlockData data) {
        return new WrapperPlayServerBlockChange(pos, SpigotConversionUtil.fromBukkitBlockData(data));
    }

    private static WrapperPlayServerOpenSignEditor openSignEditor(Vector3i pos) {
        // Second arg is the 1.20+ flag selecting the front side of the sign.
        return new WrapperPlayServerOpenSignEditor(pos, true);
    }

    private static WrapperPlayServerBlockEntityData tileEntityData(Vector3i pos, String[] messages) {
        return new WrapperPlayServerBlockEntityData(pos, BlockEntityTypes.SIGN, buildSignNbt(pos, messages));
    }

    private static NBTCompound buildSignNbt(Vector3i pos, String[] messages) {
        NBTCompound root = new NBTCompound();
        root.setTag("id", new NBTString("minecraft:sign"));
        root.setTag("x", new NBTInt(pos.getX()));
        root.setTag("y", new NBTInt(pos.getY()));
        root.setTag("z", new NBTInt(pos.getZ()));
        root.setTag("is_waxed", new NBTByte((byte) 0));
        root.setTag("front_text", textSide(messages));
        root.setTag("back_text", textSide(new String[]{
                "{\"text\":\"\"}", "{\"text\":\"\"}", "{\"text\":\"\"}", "{\"text\":\"\"}"}));
        return root;
    }

    private static NBTCompound textSide(String[] messages) {
        NBTCompound side = new NBTCompound();
        side.setTag("has_glowing_text", new NBTByte((byte) 0));
        side.setTag("color", new NBTString("black"));
        side.setTag("messages", new NBTList<>(NBTType.STRING, java.util.List.of(
                new NBTString(messages[0]), new NBTString(messages[1]),
                new NBTString(messages[2]), new NBTString(messages[3]))));
        return side;
    }

    private static String translateJson(String key) {
        return "{\"translate\":\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private void sendPacket(Player player, com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable t) {
            logger.log(Level.FINE, "[SignProbe] sendPacket failed", t);
        }
    }

}
