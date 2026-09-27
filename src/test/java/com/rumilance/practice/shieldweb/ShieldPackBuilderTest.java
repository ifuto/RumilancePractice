package com.rumilance.practice.shieldweb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pack surgery behind Shield Web ({@code tools/add_custom_shield.py}'s Java twin):
 * injection, regeneration, removal and zip building must be exact and idempotent, because a
 * broken pack json fails EVERY client's download — not just the shield's.
 */
class ShieldPackBuilderTest {

    @TempDir
    Path packSrc;
    @TempDir
    Path outDir;

    @BeforeEach
    void minimalPack() throws IOException {
        Files.writeString(packSrc.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":63,\"description\":\"test\"}}", StandardCharsets.UTF_8);
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(width / 2, height / 2, 0xFF3366CC);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", out));
        return out.toByteArray();
    }

    private String shieldJson() throws IOException {
        return Files.readString(packSrc.resolve("assets/minecraft/models/item/shield.json"),
                StandardCharsets.UTF_8);
    }

    @Test
    void injectWritesTextureModelsAndOverride() throws IOException {
        ShieldPackBuilder.inject(packSrc, 100, png(512, 512), List.of(100));

        assertTrue(Files.isRegularFile(packSrc.resolve(
                "assets/rumilance/textures/shield/shield_100.png")));
        assertTrue(Files.isRegularFile(packSrc.resolve(
                "assets/rumilance/models/item/shield_100.json")));
        assertTrue(Files.isRegularFile(packSrc.resolve(
                "assets/rumilance/models/item/shield_100_blocking.json")));

        String shield = shieldJson();
        assertTrue(shield.contains("\"custom_model_data\": 100"), shield);
        assertTrue(shield.contains("\"rumilance:item/shield_100\""), shield);
        assertTrue(shield.contains("\"blocking\": 1"), shield);
        assertTrue(shield.contains("_rumilance_shields"), shield);

        String model = Files.readString(packSrc.resolve(
                "assets/rumilance/models/item/shield_100.json"), StandardCharsets.UTF_8);
        assertTrue(model.contains("rumilance:shield/shield_100"), model);
        assertTrue(model.contains("shield_100_blocking"), model);
    }

    @Test
    void oversizedArtworkIsAutoCroppedAndCompositedTo512() throws IOException {
        // ランドスケープ入力は縦長（盾フェイスのアスペクト）へトリミングされ、
        // 木製ボディへ合成された 512×512 の完成品テクスチャが書き込まれる。
        byte[] flat = flatPng(2048, 1024, 0xFF3366CC);
        ShieldPackBuilder.inject(packSrc, 101, flat, List.of(101));
        BufferedImage written = ImageIO.read(packSrc.resolve(
                "assets/rumilance/textures/shield/shield_101.png").toFile());
        assertEquals(512, written.getWidth());
        assertEquals(512, written.getHeight());
        // フェイス中央にはアップ画像の色、フェイス外は木目ベース（=合成済み）
        int face = written.getRGB(256, 256);
        int outside = written.getRGB(4, 4);
        assertEquals(0xFF3366CC, face, "upload must reach the shield face");
        assertNotEquals(0xFF3366CC, outside, "the wooden body stays intact around the face");
    }

    /** Fully opaque single-colour artwork (unlike {@link #png}, which is one dot). */
    private static byte[] flatPng(int w, int h, int argb) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", out));
        return out.toByteArray();
    }

    @Test
    void injectionIsIdempotentAndKeepsEarlierOverrides() throws IOException {
        ShieldPackBuilder.inject(packSrc, 100, png(64, 64), List.of(100));
        // Re-uploading the same cmd replaces artwork but never duplicates the override.
        ShieldPackBuilder.inject(packSrc, 100, png(64, 64), List.of(100));
        String shield = shieldJson();
        assertEquals(1, shield.split("\"custom_model_data\": 100").length - 1, shield);

        ShieldPackBuilder.inject(packSrc, 105, png(64, 64), List.of(100, 105));
        shield = shieldJson();
        assertTrue(shield.contains("\"custom_model_data\": 100"), shield);
        assertTrue(shield.contains("\"custom_model_data\": 105"), shield);
        // Registry keeps one entry per cmd.
        assertEquals(1, shield.split("\"custom_model_data\": 105").length - 1, shield);
    }

    @Test
    void removeDeletesEveryTrace() throws IOException {
        ShieldPackBuilder.inject(packSrc, 100, png(64, 64), List.of(100));
        ShieldPackBuilder.inject(packSrc, 200, png(64, 64), List.of(100, 200));
        ShieldPackBuilder.remove(packSrc, 100, List.of(200));

        assertFalse(Files.exists(packSrc.resolve(
                "assets/rumilance/textures/shield/shield_100.png")));
        assertFalse(Files.exists(packSrc.resolve(
                "assets/rumilance/models/item/shield_100.json")));
        String shield = shieldJson();
        assertFalse(shield.contains("\"custom_model_data\": 100"), shield);
        assertTrue(shield.contains("\"custom_model_data\": 200"), shield);
    }

    @Test
    void buildZipIsDeterministicAndSha1IsWellFormed() throws IOException {
        ShieldPackBuilder.inject(packSrc, 100, png(64, 64), List.of(100));
        Path zipA = outDir.resolve("a.zip");
        Path zipB = outDir.resolve("b.zip");
        ShieldPackBuilder.buildZip(packSrc, zipA);
        ShieldPackBuilder.buildZip(packSrc, zipB);

        assertArrayEquals(Files.readAllBytes(zipA), Files.readAllBytes(zipB),
                "identical content must zip to identical bytes (stable client-facing SHA-1)");

        String sha1 = ShieldPackBuilder.sha1Hex(zipA);
        assertTrue(sha1.matches("[0-9a-f]{40}"), sha1);

        try (ZipFile zip = new ZipFile(zipA.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("assets/minecraft/models/item/shield.json"));
            assertNotNull(zip.getEntry("assets/rumilance/textures/shield/shield_100.png"));
            // Paths must be forward-slash separated for every platform.
            assertNull(zip.getEntry("assets\\rumilance\\textures\\shield\\shield_100.png"));
        }
    }

    @Test
    void garbageUploadsAreRejectedBeforeAnythingIsWritten() throws IOException {
        byte[] notPng = "this is not a png".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class,
                () -> ShieldPackBuilder.inject(packSrc, 100, notPng, List.of(100)));
        assertFalse(Files.exists(packSrc.resolve(
                "assets/rumilance/textures/shield/shield_100.png")));
        assertThrows(IOException.class, () -> ShieldPackBuilder.validatePng(new byte[0]));
        assertThrows(IOException.class,
                () -> ShieldPackBuilder.inject(packSrc, 0, png(64, 64), List.of(0)));
    }
}
