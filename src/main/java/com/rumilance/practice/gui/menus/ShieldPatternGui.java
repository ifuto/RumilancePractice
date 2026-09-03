package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiCloseHandler;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * VIP+ shield banner-pattern editor, opened by right-clicking a shield in the kit editor.
 * Mirrors the loom: pick a dye, click pattern tiles to stack up to 6 layers, undo/reset as
 * needed. The GUI stocks real dye items and the special banner-pattern items, and the preview
 * shield updates live. Saving writes the patterns onto the kit's shield.
 */
public final class ShieldPatternGui extends AbstractGui implements GuiCloseHandler {

    private static final int MAX_LAYERS = 6;
    private static final int PATTERNS_PER_PAGE = 27;

    /** Every usable pattern type in a stable, display-friendly order (1.21+ constant names). */
    private static final List<PatternType> PATTERNS = List.of(
            PatternType.BASE,
            PatternType.SQUARE_BOTTOM_LEFT, PatternType.SQUARE_BOTTOM_RIGHT,
            PatternType.SQUARE_TOP_LEFT, PatternType.SQUARE_TOP_RIGHT,
            PatternType.STRIPE_BOTTOM, PatternType.STRIPE_TOP,
            PatternType.STRIPE_LEFT, PatternType.STRIPE_RIGHT,
            PatternType.STRIPE_CENTER, PatternType.STRIPE_MIDDLE,
            PatternType.STRIPE_DOWNRIGHT, PatternType.STRIPE_DOWNLEFT,
            PatternType.SMALL_STRIPES, PatternType.CROSS, PatternType.STRAIGHT_CROSS,
            PatternType.TRIANGLE_BOTTOM, PatternType.TRIANGLE_TOP,
            PatternType.TRIANGLES_BOTTOM, PatternType.TRIANGLES_TOP,
            PatternType.DIAGONAL_LEFT, PatternType.DIAGONAL_RIGHT,
            PatternType.DIAGONAL_UP_LEFT, PatternType.DIAGONAL_UP_RIGHT,
            PatternType.CIRCLE, PatternType.RHOMBUS,
            PatternType.HALF_VERTICAL, PatternType.HALF_HORIZONTAL,
            PatternType.HALF_VERTICAL_RIGHT, PatternType.HALF_HORIZONTAL_BOTTOM,
            PatternType.BORDER, PatternType.CURLY_BORDER,
            PatternType.GRADIENT, PatternType.GRADIENT_UP, PatternType.BRICKS,
            PatternType.CREEPER, PatternType.SKULL, PatternType.FLOWER, PatternType.MOJANG,
            PatternType.GLOBE, PatternType.PIGLIN, PatternType.FLOW, PatternType.GUSTER
    );

    private static final DyeColor[] DYES = {
            DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK,
            DyeColor.BROWN, DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW,
            DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
            DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK
    };

    /** Banner-pattern items that vanilla gives special patterns. */
    private static final Map<PatternType, Material> PATTERN_ITEMS = Map.ofEntries(
            Map.entry(PatternType.CREEPER, Material.CREEPER_BANNER_PATTERN),
            Map.entry(PatternType.SKULL, Material.SKULL_BANNER_PATTERN),
            Map.entry(PatternType.FLOWER, Material.FLOWER_BANNER_PATTERN),
            Map.entry(PatternType.MOJANG, Material.MOJANG_BANNER_PATTERN),
            Map.entry(PatternType.GLOBE, Material.GLOBE_BANNER_PATTERN),
            Map.entry(PatternType.PIGLIN, Material.PIGLIN_BANNER_PATTERN),
            Map.entry(PatternType.FLOW, Material.FLOW_BANNER_PATTERN),
            Map.entry(PatternType.GUSTER, Material.GUSTER_BANNER_PATTERN)
    );

    private EditKitGui editKitGui;

    public ShieldPatternGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.SHIELD_PATTERN, 6, true);
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    /** Opens the editor for a shield living in an EditKitGui layout slot. */
    public void openForLayoutSlot(Player player, ItemStack shield, int layoutSlot,
                                  String kitId, String preset, ItemStack[] layoutSnapshot) {
        if (shield == null || shield.getType() != Material.SHIELD) {
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("shield", shield.clone());
        session.put("layout_slot", layoutSlot);
        session.put("kit_id", kitId);
        session.put("preset", preset == null ? "" : preset);
        session.put("layout", layoutSnapshot == null ? new ItemStack[0] : layoutSnapshot.clone());
        session.put("dye", DyeColor.RED.name());
        session.put("page", 0);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Shield Patterns", NamedTextColor.GOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        DyeColor dye = dye(session);
        List<Pattern> layers = layers(session);

        // Row 0 — live preview + editing controls around the header.
        inventory.setItem(GuiSlots.slot(0, 1), previewShield(session, layers));
        inventory.setItem(GuiSlots.slot(0, 2), ItemBuilder.of(Material.ARROW)
                .name(Component.text("Undo last layer", NamedTextColor.YELLOW))
                .lore(UiTheme.hint("Click: remove the newest layer"))
                .action("undo")
                .build());
        inventory.setItem(GuiSlots.slot(0, 3), ItemBuilder.of(Material.LAVA_BUCKET)
                .name(Component.text("Reset shield", NamedTextColor.RED))
                .lore(UiTheme.hint("Click: remove ALL layers"))
                .action("reset")
                .build());
        inventory.setItem(GuiSlots.slot(0, 8), ItemBuilder.of(Material.PAPER)
                .name(Component.text("Layers: " + layers.size() + "/" + MAX_LAYERS,
                        layers.size() >= MAX_LAYERS ? NamedTextColor.RED : NamedTextColor.GREEN))
                .action("layers")
                .build());

        // Rows 1-3 — pattern tiles (paged).
        int page = session.get("page", Integer.class) == null ? 0 : session.get("page", Integer.class);
        int start = page * PATTERNS_PER_PAGE;
        int row = 1;
        int col = 1;
        for (int i = start; i < Math.min(start + PATTERNS_PER_PAGE, PATTERNS.size()); i++) {
            inventory.setItem(GuiSlots.slot(row, col), patternTile(PATTERNS.get(i), dye));
            col++;
            if (col > 9) {
                col = 1;
                row++;
            }
        }
        // Page arrows (only when there are 2+ pages).
        if (PATTERNS.size() > PATTERNS_PER_PAGE) {
            inventory.setItem(GuiSlots.slot(0, 7), ItemBuilder.of(Material.SPECTRAL_ARROW)
                    .name(Component.text("Previous page", NamedTextColor.GRAY))
                    .action("page:" + Math.max(0, page - 1))
                    .build());
            inventory.setItem(GuiSlots.slot(0, 9), ItemBuilder.of(Material.SPECTRAL_ARROW)
                    .name(Component.text("Next page", NamedTextColor.GRAY))
                    .action("page:" + Math.min(1, page + 1))
                    .build());
        }

        // Rows 4-5 — the dye palette (real dye items).
        int idx = 0;
        for (int c = 1; c <= 9 && idx < DYES.length; c++, idx++) {
            inventory.setItem(GuiSlots.slot(4, c), dyeItem(DYES[idx], dye));
        }
        for (int c = 2; c <= 8 && idx < DYES.length; c++, idx++) {
            inventory.setItem(GuiSlots.slot(5, c), dyeItem(DYES[idx], dye));
        }

        MenuScaffold.closeButton(inventory, Component.text("Save & Close"));
    }

    private ItemStack patternTile(PatternType patternType, DyeColor dye) {
        Material special = PATTERN_ITEMS.get(patternType);
        String pretty = prettyName(patternType);
        ItemBuilder b;
        if (special != null) {
            // Special patterns ship as their real banner-pattern item.
            b = ItemBuilder.of(special);
        } else if (patternType == PatternType.BASE) {
            b = ItemBuilder.of(dyeMaterial(dye));
        } else {
            // Regular patterns render as a banner showing the shape in the selected dye.
            ItemStack banner = new ItemStack(Material.WHITE_BANNER);
            if (banner.getItemMeta() instanceof BannerMeta bannerMeta) {
                bannerMeta.setPatterns(List.of(new Pattern(patternType, dye)));
                banner.setItemMeta(bannerMeta);
            }
            b = ItemBuilder.of(banner);
        }
        return b.name(Component.text(pretty, NamedTextColor.AQUA))
                .lore(UiTheme.blank(),
                        UiTheme.hint("Click: add as new layer (" + prettyName(dye) + ")"))
                .action("pattern:" + patternType.name())
                .build();
    }

    private ItemStack dyeItem(DyeColor color, DyeColor selected) {
        return ItemBuilder.of(dyeMaterial(color))
                .name(Component.text(prettyName(color), color == selected
                        ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .glintIf(color == selected)
                .lore(UiTheme.hint(color == selected ? "Selected" : "Click: select color"))
                .action("dye:" + color.name())
                .build();
    }

    private ItemStack previewShield(GuiSession session, List<Pattern> layers) {
        ItemStack shield = ((ItemStack) session.get("shield", ItemStack.class)).clone();
        if (shield.getItemMeta() instanceof BannerMeta bannerMeta) {
            bannerMeta.setPatterns(layers);
            shield.setItemMeta(bannerMeta);
        }
        return ItemBuilder.of(shield)
                .name(Component.text("Preview", NamedTextColor.GOLD))
                .glintIf(!layers.isEmpty())
                .action("preview")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType clickType) {
        if (action.startsWith("page:")) {
            session.put("page", Integer.parseInt(action.substring(5)));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("dye:")) {
            session.put("dye", action.substring(4));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("pattern:")) {
            List<Pattern> layers = layers(session);
            if (layers.size() >= MAX_LAYERS) {
                sounds.play(player, "error");
                player.sendMessage(Component.text("A shield holds at most " + MAX_LAYERS
                        + " pattern layers.", NamedTextColor.RED));
                return;
            }
            PatternType patternType = PatternType.valueOf(action.substring(8));
            layers.add(new Pattern(patternType, dye(session)));
            session.put("layers", new java.util.ArrayList<>(layers));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("undo".equals(action)) {
            List<Pattern> layers = layers(session);
            if (!layers.isEmpty()) {
                layers.remove(layers.size() - 1);
                session.put("layers", new java.util.ArrayList<>(layers));
            }
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("reset".equals(action)) {
            session.put("layers", new java.util.ArrayList<Pattern>());
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            saveAndReturn(player, session);
        }
    }

    @Override
    public void onGuiClose(Player player, GuiSession session, Inventory top,
                           org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        // Switching GUIs (e.g. back into the editor) handles its own reopen; any real close
        // saves the shield first — it is the player's own kit item.
        if (reason == org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW) {
            return;
        }
        if (player.isOnline()) {
            saveAndReturn(player, session);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Pattern> layers(GuiSession session) {
        List<Pattern> stored = (List<Pattern>) session.get("layers", List.class);
        return stored == null ? new ArrayList<>() : new ArrayList<>(stored);
    }

    private DyeColor dye(GuiSession session) {
        String name = session.get("dye", String.class);
        try {
            return name == null ? DyeColor.RED : DyeColor.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DyeColor.RED;
        }
    }

    private void saveAndReturn(Player player, GuiSession session) {
        ItemStack shield = session.get("shield", ItemStack.class);
        int layoutSlot = session.get("layout_slot", Integer.class) == null
                ? -1 : session.get("layout_slot", Integer.class);
        String kitId = session.get("kit_id", String.class);
        String preset = session.get("preset", String.class);
        if (shield != null && shield.getItemMeta() instanceof BannerMeta bannerMeta) {
            bannerMeta.setPatterns(layers(session));
            shield.setItemMeta(bannerMeta);
        }
        if (editKitGui == null || kitId == null || layoutSlot < 0) {
            return;
        }
        final ItemStack saved = shield;
        // Defer one tick: the reopen may run inside an inventory-close event where opening a
        // new GUI directly is rejected by the client.
        org.bukkit.plugin.Plugin plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(ShieldPatternGui.class);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                editKitGui.applyRenamedItem(player, kitId, preset, layoutSlot, saved, null);
            }
        });
    }

    private static Material dyeMaterial(DyeColor color) {
        return switch (color) {
            case WHITE -> Material.WHITE_DYE;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_DYE;
            case GRAY -> Material.GRAY_DYE;
            case BLACK -> Material.BLACK_DYE;
            case BROWN -> Material.BROWN_DYE;
            case RED -> Material.RED_DYE;
            case ORANGE -> Material.ORANGE_DYE;
            case YELLOW -> Material.YELLOW_DYE;
            case LIME -> Material.LIME_DYE;
            case GREEN -> Material.GREEN_DYE;
            case CYAN -> Material.CYAN_DYE;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_DYE;
            case BLUE -> Material.BLUE_DYE;
            case PURPLE -> Material.PURPLE_DYE;
            case MAGENTA -> Material.MAGENTA_DYE;
            case PINK -> Material.PINK_DYE;
        };
    }

    private static String prettyName(Object enumValue) {
        String name = ((Enum<?>) enumValue).name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(name.length());
        boolean upper = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                out.append(' ');
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }
}
