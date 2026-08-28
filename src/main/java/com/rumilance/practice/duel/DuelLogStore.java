package com.rumilance.practice.duel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Packed ring of duel IDs. Each row is 37 bytes ({@code id[5] + p1[16] + p2[16]}), no JSON.
 * Lookup is {@code slot = decode(id) % MAX} then a stored-id check, so there is no hash table.
 */
public final class DuelLogStore {

    public static final int MAX_RECORDS = 100_000_000;
    static final int ID_LEN = 5;
    static final int NAME_LEN = 16;
    static final int RECORD = ID_LEN + NAME_LEN + NAME_LEN;
    private static final int HEADER = 32;
    private static final byte[] MAGIC = {'R', 'P', 'D', '1'};

    public record Entry(String id, String player1, String player2) {
    }

    private final Path file;
    private long nextSeq;

    public DuelLogStore(Path file) {
        this.file = file;
        try {
            this.nextSeq = loadNextSeq();
        } catch (IOException e) {
            this.nextSeq = 0L;
        }
    }

    public synchronized String append(String player1, String player2) {
        long seq = nextSeq++;
        String id = DuelIds.encode(seq);
        try {
            write(seq, id, player1, player2);
        } catch (IOException ignored) {
            // Match still runs; lookup may miss this row.
        }
        return id;
    }

    public synchronized Optional<Entry> find(String id) {
        if (!DuelIds.valid(id)) {
            return Optional.empty();
        }
        long seq = DuelIds.decode(id);
        if (seq < 0L || seq >= nextSeq) {
            return Optional.empty();
        }
        long slot = seq % MAX_RECORDS;
        try (FileChannel channel = channel()) {
            if (channel.size() < HEADER + (slot + 1) * RECORD) {
                return Optional.empty();
            }
            ByteBuffer buf = ByteBuffer.allocate(RECORD);
            channel.position(HEADER + slot * (long) RECORD);
            if (channel.read(buf) < RECORD) {
                return Optional.empty();
            }
            buf.flip();
            String stored = readFixed(buf, ID_LEN);
            if (!id.equals(stored)) {
                return Optional.empty();
            }
            return Optional.of(new Entry(stored, readFixed(buf, NAME_LEN).trim(), readFixed(buf, NAME_LEN).trim()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void write(long seq, String id, String player1, String player2) throws IOException {
        Files.createDirectories(file.getParent());
        try (FileChannel channel = channel()) {
            if (channel.size() < HEADER) {
                ByteBuffer header = ByteBuffer.allocate(HEADER);
                header.put(MAGIC);
                header.put((byte) 1);
                header.put(new byte[HEADER - 5]);
                header.flip();
                channel.write(header, 0);
            }
            long slot = seq % MAX_RECORDS;
            ByteBuffer buf = ByteBuffer.allocate(RECORD);
            putFixed(buf, id, ID_LEN);
            putFixed(buf, player1, NAME_LEN);
            putFixed(buf, player2, NAME_LEN);
            buf.flip();
            channel.write(buf, HEADER + slot * (long) RECORD);
            ByteBuffer seqBuf = ByteBuffer.allocate(8);
            seqBuf.putLong(nextSeq);
            seqBuf.flip();
            channel.write(seqBuf, 5);
        }
    }

    private long loadNextSeq() throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) < 13) {
            return 0L;
        }
        try (FileChannel channel = channel()) {
            ByteBuffer magic = ByteBuffer.allocate(5);
            channel.read(magic, 0);
            magic.flip();
            if (magic.remaining() < 5 || magic.get() != MAGIC[0] || magic.get() != MAGIC[1]
                    || magic.get() != MAGIC[2] || magic.get() != MAGIC[3]) {
                return 0L;
            }
            ByteBuffer seqBuf = ByteBuffer.allocate(8);
            channel.read(seqBuf, 5);
            seqBuf.flip();
            return seqBuf.remaining() == 8 ? Math.max(0L, seqBuf.getLong()) : 0L;
        }
    }

    private FileChannel channel() throws IOException {
        return FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    private static void putFixed(ByteBuffer buf, String value, int length) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.US_ASCII);
        int n = Math.min(bytes.length, length);
        buf.put(bytes, 0, n);
        for (int i = n; i < length; i++) {
            buf.put((byte) 0);
        }
    }

    private static String readFixed(ByteBuffer buf, int length) {
        byte[] bytes = new byte[length];
        buf.get(bytes);
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.US_ASCII);
    }
}
