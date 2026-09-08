package com.rumilance.ac;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Computes the SHA-256 of THIS mod's own jar file, so the server can pin accepted builds
 * (forge-proof at the attestation layer: a re-packaged handshake echo cannot match the
 * official build hash; only shipping the identical jar can).
 *
 * <p>Resolution: prefer the mod container's origin root path (a real jar file on disk for
 * normal installs). Development environments may resolve to classes/ directories — hashing
 * those is meaningless, so we return empty and the server reports the jar field as "-".</p>
 */
public final class JarFingerprint {

    private JarFingerprint() {
    }

    /** Hex SHA-256 of the jar containing this mod, or empty when not on a real jar. */
    public static Optional<String> ownJarSha256() {
        Optional<ModContainer> self = FabricLoader.getInstance().getModContainer("rumilance-ac");
        if (self.isEmpty()) {
            return Optional.empty();
        }
        Path root = self.get().getRootPaths().stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        if (root == null || !root.getFileName().toString().endsWith(".jar")) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(root)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return Optional.of(HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }
}
