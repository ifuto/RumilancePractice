package com.rumilance.practice.command;

import com.rumilance.practice.testarena.SmoothTerrainGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Admin entry point and settings menu for the generated smooth terrain test maps.
 *
 * <p>Several temporary maps may coexist: spawning a new one no longer requires deleting the
 * previous one. Each map keyed by a short id, so it can be removed individually
 * ({@code /testarena delete <id>}), in bulk ({@code /testarena delete all}) or listed. The map
 * side length is selectable both from the menu and from the {@code /testarena spawn} command.</p>
 */
public final class TestArenaCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int GRASS_SLOT = 10;
    private static final int SAND_SLOT = 12;
    private static final int RED_SAND_SLOT = 14;
    private static final int RANDOM_SLOT = 19;
    private static final int BOWL_SLOT = 21;
    private static final int UNDERGROUND_SLOT = 28;
    private static final int SURFACE_ONLY_SLOT = 30;
    private static final int HEIGHT_ZERO_SLOT = 37;
    private static final int HEIGHT_TWO_SLOT = 39;
    private static final int HEIGHT_FOUR_SLOT = 41;
    private static final int SIZE_32_SLOT = 3;
    private static final int SIZE_64_SLOT = 4;
    private static final int SIZE_100_SLOT = 5;
    private static final int SIZE_CUSTOM_SLOT = 6;
    /** Layer width (fineness) button — sits next to the shape row. */
    private static final int FINENESS_SLOT = 23;
    /** Preset cell widths cycled by left-clicking the fineness button. */
    private static final List<Integer> FINENESS_PRESETS = List.of(20, 10, 5, 40);
    private static final int START_SLOT = 49;

    private final SmoothTerrainGenerator generator;
    private final com.rumilance.practice.testarena.SideLengthAnvilService anvilService;

    public TestArenaCommand(SmoothTerrainGenerator generator) {
        this(generator, null);
    }

    public TestArenaCommand(SmoothTerrainGenerator generator,
                            com.rumilance.practice.testarena.SideLengthAnvilService anvilService) {
        this.generator = generator;
        this.anvilService = anvilService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only: run /testarena spawn to create a temporary test map.");
            return true;
        }
        int size = defaultSideLength();
        if (args.length == 0) {
            openSettingsMenu(player, size);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "cancel" -> {
                generator.cancel(player.getUniqueId());
                player.sendMessage(Component.text("Test map operation cancelled.", NamedTextColor.YELLOW));
                return true;
            }
            case "list" -> {
                listMaps(player);
                return true;
            }
            case "delete" -> {
                delete(player, args);
                return true;
            }
            case "spawn", "spawnde", "create" -> {
                spawn(player, args);
                return true;
            }
            default -> {
                player.sendMessage(Component.text(
                        "/testarena spawn [size] [map] [f<layer width>] | " +
                        "/testarena delete [id|all] | /testarena list | /testarena cancel",
                        NamedTextColor.YELLOW));
                return true;
            }
        }
    }

    private static int defaultSideLength() {
        return SmoothTerrainGenerator.WIDTH;
    }

    private static Integer parseSize(String raw) {
        if (raw == null) {
            return null;
        }
        int size;
        try {
            size = Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
        // No size limit: any positive side length is accepted (0/negative means "invalid").
        return size >= 1 ? size : null;
    }

    private void spawn(Player player, String[] args) {
        int size = defaultSideLength();
        int fineness = 0; // 0 = default cell width
        String mapKey = null;
        for (int i = 1; i < args.length; i++) {
            String raw = args[i];
            SmoothTerrainGenerator.TerrainMap parsed = SmoothTerrainGenerator.TerrainMap.parse(raw);
            if (parsed != null) {
                mapKey = raw;
                continue;
            }
            Integer parsedSize = parseSize(raw);
            if (parsedSize != null) {
                size = parsedSize;
                continue;
            }
            Integer parsedFineness = parseFineness(raw);
            if (parsedFineness != null) {
                fineness = parsedFineness;
                continue;
            }
            player.sendMessage(Component.text(
                    "Unknown argument '" + raw + "'. Use a positive side length, a map" +
                            " (grass-stone, sand-sandstone, red-sand-red-sandstone) and/or a" +
                            " layer width (f5, f10, f20, f40, ...).",
                    NamedTextColor.RED));
            return;
        }
        if (args.length == 1) {
            // Bare /testarena spawn: open the two-stage settings menu.
            openSettingsMenu(player, size);
            return;
        }
        SmoothTerrainGenerator.TerrainMap map = mapKey == null
                ? SmoothTerrainGenerator.TerrainMap.GRASS_STONE
                : SmoothTerrainGenerator.TerrainMap.parse(mapKey);
        start(player, new SmoothTerrainGenerator.TerrainSettings(
                map, size, SmoothTerrainGenerator.TerrainShape.RANDOM, false,
                SmoothTerrainGenerator.MAX_HEIGHT_DELTA, fineness));
    }

    /** Layer width token: {@code f<N>} / {@code spacing<N>} / {@code spacing=<N>}, N >= 1. */
    private static Integer parseFineness(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        String digits;
        if (lowered.startsWith("f") && lowered.length() > 1) {
            digits = lowered.substring(1);
        } else if (lowered.startsWith("spacing=")) {
            digits = lowered.substring("spacing=".length());
        } else if (lowered.startsWith("spacing")) {
            digits = lowered.substring("spacing".length());
        } else {
            return null;
        }
        try {
            int value = Integer.parseInt(digits);
            return value >= 1 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void start(Player player, SmoothTerrainGenerator.TerrainSettings settings) {
        if (generator.isRunning(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "A test map operation is already running for you.", NamedTextColor.YELLOW));
            return;
        }
        int size = settings.sideLength();
        String foundation = settings.surfaceOnly() ? "surface-only (2 underground layers)" :
                "200 underground layers";
        String fineness = settings.controlSpacing() == SmoothTerrainGenerator.CONTROL_SPACING
                ? ""
                : String.format(", layer width %d", settings.controlSpacing());
        player.sendMessage(Component.text(String.format(
                        "Planning a %dx%d smooth %s terrain with %s%s, %s, max height difference %d. "
                                + "Block placement is batched to protect TPS...",
                        size, size, settings.shape().label().toLowerCase(Locale.ROOT),
                        settings.map().displayName(), fineness, foundation, settings.maxHeightDelta()),
                NamedTextColor.AQUA));
        long seed = ThreadLocalRandom.current().nextLong();
        generator.generate(player, settings, seed, result -> player.sendMessage(Component.text(
                "Test map ready: " + result.map().displayName() + ", " + result.width() + "x"
                        + result.width() + ", " + foundation + " + bedrock, height " + result.minimumHeight() + ".."
                        + result.maximumHeight() + " (range " + result.heightRange() + "), seed "
                        + result.seed() + ". Use /testarena delete to remove it.",
                NamedTextColor.GREEN)),
                error -> player.sendMessage(Component.text(error, NamedTextColor.RED)));
    }

    private void delete(Player player, String[] args) {
        String target = args.length > 1 ? args[1] : null;
        if (target != null && target.equalsIgnoreCase("all")) {
            target = null;
        }
        generator.delete(player, target,
                columns -> player.sendMessage(Component.text(
                        "Deleted test map(s) (" + columns + " columns).", NamedTextColor.GREEN)),
                error -> player.sendMessage(Component.text(error, NamedTextColor.RED)));
    }

    private void listMaps(Player player) {
        var maps = generator.maps();
        if (maps.isEmpty()) {
            player.sendMessage(Component.text("No test maps yet. Create one with /testarena spawn.",
                    NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("Test maps (" + maps.size() + "):", NamedTextColor.AQUA));
        for (SmoothTerrainGenerator.Area area : maps.values()) {
            player.sendMessage(Component.text("  " + area.id() + "  " + area.width() + "x"
                    + area.width() + "  " + area.settings().map().displayName() + "  @ "
                    + area.centerX() + "," + area.centerZ(), NamedTextColor.GRAY));
        }
    }

    private void openSettingsMenu(Player player, int size) {
        openSettingsMenu(player, size, 0);
    }

    /** {@code fineness >= 1} seeds the layer width (the {@code f<N>} spawn argument). */
    private void openSettingsMenu(Player player, int size, int fineness) {
        MapMenuHolder holder = new MapMenuHolder();
        holder.size = Math.max(1, size);
        if (fineness >= 1) {
            holder.controlSpacing = fineness;
        }
        openSettingsMenu(player, holder);
    }

    /** Opens a fresh inventory bound to {@code holder}, preserving its existing selections. */
    private void openSettingsMenu(Player player, MapMenuHolder holder) {
        if (player == null || !player.isOnline()) {
            return;
        }
        holder.size = Math.max(1, holder.size);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("TestArena: map settings", NamedTextColor.DARK_AQUA));
        holder.bind(inventory);
        render(holder);
        player.openInventory(inventory);
    }

    private static void render(MapMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        inventory.setItem(4, item(Material.NETHER_STAR, "TestArena settings",
                holder.size + " x " + holder.size + " surface, automatically smoothed",
                "Choose material, shape, underground, height and size."));

        inventory.setItem(GRASS_SLOT, item(Material.GRASS_BLOCK,
                holder.map == SmoothTerrainGenerator.TerrainMap.GRASS_STONE, "Grass / Stone",
                "Surface: grass block", "Layers 2-3: dirt", "Deeper foundation: stone"));
        inventory.setItem(SAND_SLOT, item(Material.SAND,
                holder.map == SmoothTerrainGenerator.TerrainMap.SAND_SANDSTONE, "Sand / Sandstone",
                "Surface: sand", "Layers 1-4: sand", "Deeper foundation: sandstone"));
        inventory.setItem(RED_SAND_SLOT, item(Material.RED_SAND,
                holder.map == SmoothTerrainGenerator.TerrainMap.RED_SAND_RED_SANDSTONE,
                        "Red Sand / Red Sandstone",
                "Layers 1-3: red sand", "Deeper surface: red sandstone"));

        inventory.setItem(RANDOM_SLOT, item(Material.WHEAT_SEEDS,
                holder.shape == SmoothTerrainGenerator.TerrainShape.RANDOM, "Smooth random shape",
                "Shape: smooth random height map"));
        inventory.setItem(BOWL_SLOT, item(Material.BOWL,
                holder.shape == SmoothTerrainGenerator.TerrainShape.CENTER_LOW, "Centre-low bowl",
                "Shape: centre gently slopes down", "Independent from the material choice"));
        inventory.setItem(FINENESS_SLOT,
                holder.shape == SmoothTerrainGenerator.TerrainShape.CENTER_LOW
                        ? item(Material.GRAY_DYE, "Layer width: n/a (bowl)",
                                "The centre-low bowl ignores the layer width.",
                                "Pick the random shape to set it.")
                        : finenessButton(holder.controlSpacing));

        inventory.setItem(UNDERGROUND_SLOT, item(Material.STONE,
                !holder.surfaceOnly, "Underground: 200 blocks",
                "Surface to bedrock: up to 200 blocks", "Bedrock is kept above world minimum Y"));
        inventory.setItem(SURFACE_ONLY_SLOT, item(Material.GLASS,
                holder.surfaceOnly, "Surface only",
                "Still creates at least 2 underground layers", "Then one safe bedrock layer"));

        inventory.setItem(HEIGHT_ZERO_SLOT, item(Material.SNOWBALL,
                holder.maxHeightDelta == 0, "Max height difference: 0",
                "Flat surface"));
        inventory.setItem(HEIGHT_TWO_SLOT, item(Material.SNOW_BLOCK,
                holder.maxHeightDelta == 2, "Max height difference: 2",
                "Gentle terrain"));
        inventory.setItem(HEIGHT_FOUR_SLOT, item(Material.COBBLESTONE,
                holder.maxHeightDelta == 4, "Max height difference: 4",
                "Maximum smooth variation"));

        inventory.setItem(SIZE_32_SLOT, item(Material.BRICKS,
                holder.size == 32, "Side length: 32",
                "32 x 32 blocks"));
        inventory.setItem(SIZE_64_SLOT, item(Material.STONE_BRICKS,
                holder.size == 64, "Side length: 64",
                "64 x 64 blocks"));
        inventory.setItem(SIZE_100_SLOT, item(Material.DEEPSLATE_BRICKS,
                holder.size == 100, "Side length: 100",
                "100 x 100 blocks (default)"));

        // Custom side length: the anvil prompt. No size limit anymore — any positive number
        // of blocks goes (large maps simply take longer / more memory to generate).
        inventory.setItem(SIZE_CUSTOM_SLOT, item(Material.NAME_TAG,
                holder.size != 32 && holder.size != 64 && holder.size != 100,
                "Side length: " + holder.size,
                "Enter any custom side length (no limit)", "Opens the anvil naming dialog"));

        inventory.setItem(START_SLOT, item(Material.EMERALD_BLOCK, "Generate test map",
                "Click to create the selected " + holder.size + " x " + holder.size + " map",
                holder.shape == SmoothTerrainGenerator.TerrainShape.CENTER_LOW
                        ? "Layer width: n/a (bowl shape)"
                        : "Layer width: " + holder.controlSpacing));
        inventory.setItem(53, item(Material.BARRIER, "Close", "No map will be created."));
    }

    /** Menu button; {@code selected} buttons glow (enchantment glint) on top of the name marker. */
    private static ItemStack item(Material material, boolean selected, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(selected ? "Selected: " + name : name, NamedTextColor.AQUA));
        meta.lore(java.util.Arrays.stream(lore)
                .map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** The layer-width picker tile: current value, presets, shift-click = any number. */
    private static ItemStack finenessButton(int spacing) {
        return item(Material.SCAFFOLDING,
                spacing != SmoothTerrainGenerator.CONTROL_SPACING,
                "Layer width (fineness): " + spacing,
                "Width of ONE random terrain cell",
                "5 = fine bumps · 10 = fine · 20 = default · 40 = broad",
                "Click: cycle presets · Shift-click: any number (anvil)",
                "Huge maps widen tiny cells automatically (memory guard)");
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.lore(java.util.Arrays.stream(lore)
                .map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MapMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        switch (event.getRawSlot()) {
            case GRASS_SLOT -> holder.map = SmoothTerrainGenerator.TerrainMap.GRASS_STONE;
            case SAND_SLOT -> holder.map = SmoothTerrainGenerator.TerrainMap.SAND_SANDSTONE;
            case RED_SAND_SLOT -> holder.map = SmoothTerrainGenerator.TerrainMap.RED_SAND_RED_SANDSTONE;
            case RANDOM_SLOT -> holder.shape = SmoothTerrainGenerator.TerrainShape.RANDOM;
            case BOWL_SLOT -> holder.shape = SmoothTerrainGenerator.TerrainShape.CENTER_LOW;
            case FINENESS_SLOT -> {
                // 器型 (centre-low bowl) はセル幅を使わない: 押しても何も起きない。
                if (holder.shape == SmoothTerrainGenerator.TerrainShape.CENTER_LOW) {
                    return;
                }
                if (event.isShiftClick()) {
                    if (anvilService != null) {
                        int current = holder.controlSpacing;
                        com.rumilance.practice.testarena.SideLengthAnvilService.OpenResult opened =
                                anvilService.open(player, current,
                                        (p, parsed) -> {
                                            holder.controlSpacing = Math.max(1, parsed);
                                            openSettingsAndReopen(p, holder);
                                        },
                                        (p, raw) -> openSettingsAndReopen(p, holder),
                                        p -> openSettingsAndReopen(p, holder));
                        if (opened == com.rumilance.practice.testarena.SideLengthAnvilService.OpenResult.BUSY) {
                            player.sendMessage(Component.text(
                                    "A number prompt is already open.", NamedTextColor.YELLOW));
                        }
                    } else {
                        player.sendMessage(Component.text(
                                "Custom layer width is unavailable.", NamedTextColor.RED));
                    }
                    return;
                }
                // Cycle the presets; an off-preset (custom) value restarts from the default.
                int index = FINENESS_PRESETS.indexOf(holder.controlSpacing);
                holder.controlSpacing = FINENESS_PRESETS.get(
                        index < 0 ? 0 : (index + 1) % FINENESS_PRESETS.size());
            }
            case UNDERGROUND_SLOT -> holder.surfaceOnly = false;
            case SURFACE_ONLY_SLOT -> holder.surfaceOnly = true;
            case HEIGHT_ZERO_SLOT -> holder.maxHeightDelta = 0;
            case HEIGHT_TWO_SLOT -> holder.maxHeightDelta = 2;
            case HEIGHT_FOUR_SLOT -> holder.maxHeightDelta = 4;
            case SIZE_32_SLOT -> holder.size = 32;
            case SIZE_64_SLOT -> holder.size = 64;
            case SIZE_100_SLOT -> holder.size = 100;
            case SIZE_CUSTOM_SLOT -> {
                if (anvilService != null) {
                    int current = holder.size;
                    com.rumilance.practice.testarena.SideLengthAnvilService.OpenResult opened =
                            anvilService.open(player, current,
                                    (p, parsed) -> reapplySettings(player, holder, parsed),
                                    (p, raw) -> openSettingsAndReopen(p, holder),
                                    p -> openSettingsAndReopen(p, holder));
                    if (opened == com.rumilance.practice.testarena.SideLengthAnvilService.OpenResult.BUSY) {
                        player.sendMessage(Component.text(
                                "A side length prompt is already open.", NamedTextColor.YELLOW));
                    }
                } else {
                    player.sendMessage(Component.text(
                            "Custom side length is unavailable.", NamedTextColor.RED));
                }
                return;
            }
            case START_SLOT -> {
                player.closeInventory();
                SmoothTerrainGenerator.TerrainMap map = holder.map;
                boolean surfaceOnly = holder.surfaceOnly;
                int maxHeightDelta = holder.maxHeightDelta;
                int side = holder.size;
                player.sendMessage(Component.text("Generating " + side + "x" + side + " test map...",
                        NamedTextColor.AQUA));
                start(player, new SmoothTerrainGenerator.TerrainSettings(
                        map, side, holder.shape, surfaceOnly, maxHeightDelta,
                        holder.controlSpacing));
                return;
            }
            case 53 -> {
                player.closeInventory();
                return;
            }
            default -> { return; }
        }
        render(holder);
    }

    private void reapplySettings(Player player, MapMenuHolder holder, int parsed) {
        holder.size = Math.max(1, parsed);
        openSettingsAndReopen(player, holder);
    }

    /** Reopens the settings menu (the anvil takes the inventory screen). */
    private void openSettingsAndReopen(Player player, MapMenuHolder holder) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TestArenaCommand.class),
                () -> {
                    if (player.isOnline()) {
                        openSettingsMenu(player, holder);
                    }
                });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            return TabCompletions.filter(args[0], "spawn", "create", "delete", "list", "cancel");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn")
                || args[0].equalsIgnoreCase("spawnde") || args[0].equalsIgnoreCase("create"))) {
            options.add("grass-stone");
            options.add("sand-sandstone");
            options.add("red-sand-red-sandstone");
            options.add(String.valueOf(defaultSideLength()));
            return TabCompletions.filter(args[1], options.toArray(String[]::new));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            options.add("f5");
            options.add("f10");
            options.add("f20");
            options.add("f40");
            return TabCompletions.filter(args[2], options.toArray(String[]::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            options.add("all");
            generator.maps().keySet().forEach(options::add);
            return TabCompletions.filter(args[1], options.toArray(String[]::new));
        }
        return List.of();
    }

    private static final class MapMenuHolder implements InventoryHolder {
        private Inventory inventory;
        private SmoothTerrainGenerator.TerrainMap map = SmoothTerrainGenerator.TerrainMap.GRASS_STONE;
        private SmoothTerrainGenerator.TerrainShape shape = SmoothTerrainGenerator.TerrainShape.RANDOM;
        private boolean surfaceOnly;
        private int maxHeightDelta = SmoothTerrainGenerator.MAX_HEIGHT_DELTA;
        private int size = SmoothTerrainGenerator.WIDTH;
        private int controlSpacing = SmoothTerrainGenerator.CONTROL_SPACING;

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
