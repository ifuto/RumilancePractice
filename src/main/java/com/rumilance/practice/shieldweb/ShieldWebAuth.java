package com.rumilance.practice.shieldweb;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Shield Web access control: bearer token + source-IP allowlist.
 *
 * <p>The token is generated once ({@code token.txt} in the shield-web folder, 64 lowercase
 * hex chars = 256 bits) and shown to operators via the console and {@code /urank web}; it is
 * required for every {@code /admin/**} request, as a {@code ?token=} query parameter (browser
 * page) or {@code Authorization: Bearer} header (API).</p>
 *
 * <p>Even with the right token, admin requests only pass from private network addresses
 * (loopback / RFC-1918 / link-local / ULA / Tailscale CGNAT 100.64.0.0/10) unless the server
 * owner explicitly sets {@code shield-web.admin-allow-external: true}. The player-facing
 * {@code /pack.zip} endpoint is deliberately open — the pack URL itself is what clients get
 * told to download, and it only ever serves pack bytes.</p>
 */
public final class ShieldWebAuth {

    private final Path tokenFile;
    private String token;

    public ShieldWebAuth(Path tokenFile) {
        this.tokenFile = tokenFile;
    }

    /** Loads the existing token or generates a fresh one. Never throws — falls back to memory. */
    public synchronized String token() {
        if (token != null) {
            return token;
        }
        try {
            if (Files.isRegularFile(tokenFile)) {
                String loaded = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
                if (loaded.matches("[0-9a-f]{32,128}")) {
                    token = loaded;
                    return token;
                }
            }
        } catch (IOException ignored) {
            // fall through to generation
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : random) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        token = sb.toString();
        try {
            Files.createDirectories(tokenFile.toAbsolutePath().getParent());
            Files.writeString(tokenFile, token + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // In-memory token still protects the UI for this session.
        }
        return token;
    }

    /** Constant-time comparison so timing oracles cannot shorten the search. */
    public boolean tokenMatches(String presented) {
        if (presented == null || presented.isEmpty()) {
            return false;
        }
        String trimmed = presented.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            trimmed = trimmed.substring(7).trim();
        }
        return MessageDigest.isEqual(
                token().getBytes(StandardCharsets.UTF_8), trimmed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Private/LAN source check used for every {@code /admin/**} request. Tailscale's CGNAT
     * range (100.64.0.0/10) counts as private so the node a Funnel/TS network admins from is
     * treated like LAN; that range cannot traverse the public internet anyway.
     */
    public static boolean isAllowedSource(InetAddress address, boolean allowExternal) {
        if (allowExternal) {
            return true;
        }
        if (address == null) {
            return false;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            // Tailscale CGNAT 100.64.0.0/10 (0100 0000 01xx xxxx — second byte 64..127).
            if ((b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40) {
                return true;
            }
            return false;
        }
        // IPv6 ULA fc00::/7
        return (b[0] & 0xFE) == 0xFC && b.length == 16;
    }
}
