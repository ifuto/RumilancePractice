package com.rumilance.practice.headfont;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;

/**
 * Renders tiny 8x8 player-head icons in chat / action bars, using the same approach as the
 * HeadsChat / ChatHeadFont plugins: a tiny resource pack provides a custom font ("head") made of
 * one-pixel glyphs plus negative-space glyphs; each face pixel is emitted as a coloured
 * {@code <font>} component (via MiniMessage/font components), and the cursor is reset between
 * rows with negative space to reconstruct the head.
 *
 * <p>The resource pack is generated entirely in code (no binary assets shipped), written to the
 * plugin data folder, and served over a built-in HTTP server so clients can fetch it. On join the
 * pack is pushed to each player. If a player declines, heads simply fall back to a blank glyph
 * (no crash). Head colours come from the player's Mojang skin, fetched async and cached.</p>
 */
public final class HeadFontService implements Listener {

    public static final String FONT = "rumilance:head";

    // Negative-space glyphs move the cursor back 1px (F101) and 2px (F102).
    private static final char NEG_1 = '\uF101'; // F101: move cursor back 1px
    private static final char NEG_2 = '\uF102'; // F102: move cursor back 2px

    private final Plugin plugin;
    private final Map<UUID, TextColor[][]> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pending = new ConcurrentHashMap<>();

    private byte[] packBytes;
    private String sha1;
    private String packUrl;
    private HttpServer httpServer;

    public HeadFontService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            buildPack();
            startServer();
        } catch (Exception e) {
            plugin.getLogger().warning("Head font resource pack failed to build: " + e.getMessage());
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Push to everyone already online (on enable).
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendPack(p);
        }
    }

    public void shutdown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    // ------------------------------------------------------------------ resource pack

    private void buildPack() throws IOException {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            out.setLevel(Deflater.BEST_COMPRESSION);

            put(out, "pack.mcmeta", ("""
                    {
                      "pack": {
                        "description": "N Arena head icons",
                        "pack_format": 64,
                        "min_format": 34,
                        "max_format": 99
                      }
                    }
                    """).getBytes(StandardCharsets.UTF_8));

            // Register the custom font into the default font via a reference provider.
            put(out, "assets/minecraft/font/default.json", ("""
                    {
                      "providers": [
                        { "type": "reference", "id": "rumilance:head" }
                      ]
                    }
                    """).getBytes(StandardCharsets.UTF_8));

            put(out, "assets/rumilance/font/head.json", fontJson().getBytes(StandardCharsets.UTF_8));

            // Eight 1x8 textures; only one row is opaque white (index i -> row i).
            for (int i = 0; i < 8; i++) {
                put(out, "assets/rumilance/textures/head/pixel" + (i + 1) + ".png", makePixelPng(i));
            }
        }
        packBytes = zip.toByteArray();
        try {
            sha1 = sha1Hex(packBytes);
        } catch (Exception e) {
            throw new IOException("Failed to hash resource pack", e);
        }

        Path file = plugin.getDataFolder().toPath().resolve("head-pack.zip");
        Files.createDirectories(file.getParent());
        Files.write(file, packBytes);
    }

    private static String fontJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"providers\":[");
        // Negative-space provider.
        sb.append("{\"type\":\"space\",\"advances\":{");
        for (int i = 1; i <= 9; i++) {
            sb.append("\"\\uF10").append(i).append("\":-").append(i).append(',');
        }
        sb.setLength(sb.length() - 1);
        sb.append("}},");
        // One bitmap provider per pixel dot.
        for (int i = 0; i < 8; i++) {
            sb.append("{\"type\":\"bitmap\",\"file\":\"rumilance:head/pixel")
                    .append(i + 1).append(".png\",\"ascent\":8,\"height\":8,\"chars\":[\"\\uF00")
                    .append(i + 1).append("\"]},");
        }
        sb.setLength(sb.length() - 1);
        sb.append("]}");
        return sb.toString();
    }

    /** A 1px x 8px grayscale+alpha PNG whose only opaque pixel is on {@code dotRow}. */
    private static byte[] makePixelPng(int dotRow) throws IOException {
        BufferedImage img = new BufferedImage(1, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            int alpha = (y == dotRow) ? 255 : 0;
            img.setRGB(0, y, (alpha << 24) | 0x00FFFFFF); // white dot
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static void put(ZipOutputStream out, String path, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        out.write(data);
        out.closeEntry();
    }

    private static String sha1Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private void startServer() throws IOException {
        int port = plugin.getConfig().getInt("head-font.port", 8733);
        String configuredUrl = plugin.getConfig().getString("head-font.url", null);
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            this.packUrl = configuredUrl;
            return; // externally hosted; no local server needed
        }
        httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        byte[] bytes = packBytes;
        httpServer.createContext("/head-pack.zip", exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IOException ignored) {
            }
        });
        httpServer.setExecutor(null);
        httpServer.start();
        String host = Bukkit.getIp();
        if (host == null || host.isBlank() || host.equals("0.0.0.0")) {
            host = "127.0.0.1";
        }
        this.packUrl = "http://" + host + ":" + port + "/head-pack.zip";
        plugin.getLogger().info("Head-font resource pack served at " + packUrl
                + " (set head-font.url in config.yml to override).");
    }

    private void sendPack(Player player) {
        if (packUrl == null) {
            return;
        }
        try {
            player.setResourcePack(UUID.randomUUID(), packUrl, sha1,
                    Component.text("N Arena の顔アイコン用リソースパックです。"), true);
        } catch (Throwable t) {
            try {
                player.setResourcePack(packUrl, sha1);
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Delay slightly so the player is fully loaded before the pack prompt.
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendPack(event.getPlayer()), 30L);
    }

    // ------------------------------------------------------------------ skin colours

    /** Returns an 8x8 grid of colours for the player's face, or null if not yet loaded. */
    public TextColor[][] faceGrid(Player player) {
        TextColor[][] grid = cache.get(player.getUniqueId());
        if (grid == null && pending.putIfAbsent(player.getUniqueId(), Boolean.TRUE) == null) {
            fetchAsync(player);
        }
        return grid;
    }

    private void fetchAsync(Player online) {
        UUID uuid = online.getUniqueId();
        // Read the skin URL straight off the server-cached profile (no extra Mojang lookup needed).
        String skinUrl = skinUrlOf(online);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextColor[][] grid = null;
                if (skinUrl != null) {
                    BufferedImage skin = ImageIO.read(new URL(skinUrl));
                    grid = gridFromSkin(skin, true);
                }
                if (grid == null) {
                    grid = loadGridFromSessionServer(uuid);
                }
                if (grid != null) {
                    cache.put(uuid, grid);
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Head fetch failed for " + uuid + ": " + e);
            } finally {
                pending.remove(uuid);
            }
        });
    }

    /** Extracts the skin texture URL from the player's cached profile, or null. */
    private String skinUrlOf(Player player) {
        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = player.getPlayerProfile();
            com.destroystokyo.paper.profile.ProfileProperty prop =
                    profile.getProperties().stream()
                            .filter(p -> p.getName().equals("textures"))
                            .findFirst().orElse(null);
            if (prop == null) {
                return null;
            }
            String decoded = new String(Base64.getDecoder().decode(prop.getValue()), StandardCharsets.UTF_8);
            int urlIdx = decoded.indexOf("\"url\"");
            if (urlIdx < 0) {
                return null;
            }
            int uq1 = decoded.indexOf('"', decoded.indexOf(':', urlIdx) + 1);
            int uq2 = decoded.indexOf('"', uq1 + 1);
            return decoded.substring(uq1 + 1, uq2);
        } catch (Exception e) {
            return null;
        }
    }

    private TextColor[][] loadGridFromSessionServer(UUID uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/"
                    + uuid.toString().replace("-", "") + "?unsigned=false");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "RumilancePractice");
            String body = readAll(conn.getInputStream());
            int valueStart = body.indexOf("\"value\"");
            int colon = body.indexOf(':', valueStart);
            int q1 = body.indexOf('"', colon + 1);
            int q2 = body.indexOf('"', q1 + 1);
            String base64 = body.substring(q1 + 1, q2);
            String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            int urlIdx = decoded.indexOf("\"url\"");
            int uq1 = decoded.indexOf('"', decoded.indexOf(':', urlIdx) + 1);
            int uq2 = decoded.indexOf('"', uq1 + 1);
            String skinUrl = decoded.substring(uq1 + 1, uq2);

            BufferedImage skin = ImageIO.read(new URL(skinUrl));
            return gridFromSkin(skin, true);
        } catch (Exception e) {
            return null;
        }
    }

    private static TextColor[][] gridFromSkin(BufferedImage skin, boolean overlay) {
        if (skin == null) {
            return null;
        }
        if (skin.getHeight() < 64) {
            overlay = false;
        }
        TextColor[][] grid = new TextColor[8][8];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int rgb = skin.getRGB(8 + col, 8 + row); // base face region
                if (overlay) {
                    int ov = skin.getRGB(40 + col, 8 + row); // hat overlay region
                    if ((ov >>> 24) != 0) {
                        rgb = ov;
                    }
                }
                grid[row][col] = TextColor.color(rgb & 0xFFFFFF);
            }
        }
        return grid;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Builds the head component for a player. Returns an empty component (and triggers an async
     * fetch) until the skin is available, after which the next action-bar update shows the head.
     */
    public Component head(Player player) {
        TextColor[][] grid = faceGrid(player);
        if (grid == null) {
            return Component.text("   "); // spacing placeholder until loaded
        }
        return renderGrid(grid);
    }

    /**
     * Lays out an 8x8 colour grid with the custom head font. This mirrors the ChatHeadFont
     * reference exactly: pixels are emitted in row-major order; the glyph for a pixel is selected
     * by its column (F001..F008), coloured to the pixel; a -2px negative space follows each dot
     * except the last column of a row which uses -1px, reconstructing the 8x8 face.
     */
    public static Component renderGrid(TextColor[][] grid) {
        Component head = Component.empty().font(net.kyori.adventure.key.Key.key(FONT));
        for (int i = 0; i < 64; i++) {
            int row = i / 8;
            int col = i % 8;
            char glyph = (char) (0xF000 + (i % 8) + 1);
            TextColor color = grid[row][col];
            String text;
            if (i == 7 || i == 15 || i == 23 || i == 31 || i == 39 || i == 47 || i == 55) {
                text = glyph + String.valueOf(NEG_1);
            } else if (i == 63) {
                text = String.valueOf(glyph);
            } else {
                text = glyph + String.valueOf(NEG_2);
            }
            head = head.append(Component.text(text).color(color == null
                    ? TextColor.color(0xFFFFFF) : color));
        }
        // Reset back to the default font for whatever follows.
        return head.append(Component.text(" ").font(net.kyori.adventure.key.Key.key("minecraft:default")));
    }

    /** Convenience: a generic monochrome head placeholder (used when no skin yet). */
    public static Component placeholder() {
        return Component.text("   ");
    }
}
