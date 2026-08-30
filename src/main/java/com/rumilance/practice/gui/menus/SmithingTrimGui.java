package com.rumilance.practice.gui.menus;

import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiCloseHandler;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * VIP armor trim picker. Applies {@link ArmorTrim} to the held/slot armor piece.
 * VIP+ unlocks premium trim materials and the Silence / Snout patterns.
 */
public final class SmithingTrimGui extends AbstractGui implements GuiCloseHandler {

    private static final List<TrimMaterial> MATERIALS = List.of(
            TrimMaterial.AMETHYST, TrimMaterial.COPPER, TrimMaterial.DIAMOND, TrimMaterial.EMERALD,
            TrimMaterial.GOLD, TrimMaterial.IRON, TrimMaterial.LAPIS, TrimMaterial.NETHERITE,
            TrimMaterial.QUARTZ, TrimMaterial.REDSTONE
    );

    private static final List<TrimPattern> PATTERNS = List.of(
            TrimPattern.SENTRY, TrimPattern.DUNE, TrimPattern.COAST, TrimPattern.WILD,
            TrimPattern.WARD, TrimPattern.EYE, TrimPattern.VEX, TrimPattern.TIDE,
            TrimPattern.SNOUT, TrimPattern.RIB, TrimPattern.SPIRE, TrimPattern.WAYFINDER,
            TrimPattern.SHAPER, TrimPattern.SILENCE, TrimPattern.RAISER, TrimPattern.HOST,
            TrimPattern.FLOW, TrimPattern.BOLT
    );

    private final RankService rankService;
    private EditKitGui editKitGui;

    public SmithingTrimGui(GuiSessionRegistry registry, SoundService sounds, RankService rankService) {
        super(registry, sounds, GuiType.SMITHING_TRIM, 6, true);
        this.rankService = rankService;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    /**
     * Opens the trim picker for an armor piece already in the player's inventory.
     *
     * @param inventorySlot Bukkit player-inventory slot index (0-40), or -1 for main hand
     */
    public void openFor(Player player, ItemStack armor, int inventorySlot) {
        if (armor == null || !(armor.getItemMeta() instanceof ArmorMeta)) {
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("armor", armor.clone());
        session.put("inv_slot", inventorySlot);
        session.put("layout_slot", -1);
        String defaultMaterial = rankService.isVipPlusOrAbove(player) ? "gold" : "copper";
        session.put("trim_material", defaultMaterial);
        session.put("trim_pattern", "sentry");
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    /** Opens trim picker for an {@link EditKitGui} layout slot. */
    public void openForLayoutSlot(Player player, ItemStack armor, int layoutSlot,
                                  String kitId, String preset) {
        if (armor == null || !(armor.getItemMeta() instanceof ArmorMeta)) {
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("armor", armor.clone());
        session.put("layout_slot", layoutSlot);
        session.put("kit_id", kitId);
        session.put("preset", preset == null ? "" : preset);
        session.put("inv_slot", -1);
        String defaultMaterial = rankService.isVipPlusOrAbove(player) ? "gold" : "copper";
        session.put("trim_material", defaultMaterial);
        session.put("trim_pattern", "sentry");
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.trim-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        ItemStack armor = session.get("armor", ItemStack.class);
        if (armor == null) {
            MenuScaffold.closeButton(inventory);
            return;
        }
        int patIndex = 0;
        for (TrimPattern pattern : PATTERNS) {
            if (patIndex >= 18) {
                break;
            }
            int row = 1 + patIndex / 9;
            int col = patIndex % 9;
            String key = trimKey(pattern);
            boolean selected = key.equalsIgnoreCase(session.get("trim_pattern", String.class));
            boolean allowed = canUsePattern(player, key);
            inventory.setItem(GuiSlots.slot(row, col), patternIcon(player, pattern, selected, allowed));
            patIndex++;
        }

        inventory.setItem(GuiSlots.slot(3, 4), preview(player, armor, session));

        int matIndex = 0;
        for (TrimMaterial mat : MATERIALS) {
            String key = trimKey(mat);
            boolean selected = key.equalsIgnoreCase(session.get("trim_material", String.class));
            boolean allowed = canUseMaterial(player, key);
            ItemBuilder matBuilder = ItemBuilder.of(allowed ? materialIcon(mat) : Material.GRAY_DYE)
                    .name(Component.text(pretty(key), selected ? UiTheme.SUCCESS
                            : allowed ? UiTheme.VALUE : UiTheme.MUTED)
                            .decoration(TextDecoration.ITALIC, false))
                    .glint(selected && allowed)
                    .action(allowed ? "mat:" + key : "locked");
            if (!allowed) {
                matBuilder.lore(
                        UiTheme.line(line(player, "gui.trim-vip")),
                        UiTheme.hint(line(player, "gui.trim-vip-materials"))
                );
            }
            // 10 ores don't fit a single 9-wide chest row. Lay them out as one tight band:
            // the first 9 fill row 4, and the 10th sits at the start of row 5 — keeping all
            // materials together (no 5/5 split) and clear of the apply/back controls which
            // occupy the right of row 5.
            int slot = matIndex < 9
                    ? GuiSlots.slot(4, matIndex)
                    : GuiSlots.slot(5, 0);
            inventory.setItem(slot, matBuilder.build());
            matIndex++;
        }

        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.of(Material.SMITHING_TABLE)
                        .name(t(player, "gui.trim-apply").color(UiTheme.SUCCESS))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.trim-apply-lore")),
                                UiTheme.hint(line(player, "gui.trim-apply-hint"))
                        )
                        .action("apply")
                        .build());
        inventory.setItem(GuiSlots.slot(5, 8),
                ItemBuilder.action(UiTheme.BACK, t(player, "menu.back"), "back"));
    }

    private ItemStack patternIcon(Player player, TrimPattern pattern, boolean selected, boolean allowed) {
        String key = trimKey(pattern);
        Material icon = allowed ? patternTemplate(pattern) : Material.GRAY_DYE;
        ItemBuilder builder = ItemBuilder.of(icon)
                .name(Component.text(pretty(key), selected ? UiTheme.SUCCESS
                        : allowed ? UiTheme.VALUE : UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false))
                .glint(selected && allowed)
                .action(allowed ? "pat:" + key : "locked");
        if (!allowed) {
            builder.lore(
                    UiTheme.line(line(player, "gui.trim-vip")),
                    UiTheme.hint(line(player, "gui.trim-vip-patterns"))
            );
        }
        return builder.build();
    }

    private ItemStack preview(Player player, ItemStack armor, GuiSession session) {
        ItemStack copy = armor.clone();
        applyTrim(copy, session);
        annotateTrimmable(player, copy);
        if (copy.hasItemMeta()) {
            ItemMeta meta = copy.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(UiTheme.divider());
            if (meta.hasEnchants()) {
                meta.getEnchants().forEach((ench, level) ->
                        lore.add(Component.text(ench.getKey().getKey() + " " + level, UiTheme.SECONDARY)
                                .decoration(TextDecoration.ITALIC, false)));
            } else {
                lore.add(Component.text(line(player, "gui.no-enchants"), UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("locked".equals(action)) {
            sounds.play(player, "error");
            player.sendMessage(t(player, "gui.trim-locked"));
            return;
        }
        if (action.startsWith("mat:")) {
            String key = action.substring(4);
            if (!canUseMaterial(player, key)) {
                sounds.play(player, "error");
                player.sendMessage(t(player, "gui.trim-locked-material"));
                return;
            }
            session.put("trim_material", action.substring(4));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("pat:")) {
            String key = action.substring(4);
            if (!canUsePattern(player, key)) {
                sounds.play(player, "error");
                player.sendMessage(t(player, "gui.trim-locked-pattern"));
                return;
            }
            session.put("trim_pattern", action.substring(4));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("apply".equals(action)) {
            ItemStack armor = session.get("armor", ItemStack.class);
            Integer invSlot = session.get("inv_slot", Integer.class);
            if (armor == null) {
                return;
            }
            String materialKey = session.get("trim_material", String.class);
            String patternKey = session.get("trim_pattern", String.class);
            if (!PracticeGuards.trimSelectionAllowed(
                    rankService.isVipPlusOrAbove(player), materialKey, patternKey)) {
                sounds.play(player, "error");
                player.sendMessage(t(player, "gui.trim-locked-selection"));
                return;
            }
            ItemStack result = armor.clone();
            if (!applyTrim(result, session)) {
                sounds.play(player, "error");
                return;
            }
            Integer layoutSlot = session.get("layout_slot", Integer.class);
            if (layoutSlot != null && layoutSlot >= 0 && editKitGui != null) {
                String kitId = session.get("kit_id", String.class);
                String preset = session.get("preset", String.class);
                editKitGui.applyTrimmedItem(player, kitId, preset, layoutSlot, result);
            } else {
                writeBack(player, result, invSlot == null ? -1 : invSlot);
                player.closeInventory();
            }
            sounds.play(player, "select");
            player.sendMessage(t(player, "gui.trim-applied"));
        }
    }

    @Override
    public void onGuiClose(Player player, GuiSession session, Inventory top,
                           org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        if (reason == org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW) {
            return;
        }
        Integer layoutSlot = session.get("layout_slot", Integer.class);
        if (layoutSlot != null && layoutSlot >= 0 && editKitGui != null) {
            org.bukkit.plugin.Plugin plugin =
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(SmithingTrimGui.class);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    editKitGui.reopenFromStash(player);
                }
            });
        }
    }

    private static void writeBack(Player player, ItemStack result, int invSlot) {
        PlayerInventory inv = player.getInventory();
        if (invSlot >= 0 && invSlot < inv.getSize()) {
            inv.setItem(invSlot, result);
            return;
        }
        ItemStack hand = inv.getItemInMainHand();
        if (hand.getItemMeta() instanceof ArmorMeta) {
            inv.setItemInMainHand(result);
            return;
        }
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack worn = inv.getItem(slot);
            if (worn != null && worn.getType() == result.getType()
                    && worn.getItemMeta() instanceof ArmorMeta) {
                inv.setItem(slot, result);
                return;
            }
        }
        inv.setItemInMainHand(result);
    }

    private static boolean applyTrim(ItemStack stack, GuiSession session) {
        if (!(stack.getItemMeta() instanceof ArmorMeta meta)) {
            return false;
        }
        TrimMaterial material = resolveMaterial(session.get("trim_material", String.class));
        TrimPattern pattern = resolvePattern(session.get("trim_pattern", String.class));
        if (material == null || pattern == null) {
            return false;
        }
        meta.setTrim(new ArmorTrim(material, pattern));
        stack.setItemMeta(meta);
        return true;
    }

    public void annotateTrimmable(Player player, ItemStack stack) {
        if (!(stack.getItemMeta() instanceof ArmorMeta meta)) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        var noteKey = com.rumilance.practice.util.ItemKeys.key("trim_note");
        if (pdc.has(noteKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            return;
        }
        pdc.set(noteKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(t(player, "gui.trim-trimmable").color(NamedTextColor.DARK_AQUA));
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    private boolean canUseMaterial(Player player, String key) {
        return PracticeGuards.trimMaterialAllowed(rankService.isVipPlusOrAbove(player), key);
    }

    private boolean canUsePattern(Player player, String key) {
        return PracticeGuards.trimPatternAllowed(rankService.isVipPlusOrAbove(player), key);
    }

    private static TrimMaterial resolveMaterial(String key) {
        if (key == null) {
            return TrimMaterial.GOLD;
        }
        String id = key.toLowerCase(Locale.ROOT);
        for (TrimMaterial mat : MATERIALS) {
            if (trimKey(mat).equals(id)) {
                return mat;
            }
        }
        return Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft(id));
    }

    private static TrimPattern resolvePattern(String key) {
        if (key == null) {
            return TrimPattern.SENTRY;
        }
        String id = key.toLowerCase(Locale.ROOT);
        for (TrimPattern pattern : PATTERNS) {
            if (trimKey(pattern).equals(id)) {
                return pattern;
            }
        }
        return Registry.TRIM_PATTERN.get(org.bukkit.NamespacedKey.minecraft(id));
    }

    private static String trimKey(TrimMaterial mat) {
        var key = Registry.TRIM_MATERIAL.getKey(mat);
        return key == null ? "gold" : key.getKey();
    }

    private static String trimKey(TrimPattern pattern) {
        var key = Registry.TRIM_PATTERN.getKey(pattern);
        return key == null ? "sentry" : key.getKey();
    }

    private static String pretty(String key) {
        if (key == null || key.isBlank()) {
            return "?";
        }
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static Material patternTemplate(TrimPattern pattern) {
        String key = trimKey(pattern);
        Material matched = Material.matchMaterial(key + "_armor_trim_smithing_template");
        return matched != null ? matched : Material.SMITHING_TABLE;
    }

    private static Material materialIcon(TrimMaterial mat) {
        String key = trimKey(mat);
        return switch (key) {
            case "amethyst" -> Material.AMETHYST_SHARD;
            case "copper" -> Material.COPPER_INGOT;
            case "diamond" -> Material.DIAMOND;
            case "emerald" -> Material.EMERALD;
            case "gold" -> Material.GOLD_INGOT;
            case "iron" -> Material.IRON_INGOT;
            case "lapis" -> Material.LAPIS_LAZULI;
            case "netherite" -> Material.NETHERITE_INGOT;
            case "quartz" -> Material.QUARTZ;
            case "redstone" -> Material.REDSTONE;
            case "resin" -> Material.RESIN_BRICK;
            default -> Material.GOLD_INGOT;
        };
    }
}
