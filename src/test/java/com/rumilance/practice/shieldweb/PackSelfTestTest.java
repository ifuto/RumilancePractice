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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one-click 動作テスト (operation self-test): after a healthy inject + zip build every
 * probe must pass, and the probes must actually FAIL when the pack is broken — otherwise
 * the admin page would show a "works perfectly" badge for a pack that downloads empty.
 */
class PackSelfTestTest {

    @TempDir
    Path packSrc;
    @TempDir
    Path outDir;

    @BeforeEach
    void minimalPack() throws IOException {
        Files.writeString(packSrc.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":63,\"description\":\"test\"}}", StandardCharsets.UTF_8);
    }

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, 0xFF2244AA);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", out));
        return out.toByteArray();
    }

    private Path healthyZip() throws IOException {
        ShieldPackBuilder.inject(packSrc, 100, png(300, 700), List.of(100));
        Path zip = outDir.resolve("pack.zip");
        ShieldPackBuilder.buildZip(packSrc, zip);
        return zip;
    }

    private static PackSelfTest.Check find(List<PackSelfTest.Check> checks, String name) {
        for (PackSelfTest.Check c : checks) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        fail("check not found: " + name);
        return null;
    }

    @Test
    void healthyPackPassesEveryProbe() throws IOException {
        Path zip = healthyZip();
        List<PackSelfTest.Check> checks = PackSelfTest.run(packSrc, zip, List.of(100));
        assertFalse(checks.isEmpty());
        for (PackSelfTest.Check check : checks) {
            assertTrue(check.ok(), check.name() + " failed: " + check.detail());
        }
    }

    @Test
    void missingZipIsCaught() {
        List<PackSelfTest.Check> checks =
                PackSelfTest.run(packSrc, outDir.resolve("nope.zip"), List.of());
        assertFalse(find(checks, "pack.zip").ok());
    }

    @Test
    void missingMcmetaIsCaught() throws IOException {
        Files.delete(packSrc.resolve("pack.mcmeta"));
        Path zip = healthyZip(); // re-injects shield, zip builds without mcmeta
        List<PackSelfTest.Check> checks = PackSelfTest.run(packSrc, zip, List.of(100));
        assertFalse(find(checks, "pack.mcmeta").ok());
    }

    @Test
    void corruptTextureIsCaught() throws IOException {
        Path zip = healthyZip();
        // Damage the working-copy png AFTER the zip — the decode probe reads pack-src.
        Files.write(packSrc.resolve("assets/rumilance/textures/shield/shield_100.png"),
                "broken".getBytes(StandardCharsets.UTF_8));
        List<PackSelfTest.Check> checks = PackSelfTest.run(packSrc, zip, List.of(100));
        assertFalse(find(checks, "textures decode").ok());
    }

    @Test
    void unregisteredCmdMismatchIsCaught() throws IOException {
        ShieldPackBuilder.inject(packSrc, 100, png(64, 64), List.of(100));
        Path zip = outDir.resolve("pack.zip");
        ShieldPackBuilder.buildZip(packSrc, zip);
        // Registry believes cmd 200 exists, but the pack only has 100 → wiring probe fails.
        List<PackSelfTest.Check> checks = PackSelfTest.run(packSrc, zip, List.of(100, 200));
        assertFalse(find(checks, "shield wiring").ok(),
                "an un-injected cmd must not pass the wiring probe");
    }
}
