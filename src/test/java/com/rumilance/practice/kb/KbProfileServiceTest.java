package com.rumilance.practice.kb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** pure-JDK coverage for the KB profile directory service + SimpleJson parser. */
class KbProfileServiceTest {

    @TempDir
    Path dir;

    private KbProfileService service() {
        KbProfileService svc = new KbProfileService(dir, Logger.getLogger("test"));
        svc.reload();
        return svc;
    }

    @Test
    void parsesFlatAndNestedJson() {
        Map<String, Object> json = SimpleJson.parseObject(
                "{ \"name\": \"PvPClub\", \"horizontal\": 1.05, \"vertical\": 0.63,"
                        + " \"source\": { \"mod\": \"kb-probe 0.5.0\", \"samplesH\": 128 } }");
        assertEquals("PvPClub", json.get("name"));
        assertEquals(1.05, ((Number) json.get("horizontal")).doubleValue(), 1e-9);
        assertEquals(0.63, ((Number) json.get("vertical")).doubleValue(), 1e-9);
        assertTrue(json.get("source") instanceof Map);
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("{ \"a\": , }"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("[1,2]"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parse(""));
        assertThrows(SimpleJson.JsonException.class,
                () -> SimpleJson.parseObject("{ \"a\": 1 } trailing"));
    }

    @Test
    void escapesAndUnicodeInStrings() {
        Map<String, Object> json = SimpleJson.parseObject(
                "{ \"n\": \"サーバー \\\"A\\\"\\n改行\" }");
        assertEquals("サーバー \"A\"\n改行", json.get("n"));
    }

    @Test
    void loadsProfilesByFileNameWithoutExtension() throws Exception {
        Files.writeString(dir.resolve("PvPClub.json"),
                "{ \"horizontal\": 2.0, \"vertical\": 0.4, \"name\": \"anything\" }");
        Files.writeString(dir.resolve("skip-me.txt"), "not json");
        Files.writeString(dir.resolve("broken.json"), "{ horizontal: }");
        KbProfileService svc = service();
        assertEquals(java.util.List.of("PvPClub"), svc.names());
        double[] f = svc.findFactor("PvPClub").orElseThrow();
        assertEquals(2.0, f[0], 1e-9);
        assertEquals(0.4, f[1], 1e-9);
        // name キーではなくファイル名が正 (appendix strip of ".json").
        assertFalse(svc.exists("anything"));
    }

    @Test
    void parsesStagedProbe07Export() throws Exception {
        // kb-probe 0.7.0 "staged-knockback" export shape (extra keys + source block).
        Files.writeString(dir.resolve("VeltHC.json"), "{"
                + "\"name\": \"VeltHC\","
                + "\"horizontal\": 1.0, \"vertical\": 1.0,"
                + "\"verticalLimit\": 0.51, \"extraHorizontal\": 0.44, \"extraVertical\": 0.12,"
                + "\"frictionHorizontal\": 0.62, \"frictionVertical\": 0.5,"
                + "\"airHorizontalMultiplier\": 0.9, \"airVerticalMultiplier\": 0.2,"
                + "\"knockbackEnchant\": 0.55, \"attackerSlowdown\": 0.3, \"hitDelay\": null,"
                + "\"extraReappliesFriction\": false, \"gravity\": 0.08,"
                + "\"source\": {\"mod\": \"kb-probe 0.7.0\", \"formula\": \"staged-knockback\","
                + "\"samples\": 42, \"samplesH\": 30, \"samplesV\": 20, \"rmsError\": 0.01}}");
        KbProfileService svc = service();
        assertEquals(java.util.List.of("VeltHC"), svc.names());
        assertTrue(svc.findFactor("VeltHC").isEmpty());
        StagedKnockback staged = svc.findStaged("VeltHC").orElseThrow();
        assertEquals(1.0d, staged.horizontal(), 1.0E-9d);
        assertEquals(0.51d, staged.verticalLimit(), 1.0E-9d);
        assertEquals(0.44d, staged.extraHorizontal(), 1.0E-9d);
        assertEquals(0.12d, staged.extraVertical(), 1.0E-9d);
        assertEquals(0.62d, staged.frictionHorizontal(), 1.0E-9d);
        assertEquals(0.9d, staged.airHorizontalMultiplier(), 1.0E-9d);
        assertEquals(0.2d, staged.airVerticalMultiplier(), 1.0E-9d);
        assertEquals(0.55d, staged.knockbackEnchant(), 1.0E-9d);
        assertFalse(staged.extraReappliesFriction());
        // measurement-side corrections are ignored (not part of the applied model)
        var resolved = svc.resolveChoice("VeltHC");
        assertTrue(resolved.hasProfile());
        assertEquals("VeltHC", resolved.name());
        assertEquals(staged, resolved.staged());
    }

    @Test
    void stagedMissingFieldsFallBackToVanilla() throws Exception {
        // only one staged key present → still a staged file; the rest fill with vanilla.
        Files.writeString(dir.resolve("half.json"),
                "{ \"horizontal\": 1.1, \"vertical\": 0.9, \"verticalLimit\": 0.7 }");
        KbProfileService svc = service();
        StagedKnockback staged = svc.findStaged("half").orElseThrow();
        assertEquals(1.1d, staged.horizontal(), 1.0E-9d);
        assertEquals(0.7d, staged.verticalLimit(), 1.0E-9d);
        assertEquals(StagedKnockback.VANILLA.extraHorizontal(), staged.extraHorizontal(), 1.0E-9d);
        assertEquals(StagedKnockback.VANILLA.frictionHorizontal(), staged.frictionHorizontal(), 1.0E-9d);
        assertTrue(staged.extraReappliesFriction());
    }

    @Test
    void legacyAndStagedFilesCoexist() throws Exception {
        Files.writeString(dir.resolve("old.json"),
                "{ \"horizontal\": 1.5, \"vertical\": 0.8 }");
        Files.writeString(dir.resolve("new.json"),
                "{ \"horizontal\": 1.0, \"vertical\": 1.0, \"frictionHorizontal\": 0.6 }");
        KbProfileService svc = service();
        assertTrue(svc.findFactor("old").isPresent());
        assertTrue(svc.findStaged("old").isEmpty());
        assertTrue(svc.findStaged("new").isPresent());
        assertTrue(svc.findFactor("new").isEmpty());
        svc.configureDefault("new");
        assertTrue(svc.resolveChoice(null).staged() != null);
        svc.configureDefault("old");
        var r = svc.resolveChoice(null);
        assertTrue(r.staged() == null);
        assertEquals(1.5d, r.horizontal(), 1.0E-9d);
    }

    @Test
    void rejectsOutOfBandFactors() throws Exception {
        Files.writeString(dir.resolve("moon.json"), "{ \"horizontal\": 40, \"vertical\": 1 }");
        assertTrue(service().names().isEmpty());
    }

    @Test
    void resolveChoiceSemantics() throws Exception {
        Files.writeString(dir.resolve("fast.json"), "{ \"horizontal\": 1.5, \"vertical\": 0.8 }");
        KbProfileService svc = service();
        svc.configureDefault("fast");
        // 未選択 (null / 既定 sentinel) → 既定プロファイルに解決される
        var r = svc.resolveChoice(null);
        assertTrue(r.hasProfile());
        assertEquals("fast", r.name());
        assertEquals(1.5, r.horizontal(), 1e-9);
        // 明示「変更無し」 → 層なし
        assertFalse(svc.resolveChoice(KbProfileService.CHOICE_NONE).hasProfile());
        // 直接名
        assertTrue(svc.resolveChoice("fast").hasProfile());
        // 既定が空 → 何も適用しない
        svc.configureDefault("");
        assertFalse(svc.resolveChoice(null).hasProfile());
        // 不明な名前は安全側で層なし
        assertFalse(svc.resolveChoice("missing").hasProfile());
    }
}
