package com.rumilance.practice.shieldweb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/**
 * On-demand integrity check for the self-hosted shield pack — the "動作テスト" button in
 * the web UI. Each probe answers one way a broken pack could silently reach players
 * (a drifted hash, a model json without its texture, a texture Minecraft cannot decode,
 * non-reproducible zips that would re-download forever).
 *
 * <p>Pure JDK and read-only: it never modifies {@code packSrc}; the determinism probe
 * builds into a scratch directory.</p>
 */
public final class PackSelfTest {

    /** One probe outcome. */
    public record Check(String name, boolean ok, String detail) {
        static Check pass(String name, String detail) {
            return new Check(name, true, detail);
        }

        static Check fail(String name, String detail) {
            return new Check(name, false, detail);
        }
    }

    private PackSelfTest() {
    }

    /**
     * Runs every probe. {@code cmds} is the authoritative registry view — every one of them
     * must be fully wired (texture + 2 models + a shield.json override).
     */
    public static List<Check> run(Path packSrc, Path packZip, List<Integer> cmds) {
        List<Check> checks = new ArrayList<>();
        checks.add(checkPackMeta(packSrc));
        checks.add(checkZipExists(packZip));
        if (!checks.get(checks.size() - 1).ok()) {
            checks.add(new Check("zip entries", false, "zip 自体が無いため中身の検査をスキップ"));
            return checks;
        }
        checks.addAll(checkZipEntries(packZip, cmds));
        checks.add(checkTexturesDecode(packSrc, cmds));
        checks.add(checkDeterminism(packSrc, packZip));
        return checks;
    }

    private static Check checkPackMeta(Path packSrc) {
        try {
            Path meta = packSrc.resolve("pack.mcmeta");
            if (!Files.isRegularFile(meta)) {
                return Check.fail("pack.mcmeta", "pack-src/pack.mcmeta がありません"
                        + "（pack-base の展開に失敗している可能性）");
            }
            String text = Files.readString(meta, StandardCharsets.UTF_8);
            return text.contains("pack_format")
                    ? Check.pass("pack.mcmeta", "pack_format を確認")
                    : Check.fail("pack.mcmeta", "pack_format キーが見つかりません");
        } catch (IOException e) {
            return Check.fail("pack.mcmeta", e.getMessage());
        }
    }

    private static Check checkZipExists(Path packZip) {
        if (!Files.isRegularFile(packZip)) {
            return Check.fail("pack.zip", "pack.zip がまだ生成されていません"
                    + "（/admin/api/repush 相当の再構築を実行してください）");
        }
        try {
            long size = Files.size(packZip);
            if (size <= 0) {
                return Check.fail("pack.zip", "0バイトです");
            }
            return Check.pass("pack.zip", (size / 1024) + " KB");
        } catch (IOException e) {
            return Check.fail("pack.zip", e.getMessage());
        }
    }

    private static List<Check> checkZipEntries(Path packZip, List<Integer> cmds) {
        List<Check> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(packZip.toFile())) {
            boolean meta = zip.getEntry("pack.mcmeta") != null;
            boolean shieldJson = zip.getEntry("assets/minecraft/models/item/shield.json") != null;
            out.add(meta && shieldJson
                    ? Check.pass("zip entries", "pack.mcmeta + shield.json 同梱（全"
                        + zip.size() + " エントリ）")
                    : Check.fail("zip entries", "pack.mcmeta=" + meta + " shield.json=" + shieldJson));

            String shield = new String(zip.getInputStream(
                    zip.getEntry("assets/minecraft/models/item/shield.json")).readAllBytes(),
                    StandardCharsets.UTF_8);
            List<String> missing = new ArrayList<>();
            for (Integer cmd : cmds) {
                boolean textureMissing = zip.getEntry(
                        "assets/rumilance/textures/shield/shield_" + cmd + ".png") == null;
                boolean modelMissing = zip.getEntry(
                        "assets/rumilance/models/item/shield_" + cmd + ".json") == null;
                boolean overrideMissing = !shield.contains(
                        "\"custom_model_data\": " + cmd);
                if (textureMissing || modelMissing || overrideMissing) {
                    missing.add("cmd=" + cmd + (textureMissing ? " texture" : "")
                            + (modelMissing ? " model" : "") + (overrideMissing ? " override" : ""));
                }
            }
            out.add(missing.isEmpty()
                    ? Check.pass("shield wiring", cmds.size() + " 件の盾すべてが"
                            + " texture/model/override 完備")
                    : Check.fail("shield wiring", "配線漏れ: " + String.join(", ", missing)));
        } catch (IOException e) {
            out.add(Check.fail("zip entries", "zip を開けません: " + e.getMessage()));
        }
        return out;
    }

    private static Check checkTexturesDecode(Path packSrc, List<Integer> cmds) {
        List<String> bad = new ArrayList<>();
        for (Integer cmd : cmds) {
            Path png = ShieldPackBuilder.textureFile(packSrc, cmd);
            try {
                if (!Files.isRegularFile(png)) {
                    bad.add("cmd=" + cmd + " なし");
                    continue;
                }
                var image = ImageIO.read(png.toFile());
                if (image == null) {
                    bad.add("cmd=" + cmd + " decode失敗");
                } else if (image.getWidth() != ShieldComposite.SIZE
                        || image.getHeight() != ShieldComposite.SIZE) {
                    bad.add("cmd=" + cmd + " " + image.getWidth() + "x" + image.getHeight()
                            + "（512x512以外）");
                }
            } catch (IOException e) {
                bad.add("cmd=" + cmd + " " + e.getMessage());
            }
        }
        return bad.isEmpty()
                ? Check.pass("textures decode", cmds.size() + " 件すべて 512x512 PNG として decode 可能")
                : Check.fail("textures decode", String.join(", ", bad));
    }

    /** The zip must rebuild byte-identically, or clients re-download on every restart. */
    private static Check checkDeterminism(Path packSrc, Path packZip) {
        Path scratch = null;
        try {
            scratch = Files.createTempDirectory("shieldweb-selftest");
            Path rebuilt = scratch.resolve("rebuilt.zip");
            ShieldPackBuilder.buildZip(packSrc, rebuilt);
            String originalSha = ShieldPackBuilder.sha1Hex(packZip);
            String rebuiltSha = ShieldPackBuilder.sha1Hex(rebuilt);
            return originalSha.equals(rebuiltSha)
                    ? Check.pass("deterministic zip", "sha1 一致: " + originalSha.substring(0, 12) + "…")
                    : Check.fail("deterministic zip", "再構築で sha1 が変わります"
                            + "（pack-src 非同期変更 or タイムスタンプ混入）: "
                            + originalSha.substring(0, 12) + "… != " + rebuiltSha.substring(0, 12) + "…");
        } catch (IOException e) {
            return Check.fail("deterministic zip", e.getMessage());
        } finally {
            if (scratch != null) {
                try {
                    Files.deleteIfExists(scratch.resolve("rebuilt.zip"));
                    Files.deleteIfExists(scratch);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
