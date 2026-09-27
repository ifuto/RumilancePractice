package com.rumilance.practice.shieldweb;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

/**
 * Java port of {@code tools/add_custom_shield.py} — the resource-pack surgery the Shield Web
 * UI performs on a working copy of {@code resourcepack/}.
 *
 * <p>Pure JDK (no Bukkit): everything is expressed against a pack source directory so the
 * whole pipeline is unit-testable without a server. The {@code shield.json} vanilla override
 * file is always REGENERATED from the authoritative {@link ShieldRegistry} contents, so this
 * class never has to parse JSON — it only emits it.</p>
 *
 * <ul>
 *   <li>textures → {@code assets/rumilance/textures/shield/shield_<cmd>.png}</li>
 *   <li>models → {@code assets/rumilance/models/item/shield_<cmd>.json} (+ {@code _blocking})</li>
 *   <li>overrides → {@code assets/minecraft/models/item/shield.json} (custom_model_data)</li>
 *   <li>zip → deterministic byte output (sorted entries, fixed timestamps)</li>
 * </ul>
 */
public final class ShieldPackBuilder {

    /** Uploaded artwork is normalised to this edge (crisp on the vanilla shield UV map). */
    public static final int MAX_TEXTURE_SIZE = 512;
    /** Hard upload cap: PNG validation and resizing are done in-memory. */
    public static final int MAX_UPLOAD_BYTES = 8 * 1024 * 1024;

    private static final String TEXTURE_DIR = "assets/rumilance/textures/shield";
    private static final String MODEL_DIR = "assets/rumilance/models/item";
    private static final String VANILLA_MODEL_DIR = "assets/minecraft/models/item";
    private static final String REGISTRY_KEY = "_rumilance_shields";
    /** DOS-era fixed timestamp keeps rebuilds byte-identical for unchanged content. */
    private static final long ZIP_TIMESTAMP = 315532800000L; // 1980-01-02T00:00:00Z

    private ShieldPackBuilder() {
    }

    // ------------------------------------------------------------------ PNG handling

    /**
     * Validates PNG bytes and returns the decoded image. Rejects anything that is not a PNG
     * ImageIO can decode, or empty — the admin UI must never corrupt the pack with a bad drop.
     */
    public static BufferedImage validatePng(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 8 || bytes.length > MAX_UPLOAD_BYTES) {
            throw new IOException("PNG が不正です（0バイト、または "
                    + (MAX_UPLOAD_BYTES / 1024 / 1024) + "MB 超過）");
        }
        // PNG magic: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) != 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IOException("PNG ファイルではありません（マジックバイト不一致）");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("PNG としてデコードできませんでした");
        }
        return image;
    }

    /** Scales the artwork down to fit {@link #MAX_TEXTURE_SIZE} (alpha preserved). */
    public static BufferedImage normalize(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= MAX_TEXTURE_SIZE && h <= MAX_TEXTURE_SIZE) {
            return source;
        }
        double scale = Math.min((double) MAX_TEXTURE_SIZE / w, (double) MAX_TEXTURE_SIZE / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    public static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", out)) {
            throw new IOException("PNG エンコードに失敗しました");
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ injection

    /**
     * Writes the texture + models for {@code cmd} into {@code packSrc} and regenerates
     * {@code shield.json} from {@code allCmds} (the authoritative registry view, includes
     * {@code cmd}). The artwork goes through {@link ShieldComposite}: it is automatically
     * cropped to the shield's tall face and composited onto the wooden body, so any photo
     * comes out as a proper shield. Idempotent: re-injecting an existing cmd overwrites it.
     */
    public static void inject(Path packSrc, int cmd, byte[] pngBytes, List<Integer> allCmds)
            throws IOException {
        if (cmd <= 0) {
            throw new IOException("cmd は 1 以上の整数にしてください");
        }
        // Upload → 縦長に自動トリミング → 盾の木部へ合成（ShieldComposite）。
        BufferedImage image = ShieldComposite.compose(packSrc, pngBytes);
        Files.createDirectories(packSrc.resolve(TEXTURE_DIR));
        Files.createDirectories(packSrc.resolve(MODEL_DIR));
        Files.createDirectories(packSrc.resolve(VANILLA_MODEL_DIR));

        Files.write(packSrc.resolve(TEXTURE_DIR + "/shield_" + cmd + ".png"), toPngBytes(image));

        String textureRef = "rumilance:shield/shield_" + cmd;
        String blockingRef = "rumilance:item/shield_" + cmd + "_blocking";
        writeJson(packSrc.resolve(MODEL_DIR + "/shield_" + cmd + ".json"),
                modelFor(textureRef, blockingRef));
        writeJson(packSrc.resolve(MODEL_DIR + "/shield_" + cmd + "_blocking.json"),
                modelBlocking(textureRef));

        regenerateShieldJson(packSrc, allCmds);
    }

    /** Removes every trace of {@code cmd} and regenerates {@code shield.json} without it. */
    public static void remove(Path packSrc, int cmd, List<Integer> remainingCmds) throws IOException {
        Files.deleteIfExists(packSrc.resolve(TEXTURE_DIR + "/shield_" + cmd + ".png"));
        Files.deleteIfExists(packSrc.resolve(MODEL_DIR + "/shield_" + cmd + ".json"));
        Files.deleteIfExists(packSrc.resolve(MODEL_DIR + "/shield_" + cmd + "_blocking.json"));
        regenerateShieldJson(packSrc, remainingCmds);
    }

    /**
     * Rewrites {@code assets/minecraft/models/item/shield.json} from scratch: vanilla base
     * (parent / textures / vanilla display) + blocking override + one custom_model_data
     * override per registered cmd, sorted ascending. Because we own this file, regeneration
     * is always exact — leftover overrides from hand edits or older runs cannot accumulate.
     */
    public static void regenerateShieldJson(Path packSrc, List<Integer> cmds) throws IOException {
        Files.createDirectories(packSrc.resolve(VANILLA_MODEL_DIR));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("parent", "builtin/entity");
        root.put("gui_light", "front");
        Map<String, Object> textures = new LinkedHashMap<>();
        textures.put("shield_base", "item/shield_base");
        textures.put("shield_base_nopattern", "item/shield_base_nopattern");
        textures.put("particle", "block/dark_oak_planks");
        root.put("textures", textures);
        root.put("display", display(false));

        Map<String, Object> blockingPredicate = new LinkedHashMap<>();
        blockingPredicate.put("blocking", 1);
        List<Object> overrides = new ArrayList<>();
        overrides.add(overrideOf(blockingPredicate, "item/shield_blocking"));

        Map<String, Object> registry = new LinkedHashMap<>();
        TreeSet<Integer> sorted = new TreeSet<>(cmds);
        for (Integer cmd : sorted) {
            if (cmd == null || cmd <= 0) {
                continue;
            }
            registry.put(String.valueOf(cmd), "rumilance:item/shield_" + cmd);
            Map<String, Object> predicate = new LinkedHashMap<>();
            predicate.put("custom_model_data", cmd);
            overrides.add(overrideOf(predicate, "rumilance:item/shield_" + cmd));
        }
        root.put("overrides", overrides);
        root.put(REGISTRY_KEY, registry);
        writeJson(packSrc.resolve(VANILLA_MODEL_DIR + "/shield.json"), root);
    }

    // ------------------------------------------------------------------ zip + hash

    /**
     * Zips {@code packSrc} into {@code outZip} with {@code pack.mcmeta} at the archive root.
     * Entries are sorted and timestamps fixed, so identical content produces identical bytes
     * (stable SHA-1 → clients are not re-downloading an unchanged pack).
     */
    public static void buildZip(Path packSrc, Path outZip) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(packSrc, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.getFileName().toString().equals(".DS_Store")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files);
        Files.createDirectories(outZip.toAbsolutePath().getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outZip))) {
            for (Path file : files) {
                String name = packSrc.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(ZIP_TIMESTAMP);
                zip.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    public static String sha1Hex(Path file) throws IOException {
        MessageDigest digest = newSha1();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newSha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ pack layout helpers

    public static Path textureFile(Path packSrc, int cmd) {
        return packSrc.resolve(TEXTURE_DIR + "/shield_" + cmd + ".png");
    }

    // ------------------------------------------------------------------ model JSON shapes (port of add_custom_shield.py)

    private static Map<String, Object> modelFor(String texture, String blockingModel) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("parent", "builtin/entity");
        model.put("gui_light", "front");
        Map<String, Object> textures = new LinkedHashMap<>();
        textures.put("shield_base", texture);
        textures.put("shield_base_nopattern", texture);
        textures.put("particle", "block/dark_oak_planks");
        model.put("textures", textures);
        model.put("display", display(false));
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("blocking", 1);
        model.put("overrides", List.of(overrideOf(predicate, blockingModel)));
        return model;
    }

    private static Map<String, Object> modelBlocking(String texture) {
        Map<String, Object> model = modelFor(texture, "");
        model.remove("overrides");
        model.put("display", display(true));
        return model;
    }

    private static Map<String, Object> overrideOf(Map<String, Object> predicate, String model) {
        Map<String, Object> override = new LinkedHashMap<>();
        override.put("predicate", predicate);
        override.put("model", model);
        return override;
    }

    /** Vanilla item/shield transforms, unchanged since 1.9 (same table as the python tool). */
    private static Map<String, Object> display(boolean blocking) {
        Map<String, Object> display = new LinkedHashMap<>();
        if (!blocking) {
            display.put("thirdperson_righthand", transform(new int[]{0, 90, 0}, new double[]{10, 6, -4}, new double[]{1, 1, 1}));
            display.put("thirdperson_lefthand", transform(new int[]{0, 90, 0}, new double[]{10, 6, 12}, new double[]{1, 1, 1}));
            display.put("firstperson_righthand", transform(new int[]{0, 180, 5}, new double[]{-10, 2, -10}, new double[]{1.25, 1.25, 1.25}));
            display.put("firstperson_lefthand", transform(new int[]{0, 180, -5}, new double[]{10, 0, -10}, new double[]{1.25, 1.25, 1.25}));
            display.put("gui", transform(new int[]{15, -25, -5}, new double[]{2, 3, 0}, new double[]{0.65, 0.65, 0.65}));
            display.put("fixed", transform(new int[]{0, 180, 0}, new double[]{-2, 4, -5}, new double[]{0.5, 0.5, 0.5}));
            display.put("ground", transform(new int[]{0, 0, 0}, new double[]{4, 4, 2}, new double[]{0.25, 0.25, 0.25}));
        } else {
            display.put("thirdperson_righthand", transform(new int[]{45, 135, 0}, new double[]{3.51, 11, -2}, new double[]{1, 1, 1}));
            display.put("thirdperson_lefthand", transform(new int[]{45, 135, 0}, new double[]{13.51, 3, 5}, new double[]{1, 1, 1}));
            display.put("firstperson_righthand", transform(new int[]{0, 180, -5}, new double[]{-15, 5, -11}, new double[]{1.25, 1.25, 1.25}));
            display.put("firstperson_lefthand", transform(new int[]{0, 180, -5}, new double[]{5, 5, -11}, new double[]{1.25, 1.25, 1.25}));
            display.put("gui", transform(new int[]{15, -25, -5}, new double[]{2, 3, 0}, new double[]{0.65, 0.65, 0.65}));
        }
        return display;
    }

    private static Map<String, Object> transform(int[] rotation, double[] translation, double[] scale) {
        Map<String, Object> t = new LinkedHashMap<>();
        List<Object> r = new ArrayList<>();
        for (int v : rotation) {
            r.add(v);
        }
        List<Object> tr = new ArrayList<>();
        for (double v : translation) {
            tr.add(v);
        }
        List<Object> s = new ArrayList<>();
        for (double v : scale) {
            s.add(v);
        }
        t.put("rotation", r);
        t.put("translation", tr);
        t.put("scale", s);
        return t;
    }

    // ------------------------------------------------------------------ minimal JSON writer

    static void writeJson(Path file, Object json) throws IOException {
        StringBuilder sb = new StringBuilder();
        appendJson(json, sb, 0);
        sb.append('\n');
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static void appendJson(Object value, StringBuilder sb, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                pad(sb, indent + 1);
                sb.append('"').append(escape(e.getKey())).append("\": ");
                appendJson(e.getValue(), sb, indent + 1);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append('}');
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                pad(sb, indent + 1);
                appendJson(item, sb, indent + 1);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append(']');
        } else if (value instanceof String string) {
            sb.append('"').append(escape(string)).append('"');
        } else if (value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) {
            // 10.0 → 10 (pack consumers accept both, ints read cleaner)
            sb.append(d.longValue());
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(Math.max(0, indent)));
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c < 0x20
                        ? String.format("\\u%04x", (int) c)
                        : String.valueOf(c));
            }
        }
        return sb.toString();
    }
}
