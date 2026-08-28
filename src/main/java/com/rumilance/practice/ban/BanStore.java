package com.rumilance.practice.ban;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip + packed binary (not JSON). Magic {@code RPB1}. Strings are length-prefixed UTF-8.
 */
public final class BanStore {

    private static final byte[] MAGIC = {'R', 'P', 'B', '1'};
    private static final int VERSION = 1;

    private final Path file;
    private final CopyOnWriteArrayList<BanRecord> records = new CopyOnWriteArrayList<>();

    public BanStore(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        records.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(file))))) {
            byte[] magic = in.readNBytes(4);
            if (magic.length != 4 || magic[0] != MAGIC[0] || magic[1] != MAGIC[1]
                    || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
                throw new IOException("Not an RPB1 ban file: " + file);
            }
            int version = in.readUnsignedByte();
            if (version != VERSION) {
                throw new IOException("Unsupported ban file version " + version);
            }
            int count = in.readInt();
            List<BanRecord> loaded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                loaded.add(readRecord(in));
            }
            records.addAll(loaded);
        }
    }

    public synchronized void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp))))) {
            out.write(MAGIC);
            out.writeByte(VERSION);
            out.writeInt(records.size());
            for (BanRecord record : records) {
                writeRecord(out, record);
            }
        }
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void add(BanRecord record) {
        records.add(record);
    }

    public List<BanRecord> all() {
        return List.copyOf(records);
    }

    public BanRecord activeOf(UUID playerId, long now) {
        BanRecord newest = null;
        for (BanRecord record : records) {
            if (!record.playerId().equals(playerId) || !record.inForce(now)) {
                continue;
            }
            if (newest == null || record.createdAtEpochMilli() > newest.createdAtEpochMilli()) {
                newest = record;
            }
        }
        return newest;
    }

    public List<BanRecord> activeNewestFirst(long now) {
        return records.stream()
                .filter(r -> r.inForce(now))
                .sorted(Comparator.comparingLong(BanRecord::createdAtEpochMilli).reversed())
                .toList();
    }

    public List<BanRecord> historyNewestFirst(UUID playerId) {
        return records.stream()
                .filter(r -> r.playerId().equals(playerId))
                .sorted(Comparator.comparingLong(BanRecord::createdAtEpochMilli).reversed())
                .toList();
    }

    public UUID uuidByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        BanRecord newest = null;
        for (BanRecord record : records) {
            if (record.playerName() == null || !record.playerName().equalsIgnoreCase(name)) {
                continue;
            }
            if (newest == null || record.createdAtEpochMilli() > newest.createdAtEpochMilli()) {
                newest = record;
            }
        }
        return newest == null ? null : newest.playerId();
    }

    public boolean deactivate(UUID playerId, long now) {
        boolean changed = false;
        for (int i = 0; i < records.size(); i++) {
            BanRecord record = records.get(i);
            if (record.playerId().equals(playerId) && record.inForce(now)) {
                records.set(i, new BanRecord(
                        record.id(), record.playerId(), record.playerName(), record.reason(),
                        record.durationLabel(), record.createdAtEpochMilli(), record.expiresAtEpochMilli(),
                        false, record.staffName()));
                changed = true;
            }
        }
        return changed;
    }

    private static void writeRecord(DataOutputStream out, BanRecord record) throws IOException {
        writeUuid(out, record.id());
        writeUuid(out, record.playerId());
        writeUtf(out, record.playerName());
        writeUtf(out, record.reason());
        writeUtf(out, record.durationLabel());
        out.writeLong(record.createdAtEpochMilli());
        out.writeLong(record.expiresAtEpochMilli());
        out.writeBoolean(record.active());
        writeUtf(out, record.staffName() == null ? "" : record.staffName());
    }

    private static BanRecord readRecord(DataInputStream in) throws IOException {
        return new BanRecord(
                readUuid(in),
                readUuid(in),
                readUtf(in),
                readUtf(in),
                readUtf(in),
                in.readLong(),
                in.readLong(),
                in.readBoolean(),
                readUtf(in)
        );
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeUtf(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) {
            throw new IOException("String too long for ban store");
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
}
