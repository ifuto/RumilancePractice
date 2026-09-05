package com.rumilance.practice.match.history;

import com.rumilance.practice.PluginIdentity;
import org.bukkit.plugin.Plugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Ring buffer of recent finished matches per participant, persisted to a single file.
 * Powers the Battle Menu "match history" entry: for the last {@link #MAX_PER_PLAYER}
 * matches each player can review the outcome, scoreline and (via
 * {@code MatchInventoryStore}) the end-of-match inventories.
 *
 * <p>Entries live at most {@link #TTL_MS}; the per-player cap keeps the index small so the
 * whole file is cheaply rewritten after every match (asynchronously).
 */
public final class MatchHistoryStore {

    /** "直近数十試合" — keep the last sixty matches per player. */
    public static final int MAX_PER_PLAYER = 60;
    /** History outlives the 12h inventory store; inventories degrade gracefully after that. */
    public static final long TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int FORMAT_MAGIC = 0x4D483031; // "MH01"

    /** One participant's result line for a finished match. */
    public record Participant(UUID id, String name, String teamColor, int kills, boolean winner) {
        public Participant {
            name = name == null ? "" : name;
        }
    }

    /** A finished match as shown in the history menu. */
    public record Entry(UUID matchId, String mode, String kit, long endedAtEpochMs, long durationMs,
                        boolean draw, List<Participant> participants) {
        public Entry {
            mode = mode == null ? "" : mode;
            kit = kit == null ? "" : kit;
            participants = List.copyOf(participants == null ? List.of() : participants);
        }

        public Participant participant(UUID playerId) {
            for (Participant p : participants) {
                if (p.id().equals(playerId)) {
                    return p;
                }
            }
            return null;
        }

        /** Total kills of one team side (team colour name), or of a single fighter. */
        public int killsOfSide(String teamColor) {
            int total = 0;
            for (Participant p : participants) {
                if (teamColor == null ? p.teamColor() == null : teamColor.equals(p.teamColor())) {
                    total += p.kills();
                }
            }
            return total;
        }
    }

    private final Path file;
    private final Plugin plugin;
    private final Map<UUID, ArrayDeque<Entry>> byPlayer = new ConcurrentHashMap<>();

    public MatchHistoryStore(Plugin plugin) {
        this(plugin, PluginIdentity.dataFolder(plugin).toPath().resolve("match-history.bin"));
    }

    /** Test / override constructor with an explicit file location. */
    public MatchHistoryStore(Plugin plugin, Path file) {
        this.plugin = plugin;
        this.file = file;
        if (file.getParent() != null) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ignored) {
                // write() will log persistence failures if the folder really is missing.
            }
        }
        load();
    }

    /** Records a finished match for every participant (newest kept last in the deque). */
    public synchronized void record(Entry entry) {
        if (entry == null || entry.matchId() == null || entry.participants().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Participant p : entry.participants()) {
            ArrayDeque<Entry> deque = byPlayer.computeIfAbsent(p.id(), ignored -> new ArrayDeque<>());
            deque.removeIf(e -> e.matchId().equals(entry.matchId()));
            deque.addLast(entry);
            while (deque.size() > MAX_PER_PLAYER) {
                deque.pollFirst();
            }
            deque.removeIf(e -> now - e.endedAtEpochMs() > TTL_MS);
        }
        writeAsync();
    }

    /** @return {@code viewer}'s matches, newest first, expired entries dropped. */
    public synchronized List<Entry> recent(UUID viewer) {
        if (viewer == null) {
            return List.of();
        }
        ArrayDeque<Entry> deque = byPlayer.get(viewer);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        deque.removeIf(e -> now - e.endedAtEpochMs() > TTL_MS);
        List<Entry> out = new ArrayList<>(deque);
        java.util.Collections.reverse(out);
        return out;
    }

    /** @return one entry by match id, if the viewer took part and it is still retained. */
    public Entry get(UUID viewer, UUID matchId) {
        for (Entry e : recent(viewer)) {
            if (e.matchId().equals(matchId)) {
                return e;
            }
        }
        return null;
    }

    // ---- persistence ----

    private void writeAsync() {
        if (plugin == null) {
            write();
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::write);
    }

    private synchronized void write() {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
                out.writeInt(FORMAT_MAGIC);
                out.writeInt(1);
                List<Map.Entry<UUID, ArrayDeque<Entry>>> all = new ArrayList<>(byPlayer.entrySet());
                out.writeInt(all.size());
                for (Map.Entry<UUID, ArrayDeque<Entry>> e : all) {
                    writeUuid(out, e.getKey());
                    out.writeInt(e.getValue().size());
                    for (Entry entry : e.getValue()) {
                        writeEntry(out, entry);
                    }
                }
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "Failed to persist match history", e);
            }
        }
    }

    private synchronized void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int magic = in.readInt();
            if (magic != FORMAT_MAGIC) {
                return;
            }
            int version = in.readInt();
            if (version != 1) {
                return;
            }
            long now = System.currentTimeMillis();
            int players = in.readInt();
            for (int i = 0; i < players; i++) {
                UUID id = readUuid(in);
                int count = in.readInt();
                if (count < 0 || count > MAX_PER_PLAYER * 2) {
                    throw new IOException("bad entry count");
                }
                ArrayDeque<Entry> deque = new ArrayDeque<>();
                for (int j = 0; j < count; j++) {
                    Entry entry = readEntry(in);
                    if (entry != null && now - entry.endedAtEpochMs() <= TTL_MS) {
                        deque.addLast(entry);
                    }
                }
                if (!deque.isEmpty()) {
                    byPlayer.put(id, deque);
                }
            }
        } catch (IOException ignored) {
            // Corrupt / partial file: start clean rather than blocking startup.
            byPlayer.clear();
        }
    }

    private static void writeEntry(DataOutputStream out, Entry entry) throws IOException {
        writeUuid(out, entry.matchId());
        writeString(out, entry.mode());
        writeString(out, entry.kit());
        out.writeLong(entry.endedAtEpochMs());
        out.writeLong(entry.durationMs());
        out.writeBoolean(entry.draw());
        out.writeInt(entry.participants().size());
        for (Participant p : entry.participants()) {
            writeUuid(out, p.id());
            writeString(out, p.name());
            writeString(out, p.teamColor() == null ? "" : p.teamColor());
            out.writeInt(p.kills());
            out.writeBoolean(p.winner());
        }
    }

    private static Entry readEntry(DataInputStream in) throws IOException {
        UUID matchId = readUuid(in);
        String mode = readString(in);
        String kit = readString(in);
        long ended = in.readLong();
        long duration = in.readLong();
        boolean draw = in.readBoolean();
        int count = in.readInt();
        if (count < 0 || count > 64) {
            throw new IOException("bad participant count");
        }
        List<Participant> participants = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = readUuid(in);
            String name = readString(in);
            String teamColor = readString(in);
            int kills = in.readInt();
            boolean winner = in.readBoolean();
            participants.add(new Participant(id, name, teamColor.isEmpty() ? null : teamColor, kills, winner));
        }
        return new Entry(matchId, mode, kit, ended, duration, draw, participants);
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
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
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
