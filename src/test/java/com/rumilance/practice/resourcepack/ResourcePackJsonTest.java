package com.rumilance.practice.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code resource-pack.json} is the file the operator edits by hand (only the {@code url} is
 * theirs to change; the server writes the resolved {@code sha1} back into it). It must therefore
 * round-trip safely: reading a hand-written file may never lose the URL, and a stray character
 * may never wipe the file's settings.
 */
class ResourcePackJsonTest {

    @Test
    void readsAHandWrittenFile() {
        Map<String, String> values = ResourcePackJson.parse("""
                {
                  "url": "https://github.com/ifuto/RumilancePractice/releases/download/v1.76.52/RumilanceResourcePack.zip",
                  "prompt": "N Arena のアイコン表示に必要なリソースパックです",
                  "required": false,
                  "min-client-protocol": 0,
                  "sha1": ""
                }
                """);
        assertEquals("https://github.com/ifuto/RumilancePractice/releases/download/"
                + "v1.76.52/RumilanceResourcePack.zip", values.get("url"));
        assertEquals("N Arena のアイコン表示に必要なリソースパックです", values.get("prompt"));
        assertEquals("false", values.get("required"));
        assertEquals("0", values.get("min-client-protocol"));
        assertEquals("", values.get("sha1"));
    }

    @Test
    void toleratesEscapesAndMissingKeys() {
        Map<String, String> values = ResourcePackJson.parse(
                "{\"url\": \"https://example.com/a\\\"b.zip\", \"prompt\": \"line\\nbreak\"}");
        assertEquals("https://example.com/a\"b.zip", values.get("url"));
        assertEquals("line\nbreak", values.get("prompt"));
        assertNull(values.get("sha1"));
    }

    @Test
    void malformedTailKeepsWhatWasAlreadyRead() {
        Map<String, String> values = ResourcePackJson.parse(
                "{\"url\": \"https://example.com/pack.zip\", \"broken\": ");
        assertEquals("https://example.com/pack.zip", values.get("url"));
    }

    @Test
    void roundTripsThroughTheWriter() {
        Map<String, String> written = new LinkedHashMap<>();
        written.put("url", ResourcePackJson.quote("https://example.com/pack.zip"));
        written.put("required", "true");
        written.put("min-client-protocol", "765");
        written.put("sha1", ResourcePackJson.quote("2ad094674379fb5cc9e1f224647b9f30c26e2072"));
        String json = ResourcePackJson.write(written);
        Map<String, String> read = ResourcePackJson.parse(json);
        assertEquals("https://example.com/pack.zip", read.get("url"));
        assertEquals("true", read.get("required"));
        assertEquals("765", read.get("min-client-protocol"));
        assertEquals("2ad094674379fb5cc9e1f224647b9f30c26e2072", read.get("sha1"));
        assertTrue(json.endsWith("}\n"), "the file stays hand-editable");
    }

    @Test
    void unknownNestedValuesAreNotMisparsed() {
        Map<String, String> values = ResourcePackJson.parse(
                "{\"url\": \"https://example.com/pack.zip\", \"meta\": {\"nested\": 1}}");
        assertEquals("https://example.com/pack.zip", values.get("url"));
        assertEquals("{\"nested\": 1}", values.get("meta"));
    }
}
