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
        double[] f = svc.find("PvPClub").orElseThrow();
        assertEquals(2.0, f[0], 1e-9);
        assertEquals(0.4, f[1], 1e-9);
        // name キーではなくファイル名が正 (appendix strip of ".json").
        assertFalse(svc.exists("anything"));
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
