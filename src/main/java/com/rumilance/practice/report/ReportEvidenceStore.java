package com.rumilance.practice.report;

import com.rumilance.practice.match.MatchActionRecorder.Frame;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip + packed binary evidence files (magic {@code RPE1}). One file per report, named by the
 * report id, stored under {@code plugins/RumilancePractice/reports/}. Deleting a report also
 * deletes the file (dismissal / resolution), so disk usage stays bounded by open reports only.
 */
public final class ReportEvidenceStore {

    private static final byte[] MAGIC = {'R', 'P', 'E', '1'};
    private static final int VERSION = 1;

    private final Path directory;

    public ReportEvidenceStore(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public Path pathFor(UUID reportId) {
        return directory.resolve(reportId + ".rpe");
    }

    public void save(UUID reportId, ReportEvidence evidence) throws IOException {
        Files.createDirectories(directory);
        Path file = pathFor(reportId);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp))))) {
            out.write(MAGIC);
            out.writeByte(VERSION);
            writeUuid(out, evidence.matchId());
            writeUtf(out, evidence.world());
            writeUtf(out, evidence.kit());
            writeUtf(out, evidence.mode());
            writeUuid(out, evidence.reporterId());
            writeUtf(out, evidence.reporterName());
            writeUuid(out, evidence.targetId());
            writeUtf(out, evidence.targetName());
            out.writeLong(evidence.capturedAtEpochMilli());
            writeFrames(out, evidence.reporterFrames());
            writeFrames(out, evidence.targetFrames());
        }
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public ReportEvidence load(UUID reportId) throws IOException {
        Path file = pathFor(reportId);
        if (!Files.isRegularFile(file)) {
            throw new IOException("No evidence file for report " + reportId);
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(file))))) {
            byte[] magic = in.readNBytes(4);
            if (magic.length != 4 || magic[0] != MAGIC[0] || magic[1] != MAGIC[1]
                    || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
                throw new IOException("Not an RPE1 evidence file: " + file);
            }
            int version = in.readUnsignedByte();
            if (version != VERSION) {
                throw new IOException("Unsupported evidence version " + version);
            }
            UUID matchId = readUuid(in);
            String world = readUtf(in);
            String kit = readUtf(in);
            String mode = readUtf(in);
            UUID reporterId = readUuid(in);
            String reporterName = readUtf(in);
            UUID targetId = readUuid(in);
            String targetName = readUtf(in);
            long capturedAt = in.readLong();
            List<Frame> reporterFrames = readFrames(in, matchId);
            List<Frame> targetFrames = readFrames(in, matchId);
            return new ReportEvidence(matchId, world, kit, mode, reporterId, reporterName,
                    targetId, targetName, capturedAt, reporterFrames, targetFrames);
        }
    }

    public void delete(UUID reportId) {
        try {
            Files.deleteIfExists(pathFor(reportId));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private static void writeFrames(DataOutputStream out, List<Frame> frames) throws IOException {
        List<Frame> list = frames == null ? List.of() : frames;
        out.writeInt(list.size());
        for (Frame f : list) {
            out.writeLong(f.tick());
            out.writeDouble(f.x());
            out.writeDouble(f.y());
            out.writeDouble(f.z());
            out.writeFloat(f.yaw());
            out.writeFloat(f.pitch());
            out.writeDouble(f.vx());
            out.writeDouble(f.vy());
            out.writeDouble(f.vz());
            out.writeDouble(f.health());
            out.writeBoolean(f.sprinting());
            out.writeBoolean(f.onGround());
        }
    }

    private static List<Frame> readFrames(DataInputStream in, UUID matchId) throws IOException {
        int count = in.readInt();
        List<Frame> frames = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            long tick = in.readLong();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            double vx = in.readDouble();
            double vy = in.readDouble();
            double vz = in.readDouble();
            double health = in.readDouble();
            boolean sprinting = in.readBoolean();
            boolean onGround = in.readBoolean();
            frames.add(new Frame(tick, matchId, x, y, z, yaw, pitch, vx, vy, vz, health, sprinting, onGround));
        }
        return frames;
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        if (uuid == null) {
            uuid = new UUID(0L, 0L);
        }
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeUtf(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) {
            bytes = new String(bytes, 0, 65535, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
}
