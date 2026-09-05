package com.rumilance.practice.match.inventory;

import com.rumilance.practice.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;
import com.rumilance.practice.PluginIdentity;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Ring buffer of post-match inventories. Keeps the newest {@link #MAX_MATCHES} matches in
 * memory and on disk under {@code plugins/.../match-inv/}. Supports N participants per match
 * (team fights): capture at death with {@link #captureIfAbsent}, then
 * {@link #finalizeMatch} at end for anyone still missing.
 */
public final class MatchInventoryStore {

    public static final int MAX_MATCHES = 10_000;
    public static final long TTL_MS = 12L * 60L * 60L * 1000L;
    private static final int FORMAT_MAGIC_V2 = 0x4D493032; // "MI02"
    private static final int FORMAT_MAGIC_V3 = 0x4D493033; // "MI03" gzipped inventories
    private static final int FORMAT_MAGIC = 0x4D493034; // "MI04" + team colour per fighter

    /** One fighter's end inventory (at death or match end) with their team colour, if any. */
    public record Fighter(UUID playerId, String name, byte[] inventory, String teamColor) {
        public Fighter(UUID playerId, String name, byte[] inventory) {
            this(playerId, name, inventory, null);
        }

        public ItemStack[] contents() {
            return ItemSerializer.deserialize(inventory);
        }
    }

    public record Snapshot(UUID matchId, List<Fighter> fighters, long endedAtEpochMs) {
        public Snapshot {
            fighters = List.copyOf(fighters == null ? List.of() : fighters);
        }

        public Optional<ItemStack[]> contentsOf(UUID playerId) {
            if (playerId == null) {
                return Optional.empty();
            }
            for (Fighter f : fighters) {
                if (playerId.equals(f.playerId())) {
                    return Optional.of(f.contents());
                }
            }
            return Optional.empty();
        }

        public Optional<Fighter> fighter(UUID playerId) {
            if (playerId == null) {
                return Optional.empty();
            }
            for (Fighter f : fighters) {
                if (playerId.equals(f.playerId())) {
                    return Optional.of(f);
                }
            }
            return Optional.empty();
        }

        /** First fighter (GUI default / legacy A). */
        public Optional<Fighter> first() {
            return fighters.isEmpty() ? Optional.empty() : Optional.of(fighters.get(0));
        }

        public UUID playerA() {
            return fighters.isEmpty() ? null : fighters.get(0).playerId();
        }

        public UUID playerB() {
            return fighters.size() < 2 ? null : fighters.get(1).playerId();
        }

        public String nameA() {
            return fighters.isEmpty() ? "" : fighters.get(0).name();
        }

        public String nameB() {
            return fighters.size() < 2 ? "" : fighters.get(1).name();
        }

        public ItemStack[] contentsA() {
            return fighters.isEmpty() ? new ItemStack[0] : fighters.get(0).contents();
        }

        public ItemStack[] contentsB() {
            return fighters.size() < 2 ? new ItemStack[0] : fighters.get(1).contents();
        }
    }

    private final Plugin plugin;
    private final Path folder;
    private final Map<UUID, Snapshot> byMatch = new ConcurrentHashMap<>();
    private final Deque<UUID> order = new ArrayDeque<>();
    /** In-progress captures keyed by match then player (death / end). */
    private final Map<UUID, Map<UUID, Fighter>> pending = new ConcurrentHashMap<>();

    public MatchInventoryStore(Plugin plugin) {
        this.plugin = plugin;
        this.folder = PluginIdentity.dataFolder(plugin).toPath().resolve("match-inv");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not create match-inv folder", e);
        }
        purgeExpired();
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::purgeExpired,
                20L * 60L * 15L, 20L * 60L * 15L);
    }

    public synchronized void purgeExpired() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        for (UUID id : new ArrayList<>(byMatch.keySet())) {
            Snapshot snap = byMatch.get(id);
            if (snap != null && snap.endedAtEpochMs() > 0 && snap.endedAtEpochMs() < cutoff) {
                byMatch.remove(id);
                order.remove(id);
                pending.remove(id);
                try {
                    Files.deleteIfExists(folder.resolve(id + ".bin"));
                } catch (IOException ignored) {
                }
            }
        }
        try (var stream = Files.newDirectoryStream(folder, "*.bin")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".bin")) {
                    continue;
                }
                UUID id;
                try {
                    id = UUID.fromString(name.substring(0, name.length() - 4));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (byMatch.containsKey(id)) {
                    continue;
                }
                long ended = peekEndedAt(path);
                if (ended > 0 && ended < cutoff) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static long peekEndedAt(Path file) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int maybeMagic = in.readInt();
            if (maybeMagic == FORMAT_MAGIC || maybeMagic == FORMAT_MAGIC_V3 || maybeMagic == FORMAT_MAGIC_V2) {
                int version = in.readInt();
                if (version < 2 || version > 4) {
                    return 0L;
                }
                return in.readLong();
            }
            long high = maybeMagic & 0xFFFFFFFFL;
            long low = in.readInt() & 0xFFFFFFFFL;
            return (high << 32) | low;
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Snapshot a participant's inventory once (e.g. at mid-fight death). Later calls for the
     * same match/player are ignored so death kits are not overwritten by empty/spectator state.
     */
    public void captureIfAbsent(UUID matchId, UUID playerId, String name, byte[] inventory) {
        captureIfAbsent(matchId, playerId, name, inventory, null);
    }

    /** Variant that also stores the fighter's team colour (for party-fight previews). */
    public void captureIfAbsent(UUID matchId, UUID playerId, String name, byte[] inventory, String teamColor) {
        if (matchId == null || playerId == null || inventory == null) {
            return;
        }
        String safeName = name == null ? "" : name;
        pending.computeIfAbsent(matchId, id -> new ConcurrentHashMap<>())
                .putIfAbsent(playerId, new Fighter(playerId, safeName, inventory, teamColor));
    }

    public boolean hasCapture(UUID matchId, UUID playerId) {
        if (matchId == null || playerId == null) {
            return false;
        }
        Map<UUID, Fighter> map = pending.get(matchId);
        return map != null && map.containsKey(playerId);
    }

    /**
     * Promotes pending captures for {@code matchId} into the ring buffer and persists them.
     * Call once at match end after capturing any still-alive fighters.
     */
    public synchronized void finalizeMatch(UUID matchId, long endedAtEpochMs) {
        if (matchId == null) {
            return;
        }
        Map<UUID, Fighter> map = pending.remove(matchId);
        if (map == null || map.isEmpty()) {
            return;
        }
        List<Fighter> fighters = new ArrayList<>(map.values());
        put(new Snapshot(matchId, fighters, endedAtEpochMs));
    }

    public synchronized void put(Snapshot snapshot) {
        if (snapshot == null || snapshot.matchId() == null || snapshot.fighters().isEmpty()) {
            return;
        }
        pending.remove(snapshot.matchId());
        if (byMatch.put(snapshot.matchId(), snapshot) == null) {
            order.addLast(snapshot.matchId());
        }
        while (order.size() > MAX_MATCHES) {
            UUID old = order.pollFirst();
            if (old != null) {
                byMatch.remove(old);
                pending.remove(old);
                try {
                    Files.deleteIfExists(folder.resolve(old + ".bin"));
                } catch (IOException ignored) {
                }
            }
        }
        writeAsync(snapshot);
    }

    public Optional<Snapshot> get(UUID matchId) {
        if (matchId == null) {
            return Optional.empty();
        }
        Snapshot cached = byMatch.get(matchId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return read(matchId);
    }

    private void writeAsync(Snapshot snapshot) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Path file = folder.resolve(snapshot.matchId() + ".bin");
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                out.writeInt(FORMAT_MAGIC);
                out.writeInt(4);
                out.writeLong(snapshot.endedAtEpochMs());
                writeUuid(out, snapshot.matchId());
                out.writeInt(snapshot.fighters().size());
                for (Fighter f : snapshot.fighters()) {
                    writeUuid(out, f.playerId());
                    writeString(out, f.name());
                    writeString(out, f.teamColor() == null ? "" : f.teamColor());
                    writeBytes(out, gzip(f.inventory()));
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.FINE, "Failed to persist match inventory " + snapshot.matchId(), e);
            }
        });
    }

    private Optional<Snapshot> read(UUID matchId) {
        Path file = folder.resolve(matchId + ".bin");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            byte[] raw = Files.readAllBytes(file);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
                int maybeMagic = in.readInt();
                Snapshot snap;
                if (maybeMagic == FORMAT_MAGIC || maybeMagic == FORMAT_MAGIC_V3
                        || maybeMagic == FORMAT_MAGIC_V2) {
                    int version = in.readInt();
                    if (version < 2 || version > 4) {
                        return Optional.empty();
                    }
                    boolean gzipped = version >= 3;
                    boolean hasColor = version >= 4;
                    long ended = in.readLong();
                    UUID mid = readUuid(in);
                    int count = in.readInt();
                    if (count < 0 || count > 64) {
                        throw new IOException("bad fighter count");
                    }
                    List<Fighter> fighters = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        UUID pid = readUuid(in);
                        String name = readString(in);
                        String teamColor = null;
                        if (hasColor) {
                            String raw = readString(in);
                            teamColor = raw.isEmpty() ? null : raw;
                        }
                        byte[] inv = readBytes(in);
                        if (gzipped) {
                            inv = gunzip(inv);
                        }
                        fighters.add(new Fighter(pid, name, inv, teamColor));
                    }
                    snap = new Snapshot(mid, fighters, ended);
                } else {
                    // Legacy v1: long endedAt, then A/B pair (first 4 bytes already consumed).
                    long high = maybeMagic & 0xFFFFFFFFL;
                    long low = in.readInt() & 0xFFFFFFFFL;
                    long ended = (high << 32) | low;
                    UUID mid = readUuid(in);
                    UUID a = readUuid(in);
                    UUID b = readUuid(in);
                    String nameA = readString(in);
                    String nameB = readString(in);
                    byte[] invA = readBytes(in);
                    byte[] invB = readBytes(in);
                    List<Fighter> fighters = new ArrayList<>(2);
                    fighters.add(new Fighter(a, nameA, invA));
                    fighters.add(new Fighter(b, nameB, invB));
                    snap = new Snapshot(mid, fighters, ended);
                }
                byMatch.put(snap.matchId(), snap);
                return Optional.of(snap);
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(baos)) {
            out.write(raw == null ? new byte[0] : raw);
        }
        return baos.toByteArray();
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return new byte[0];
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 256) {
            throw new IOException("bad string length");
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream out, byte[] data) throws IOException {
        byte[] payload = data == null ? new byte[0] : data;
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 512_000) {
            throw new IOException("bad inventory length");
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }
}
