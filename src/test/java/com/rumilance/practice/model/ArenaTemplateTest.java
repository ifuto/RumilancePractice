package com.rumilance.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rumilance.practice.state.ArenaType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 内部名(一意)と外部名(重複可)、そして Queue 戦・Random Map から外せる
 * フラグの振る舞い。
 */
public class ArenaTemplateTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static ArenaTemplate arena(String internal, String display, boolean queue) {
        return new ArenaTemplate(ID, internal, ArenaType.DUEL, "world",
                0, 0, 0, 10, 10, 10, "spawnA", "spawnB", "box.schem", true, false, null,
                display, queue);
    }

    /** 旧 16 引数コンストラクタで作ったアリーナは今まで通り: 外部名=内部名、Queue 対象。 */
    @Test
    void legacyTemplateKeepsOldBehaviour() {
        ArenaTemplate t = new ArenaTemplate(ID, "Sumo_01", ArenaType.DUEL, "world",
                0, 0, 0, 10, 10, 10, "spawnA", "spawnB", "box.schem", true, false, null);

        assertEquals("Sumo_01", t.name());
        assertEquals("Sumo_01", t.displayName());
        assertEquals("Sumo_01", t.displayNameOrName());
        assertTrue(t.queueSelectable());
    }

    /** 外部名は自由に付け替えられて、内部名は変わらない。 */
    @Test
    void displayNameIsIndependentOfInternalName() {
        ArenaTemplate t = arena("arena_7f31", "Sky Bridge", true).withDisplayName("Sky Bridge");

        assertEquals("arena_7f31", t.name());
        assertEquals("Sky Bridge", t.displayName());
        assertEquals("Sky Bridge", t.displayNameOrName());
    }

    /** 空の外部名は内部名に落ちる(表示が空白にならない)。 */
    @Test
    void blankDisplayNameFallsBackToInternalName() {
        assertEquals("arena_7f31", arena("arena_7f31", "   ", true).displayNameOrName());
        assertEquals("arena_7f31", arena("arena_7f31", null, true).displayNameOrName());
    }

    /** Queue 除外フラグを切り替えても他の項目はそのまま。 */
    @Test
    void queueFlagTogglesWithoutLosingOtherFields() {
        ArenaTemplate base = arena("arena_7f31", "Sky Bridge", true);
        ArenaTemplate excluded = base.withQueueSelectable(false);

        assertTrue(base.queueSelectable());
        assertFalse(excluded.queueSelectable());
        assertEquals("Sky Bridge", excluded.displayNameOrName());
        assertEquals("arena_7f31", excluded.name());
        assertEquals("box.schem", excluded.schematicPath());
        assertTrue(excluded.enabled());
    }

    /** 既存の withX も新しい2項目を引き継ぐ(取りこぼし防止)。 */
    @Test
    void everyWitherCarriesTheNewFields() {
        ArenaTemplate t = arena("arena_7f31", "Sky Bridge", false);

        assertEquals("Sky Bridge", t.withEnabled(true).displayNameOrName());
        assertFalse(t.withEnabled(true).queueSelectable());
        assertEquals("Sky Bridge", t.withName("arena_9999").displayNameOrName());
        assertEquals("arena_9999", t.withName("arena_9999").name());
        assertEquals("Sky Bridge", t.withParty(true).displayNameOrName());
        assertFalse(t.withParty(true).queueSelectable());
        assertEquals("Sky Bridge", t.withType(ArenaType.DUEL).displayNameOrName());
        assertEquals("Sky Bridge", t.withIconMaterial("STONE").displayNameOrName());
        assertEquals("Sky Bridge", t.withSchematic("new.schem").displayNameOrName());
        assertEquals("Sky Bridge", t.withSpawns("a", "b").displayNameOrName());
        assertEquals("Sky Bridge", t.withBounds("world", 0, 0, 0, 5, 5, 5).displayNameOrName());
    }
}
