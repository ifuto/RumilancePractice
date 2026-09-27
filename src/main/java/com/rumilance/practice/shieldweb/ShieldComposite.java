package com.rumilance.practice.shieldweb;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

/**
 * Turns an arbitrary uploaded picture into a proper shield texture: the image is
 * center-cropped to a tall (縦長) aspect, fitted into the shield's front face, and the
 * vanilla-style wooden body is drawn around it, so every upload looks like a real shield
 * instead of a square photo stretched over the UV map.
 *
 * <p>Layout (all in the final 512×512 texture space, i.e. the 64×64 vanilla UV × 8):</p>
 *
 * <pre>
 *   ┌──────────────── 512 ────────────────┐
 *   │  wood / iron rim (procedural base)  │
 *   │      ┌──── 256 wide ────┐           │
 *   │      │                  │           │
 *   │      │  uploaded image  │ 352 tall  │
 *   │      │  (crop 8:11)     │           │
 *   │      └──────────────────┘           │
 *   └─────────────────────────────────────┘
 * </pre>
 *
 * <p>The face rectangle mirrors the front plate of the vanilla shield
 * (32×44 of 64 UV). Operators who want the pixel-perfect vanilla wood can drop a
 * {@code _base.png} (64×64 or 512×512) into {@code assets/rumilance/textures/shield/};
 * it replaces the procedural approximation.</p>
 *
 * <p>Pure JDK &amp; headless-safe — unit-tested without a server.</p>
 */
public final class ShieldComposite {

    /** Output texture edge (vanilla 64 × 8, crisp on the high-res toggle). */
    public static final int SIZE = 512;
    /** Front-face rectangle in output space (originally 16,8 / 32×44 of the 64 UV map). */
    public static final int FACE_X = 16 * 8;
    public static final int FACE_Y = 8 * 8;
    public static final int FACE_W = 32 * 8;
    public static final int FACE_H = 44 * 8;

    /** Optional operator-supplied base texture inside the pack working copy. */
    private static final String BASE_OVERRIDE =
            "assets/rumilance/textures/shield/_base.png";

    /** Procedural base is constant — generate once per JVM. */
    private static final AtomicReference<BufferedImage> PROCEDURAL_BASE = new AtomicReference<>();

    private ShieldComposite() {
    }

    /**
     * Centre-crops {@code source} to the tall face aspect. Pure geometry, exposed for tests:
     * returns {@code {x, y, w, h}} of the crop inside the source.
     */
    static int[] faceCrop(int srcW, int srcH) {
        if (srcW <= 0 || srcH <= 0) {
            throw new IllegalArgumentException("empty image");
        }
        // tall aspect of the shield face (w:h = 32:44)
        double target = (double) FACE_W / FACE_H;
        double current = (double) srcW / srcH;
        if (current > target) {
            // too wide → crop the sides (縦長にする)
            int cropW = Math.max(1, (int) Math.round(srcH * target));
            return new int[]{(srcW - cropW) / 2, 0, cropW, srcH};
        }
        if (current < target) {
            // too tall (rarer) → crop top/bottom symmetrically
            int cropH = Math.max(1, (int) Math.round(srcW / target));
            return new int[]{0, (srcH - cropH) / 2, srcW, cropH};
        }
        return new int[]{0, 0, srcW, srcH};
    }

    /**
     * Composes the upload into a finished 512×512 shield texture.
     *
     * @param packSrc pack working copy (for the optional {@code _base.png} override)
     * @param pngBytes validated PNG of any size (re-validated here defensively)
     */
    public static BufferedImage compose(Path packSrc, byte[] pngBytes) throws IOException {
        BufferedImage upload = ShieldPackBuilder.validatePng(pngBytes);
        int[] crop = faceCrop(upload.getWidth(), upload.getHeight());

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(loadBase(packSrc), 0, 0, SIZE, SIZE, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(upload,
                FACE_X, FACE_Y, FACE_X + FACE_W, FACE_Y + FACE_H,
                crop[0], crop[1], crop[0] + crop[2], crop[1] + crop[3],
                null);
        g.dispose();
        return out;
    }

    /** Operator override wins; otherwise the cached procedural base. */
    static BufferedImage loadBase(Path packSrc) {
        if (packSrc != null) {
            Path override = packSrc.resolve(BASE_OVERRIDE);
            try {
                if (Files.isRegularFile(override)) {
                    BufferedImage loaded = ImageIO.read(override.toFile());
                    if (loaded != null && loaded.getWidth() > 0) {
                        return loaded;
                    }
                }
            } catch (IOException ignored) {
                // fall through to procedural
            }
        }
        return PROCEDURAL_BASE.updateAndGet(existing ->
                existing != null ? existing : generateWoodBase());
    }

    /**
     * Procedural stand-in for the vanilla {@code shield_base_nopattern} texture: dark-oak
     * planks with horizontal seams and the iron rim on the top edge, drawn at the vanilla
     * 64×64 grid so it stays pixel-crisp after the ×8 upscale.
     *
     * <p>It is an approximation on purpose — a comment where {@code _base.png} can replace
     * it with the real vanilla texture — but close enough that an uploaded picture reads
     * instantly as "a shield".</p>
     */
    static BufferedImage generateWoodBase() {
        final int g = 64;
        BufferedImage base = new BufferedImage(g, g, BufferedImage.TYPE_INT_ARGB);
        // dark-oak palette (vanilla-adjacent)
        int[] planks = {0xFF4A3423, 0xFF44301F, 0xFF503A28, 0xFF472F20};
        int seam = 0xFF2C1F13;
        int ironLight = 0xFFC4C9CC;
        int iron = 0xFFA9AEB2;
        int ironDark = 0xFF6F7477;
        for (int y = 0; y < g; y++) {
            for (int x = 0; x < g; x++) {
                int color;
                if (y < 2) {
                    color = ironDark;           // outer rim edge
                } else if (y < 8) {
                    color = (y < 4) ? ironLight : iron;  // iron band across the top
                } else {
                    int plankRow = y / 8;                 // 8px tall planks
                    color = planks[(plankRow + (x / 16)) % planks.length];
                    if (y % 8 == 0) {
                        color = seam;                     // horizontal seams
                    }
                    // subtle vertical joints, staggered per plank row
                    int joint = (plankRow % 2 == 0) ? 24 : 40;
                    if (x % 32 == joint % 32 && y % 8 != 0) {
                        color = seam;
                    }
                }
                base.setRGB(x, y, color);
            }
        }
        // corners of the iron band slightly darker for a stamped look
        for (int x = 0; x < g; x++) {
            base.setRGB(x, 2, ironDark);
        }
        return base;
    }
}
