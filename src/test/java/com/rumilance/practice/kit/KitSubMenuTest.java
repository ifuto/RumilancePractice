package com.rumilance.practice.kit;

import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real kits.yml model round-trips without a server: a folder is only a button, while each child
 * is an independent kit with its own item contents, rules, editor row and ranked-stats key.
 */
class KitSubMenuTest {

    private static KitDefinition axe() {
        return KitDefinition.builder("axe")
                .displayName("Axe")
                .icon("DIAMOND_AXE")
                .items(List.of(new KitItemEntry(0, "DIAMOND_AXE", 1, null, null, Map.of(), true)))
                .armor(Map.of("helmet", "NETHERITE_HELMET"))
                .maxHealth(28)
                .knockbackMultiplier(1.3)
                .totem(false)
                .bedExplosion(true)
                .arenas(List.of("red", "blue"))
                .build();
    }

    @Test
    void convertingExistingKitCopiesItsContentsRulesLayoutsAndStatsWithoutChangingItsId() {
        YamlConfiguration yaml = new YamlConfiguration();
        KitService kits = new KitService(yaml);
        kits.save(axe());
        List<String> migrations = new ArrayList<>();
        kits.setMigrationCallbacks((from, to) -> migrations.add("layout:" + from + ">" + to),
                (from, to) -> migrations.add("elo:" + from + ">" + to));

        KitDefinition child = kits.ensureFolder("axe").orElseThrow();
        assertEquals("axe-default", child.name());
        assertEquals("axe", child.parent());
        assertNull(child.defaultChild());
        assertEquals(axe().items(), child.items());
        assertEquals(axe().armor(), child.armor());
        assertEquals(28, child.maxHealth());
        assertEquals(1.3, child.knockbackMultiplier());
        assertFalse(child.totem());
        assertTrue(child.bedExplosion());
        assertEquals(List.of("red", "blue"), child.arenas());
        assertEquals(List.of("layout:axe>axe-default", "elo:axe>axe-default"), migrations);
        assertEquals("axe-default", kits.playableId("axe"));
        assertTrue(kits.isFolder("axe"));
        assertEquals("axe-default", yaml.getString("kits.axe.default-child"));
        assertEquals("axe", yaml.getString("kits.axe-default.parent"));
        assertEquals(List.of("axe"), kits.enabled().stream().map(KitDefinition::name).toList());

        kits.reload();
        assertEquals("axe-default", kits.defaultChild("axe").orElseThrow().name());
        assertTrue(kits.get("axe-default").orElseThrow().items().getFirst().unbreakable());
        assertEquals(child.items(), kits.get("axe-default").orElseThrow().items());
        kits.ensureFolder("axe").orElseThrow();
        assertEquals(2, migrations.size(), "restarts must not duplicate player rows or children");
    }

    @Test
    void movingExistingKitRetainsItsIdentityAndUsesFolderDefaultUntilAdminSwitchesIt() {
        KitService kits = new KitService(new YamlConfiguration());
        kits.save(axe());
        KitDefinition bow = KitDefinition.builder("bow")
                .displayName("Bow")
                .icon("BOW")
                .items(List.of(new KitItemEntry(0, "BOW", 1), new KitItemEntry(1, "ARROW", 6)))
                .maxHealth(16).build();
        kits.save(bow);

        assertTrue(kits.setParent("bow", "axe"));
        assertEquals(List.of("axe"), kits.enabled().stream().map(KitDefinition::name).toList());
        assertEquals("axe-default", kits.playableId("axe"));
        assertEquals(bow.items(), kits.get("bow").orElseThrow().items());
        assertEquals(16, kits.get("bow").orElseThrow().maxHealth(), "children have their OWN rules");
        assertFalse(kits.setDefaultChild("axe", "axe"), "a default must be a direct child");
        assertFalse(kits.setDefaultChild("bow", "axe-default"));
        assertTrue(kits.setDefaultChild("axe", "bow"));
        assertEquals("bow", kits.playableId("axe"));
        assertEquals("bow", kits.get("axe").orElseThrow().defaultChild());
        kits.reload();
        assertEquals("bow", kits.playableId("axe"));
        assertEquals(16, kits.playable("axe").orElseThrow().maxHealth());
    }

    @Test
    void creatingChildIsOrdinaryKitAndDeletingDefaultFallsBackToNextChild() {
        YamlConfiguration yaml = new YamlConfiguration();
        KitService kits = new KitService(yaml);
        kits.save(axe());
        KitDefinition created = kits.createChild("club-axe", "axe", new ItemStack[41],
                "IRON_AXE", "Club Axe");
        assertNotNull(created);
        assertEquals("axe", created.parent());
        assertEquals("Club Axe", created.displayName());
        assertEquals(axe().maxHealth(), created.maxHealth());
        assertEquals(List.of("axe-default", "club-axe"),
                kits.children("axe").stream().map(KitDefinition::name).toList());
        assertFalse(kits.setParent("axe", "club-axe"), "no self/cyclic folders");
        assertFalse(kits.setParent("axe", "axe"));
        assertFalse(kits.setDefaultChild("axe", "missing"));
        assertTrue(kits.delete("axe-default"));
        assertEquals("club-axe", kits.playableId("axe"));
        assertTrue(kits.delete("club-axe"));
        assertFalse(kits.isFolder("axe"));
        assertEquals("axe", kits.playableId("axe"));
        assertEquals(axe().items(), kits.get("axe").orElseThrow().items(),
                "original kit content is preserved when the last child leaves");
    }

    @Test
    void movingChildOutAndRenamingFolderNeverOrphansOtherChildren() {
        KitService kits = new KitService(new YamlConfiguration());
        kits.save(axe());
        kits.save(KitDefinition.simple("bow", "Bow", "BOW"));
        assertTrue(kits.setParent("bow", "axe"));
        assertEquals(KitService.RenameResult.OK, kits.rename("axe", "Hammers"));
        assertEquals("hammers", kits.get("bow").orElseThrow().parent());
        assertEquals("hammers", kits.get("axe-default").orElseThrow().parent());
        assertTrue(kits.setDefaultChild("hammers", "bow"));
        assertEquals(KitService.RenameResult.OK, kits.rename("bow", "Longbow"));
        assertEquals("longbow", kits.playableId("hammers"));
        assertTrue(kits.setParent("longbow", null));
        assertEquals("axe-default", kits.playableId("hammers"));
        assertTrue(kits.delete("hammers"));
        assertNull(kits.get("axe-default").orElseThrow().parent(),
                "deleting a folder promotes its children instead of deleting them");
        assertEquals(List.of("axe-default", "longbow"),
                kits.enabled().stream().map(KitDefinition::name).sorted().toList());
    }

    @Test
    void aFailedDataCopyDoesNotChangeTheOriginalKit() {
        KitService kits = new KitService(new YamlConfiguration());
        kits.save(axe());
        kits.setMigrationCallbacks((from, to) -> { throw new IllegalStateException("database down"); },
                (from, to) -> { });
        assertThrows(IllegalStateException.class, () -> kits.ensureFolder("axe"));
        assertFalse(kits.isFolder("axe"));
        assertTrue(kits.get("axe-default").isEmpty());
        assertEquals("axe", kits.playableId("axe"));
    }
    @Test
    void oldPresetsAreCarriedToOrdinaryChildrenOnceWithTheirPersonalLayouts() {
        YamlConfiguration yaml = new YamlConfiguration();
        KitService kits = new KitService(yaml);
        kits.save(axe());
        yaml.set("kits.axe.inner-kits.default.display-name", "Original [Default]");
        yaml.set("kits.axe.inner-kits.club.display-name", "Club Axe");
        yaml.set("kits.axe.inner-kits.club.icon", "IRON_AXE");
        InnerKitService legacy = new InnerKitService(yaml, null);
        List<String> copied = new ArrayList<>();
        kits.setMigrationCallbacks((from, to) -> copied.add(from + ">" + to),
                (from, to) -> copied.add("elo:" + from + ">" + to));

        assertEquals(2, legacy.migrateToChildKits(kits));
        assertNull(yaml.getConfigurationSection("kits.axe.inner-kits"));
        assertEquals("Original", kits.get("axe-default").orElseThrow().displayName());
        assertEquals(axe().items(), kits.get("axe-default").orElseThrow().items());
        KitDefinition club = kits.get("axe-club").orElseThrow();
        assertEquals("axe", club.parent());
        assertEquals("Club Axe", club.displayName());
        assertTrue(club.items().isEmpty(), "an empty old preset stays empty, not merged with the parent");
        assertEquals("IRON_AXE", club.icon());
        assertEquals(28, club.maxHealth());
        assertEquals(List.of("axe>axe-default", "elo:axe>axe-default",
                "axe#preset#club>axe-club"), copied);
        assertEquals(0, legacy.migrateToChildKits(kits));
        kits.reload();
        assertEquals("axe-default", kits.playableId("axe"));
        assertEquals(List.of("axe-default", "axe-club"),
                kits.children("axe").stream().map(KitDefinition::name).toList());
    }

    @Test
    void failedOldPresetMigrationRetainsTheSectionForSafeRetry() {
        YamlConfiguration yaml = new YamlConfiguration();
        KitService kits = new KitService(yaml);
        kits.save(axe());
        yaml.set("kits.axe.inner-kits.club.display-name", "Club Axe");
        InnerKitService legacy = new InnerKitService(yaml, null);
        kits.setMigrationCallbacks((from, to) -> {
            if (from.contains("#preset#")) throw new IllegalStateException("database down");
        }, (from, to) -> { });
        assertEquals(1, legacy.migrateToChildKits(kits)); // parent was safely copied already
        assertNotNull(yaml.getConfigurationSection("kits.axe.inner-kits"),
                "never remove the old preset until EVERY child and layout was copied");
        kits.setMigrationCallbacks((from, to) -> { }, (from, to) -> { });
        assertEquals(1, legacy.migrateToChildKits(kits)); // retries only the missing child
        assertNull(yaml.getConfigurationSection("kits.axe.inner-kits"));
        assertEquals(2, kits.children("axe").size());
    }

    @Test
    void adminSavingSharedContentsReplacesOnlyThatChildAndClearsOldArmorMap() {
        YamlConfiguration yaml = new YamlConfiguration();
        KitService kits = new KitService(yaml);
        kits.save(axe());
        kits.ensureFolder("axe").orElseThrow();
        assertNotNull(yaml.getConfigurationSection("kits.axe-default.armor"));
        assertTrue(kits.setOfficialLoadout("axe-default", new ItemStack[41]));
        assertTrue(kits.get("axe-default").orElseThrow().items().isEmpty());
        assertTrue(kits.get("axe-default").orElseThrow().armor().isEmpty());
        assertNull(yaml.getConfigurationSection("kits.axe-default.armor"));
        assertEquals(28, kits.get("axe-default").orElseThrow().maxHealth());
        assertEquals(axe().items(), kits.get("axe").orElseThrow().items());
        assertFalse(kits.setOfficialLoadout("axe", new ItemStack[41]),
                "a folder is a button, never the kit whose contents the admin is editing");
        kits.reload();
        assertTrue(kits.get("axe-default").orElseThrow().items().isEmpty());
        assertTrue(kits.get("axe-default").orElseThrow().armor().isEmpty());
    }

}
