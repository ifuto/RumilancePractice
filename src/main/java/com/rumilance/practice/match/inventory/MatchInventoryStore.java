package com.rumilance.practice.match.inventory;

import com.rumilance.practice.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    private static final int FORMAT_MAGIC = 0x4D493032; // "MI02"

    /** One fighter's end inventory (at death or match end). */
    public record Fighter(UUID playerId, String name, byte[] inventory) {
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
        this.folder = plugin.getDataFolder().toPath().resolve("match-inv");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not create match-inv folder", e);
        }
    }

    /**
     * Snapshot a participant's inventory once (e.g. at mid-fight death). Later calls for the
     * same match/player are ignored so death kits are not overwritten by empty/spectator state.
     */
    public void captureIfAbsent(UUID matchId, UUID playerId, String name, byte[] inventory) {
        if (matchId == null || playerId == null || inventory == null) {
            return;
        }
        String safeName = name == null ? "" : name;
        pending.computeIfAbsent(matchId, id -> new ConcurrentHashMap<>())
                .putIfAbsent(playerId, new Fighter(playerId, safeName, inventory));
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
                out.writeInt(2);
                out.writeLong(snapshot.endedAtEpochMs());
                writeUuid(out, snapshot.matchId());
                out.writeInt(snapshot.fighters().size());
                for (Fighter f : snapshot.fighters()) {
                    writeUuid(out, f.playerId());
                    writeString(out, f.name());
                    writeBytes(out, f.inventory());
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
                if (maybeMagic == FORMAT_MAGIC) {
                    int version = in.readInt();
                    if (version != 2) {
                        return Optional.empty();
                    }
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
                        byte[] inv = readBytes(in);
                        fighters.add(new Fighter(pid, name, inv));
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
