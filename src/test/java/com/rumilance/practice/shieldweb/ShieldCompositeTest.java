package com.rumilance.practice.shieldweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auto-crop + composite of uploaded shield images: the crop must always match the face
 * aspect (tall), the composite must be a fixed 512×512 texture, and determinism matters
 * because the pack hash (after re-squeeze) is what clients cache against.
 */
class ShieldCompositeTest {

    private static byte[] png(int w, int h, int rgb) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void wideImagesAreCroppedToTallAspect() {
        // landscape input → tall output w:h = 32:44
        int[] crop = ShieldComposite.faceCrop(1000, 1000);
        assertEquals(4, crop.length);
        double ratio = crop[2] / (double) crop[3];
        assertEquals(32.0 / 44.0, ratio, 0.01);
        assertEquals(1000, crop[3], "full height kept for square input");
        assertEquals((1000 - crop[2]) / 2, crop[0], "centred horizontally");

        int[] wide = ShieldComposite.faceCrop(2000, 800);
        assertEquals(800, wide[3], "short side bounds the crop");
        double wideRatio = wide[2] / (double) wide[3];
        assertEquals(32.0 / 44.0, wideRatio, 0.01);
    }

    @Test
    void tallImagesGeometryIsCentered() {
        int[] crop = ShieldComposite.faceCrop(300, 1200);
        double ratio = crop[2] / (double) crop[3];
        assertEquals(32.0 / 44.0, ratio, 0.01);
        assertEquals(crop[0] + crop[2] / 2, 150, "still centred");
    }

    @Test
    void composeProducesFixedSizeWithFaceContent(@TempDir Path packSrc) throws IOException {
        byte[] red = png(64, 64, 0xCC2244);
        BufferedImage out = ShieldComposite.compose(packSrc, red);

        assertEquals(ShieldComposite.SIZE, out.getWidth());
        assertEquals(ShieldComposite.SIZE, out.getHeight());
        // face centre shows the upload's colour (bicubic of a flat colour is the colour)
        int centre = out.getRGB(
                ShieldComposite.FACE_X + ShieldComposite.FACE_W / 2,
                ShieldComposite.FACE_Y + ShieldComposite.FACE_H / 2);
        assertEquals(0xFFCC2244, centre & 0xFFFFFFFF, "upload must land on the shield face");
        // corners outside the face are the wooden base, not the upload
        int corner = out.getRGB(4, 4);
        assertNotEquals(0xFFCC2244, corner, "outside the face stays the base texture");
    }

    @Test
    void composeIsDeterministicForSameUpload(@TempDir Path packSrc) throws IOException {
        byte[] art = png(128, 96, 0x334455);
        BufferedImage first = ShieldComposite.compose(packSrc, art);
        BufferedImage second = ShieldComposite.compose(packSrc, art);
        for (int y = 0; y < ShieldComposite.SIZE; y += 17) {
            for (int x = 0; x < ShieldComposite.SIZE; x += 17) {
                assertEquals(first.getRGB(x, y), second.getRGB(x, y),
                        "same input → same pixels at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void tinyUploadsStillCompositeAndGarbageIsRejected(@TempDir Path packSrc) throws IOException {
        BufferedImage out = ShieldComposite.compose(packSrc, png(8, 11, 0x00AA55));
        assertEquals(ShieldComposite.SIZE, out.getWidth());

        assertThrows(IOException.class,
                () -> ShieldComposite.compose(packSrc, "not a png".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> ShieldComposite.faceCrop(0, 10));
    }
}
