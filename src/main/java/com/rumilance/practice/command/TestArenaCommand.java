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

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Admin entry point and settings menu for the generated smooth terrain test arena. */
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
    private static final int START_SLOT = 49;

    private final SmoothTerrainGenerator generator;

    public TestArenaCommand(SmoothTerrainGenerator generator) {
        this.generator = generator;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only: run /testarena spawn in the test world.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text(
                    "/testarena spawn | /testarena delete | /testarena cancel", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("cancel")) {
            generator.cancel(player.getUniqueId());
            player.sendMessage(Component.text("Test arena operation cancelled.", NamedTextColor.YELLOW));
            return true;
        }
        if (sub.equals("delete")) {
            delete(player);
            return true;
        }
        if (sub.equals("spawn") || sub.equals("spawnde")) {
            if (args.length >= 2) {
                SmoothTerrainGenerator.TerrainMap map =
                        SmoothTerrainGenerator.TerrainMap.parse(args[1]);
                if (map == null) {
                    player.sendMessage(Component.text(
                            "Unknown map. Use grass-stone, sand-sandstone, or red-sand-red-sandstone.",
                            NamedTextColor.RED));
                    return true;
                }
                start(player, new SmoothTerrainGenerator.TerrainSettings(
                        map, SmoothTerrainGenerator.TerrainShape.RANDOM, false,
                        SmoothTerrainGenerator.MAX_HEIGHT_DELTA));
            } else {
                openMapMenu(player);
            }
            return true;
        }
        player.sendMessage(Component.text(
                "Unknown testarena action. Use /testarena spawn.", NamedTextColor.RED));
        return true;
    }

    private void start(Player player, SmoothTerrainGenerator.TerrainSettings settings) {
        if (generator.isRunning(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "A test arena operation is already running for you.", NamedTextColor.YELLOW));
            return;
        }
        if (generator.hasPreviousMap()) {
            player.sendMessage(Component.text(
                    "Delete the previous test arena first: /testarena delete", NamedTextColor.YELLOW));
            return;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        String foundation = settings.surfaceOnly() ? "surface-only (2 underground layers)" :
                "200 underground layers";
        player.sendMessage(Component.text(
                "Planning a 100x100 smooth " + settings.shape().label().toLowerCase(Locale.ROOT)
                        + " terrain with " + settings.map().displayName() + ", " + foundation
                        + ", max height difference " + settings.maxHeightDelta()
                        + ". Block placement is batched to protect TPS...",
                NamedTextColor.AQUA));
        generator.generate(player, settings, seed, result -> player.sendMessage(Component.text(
                "Test arena ready: " + result.map().displayName() + ", " + result.width() + "x"
                        + result.width() + ", " + foundation + " + bedrock, height " + result.minimumHeight() + ".."
                        + result.maximumHeight() + " (range " + result.heightRange() + "), seed "
                        + result.seed() + ". Use /testarena delete when finished.",
                NamedTextColor.GREEN)),
                error -> player.sendMessage(Component.text(error, NamedTextColor.RED)));
    }

    private void delete(Player player) {
        if (generator.isRunning(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "Wait for the current test arena operation to finish.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text(
                "Deleting the previous test arena, including its underground foundation and bedrock, in low-lag batches...",
                NamedTextColor.AQUA));
        generator.delete(player,
                columns -> player.sendMessage(Component.text(
                        "Deleted the previous test arena (" + columns + " columns).", NamedTextColor.GREEN)),
                error -> player.sendMessage(Component.text(error, NamedTextColor.RED)));
    }

    private void openMapMenu(Player player) {
        if (generator.hasPreviousMap()) {
            player.sendMessage(Component.text(
                    "Delete the previous test arena first: /testarena delete", NamedTextColor.YELLOW));
            return;
        }
        MapMenuHolder holder = new MapMenuHolder();
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
                "100 x 100 surface, automatically smoothed", "Choose material, shape, underground and height."));

        inventory.setItem(GRASS_SLOT, item(Material.GRASS_BLOCK,
                selected(holder.map == SmoothTerrainGenerator.TerrainMap.GRASS_STONE, "Grass / Stone"),
                "Surface: grass block", "Layers 2-3: dirt", "Deeper foundation: stone"));
        inventory.setItem(SAND_SLOT, item(Material.SAND,
                selected(holder.map == SmoothTerrainGenerator.TerrainMap.SAND_SANDSTONE, "Sand / Sandstone"),
                "Surface: sand", "Layers 1-4: sand", "Deeper foundation: sandstone"));
        inventory.setItem(RED_SAND_SLOT, item(Material.RED_SAND,
                selected(holder.map == SmoothTerrainGenerator.TerrainMap.RED_SAND_RED_SANDSTONE,
                        "Red Sand / Red Sandstone"),
                "Layers 1-3: red sand", "Deeper surface: red sandstone"));

        inventory.setItem(RANDOM_SLOT, item(Material.WHEAT_SEEDS,
                selected(holder.shape == SmoothTerrainGenerator.TerrainShape.RANDOM, "Smooth random shape"),
                "Shape: smooth random height map"));
        inventory.setItem(BOWL_SLOT, item(Material.BOWL,
                selected(holder.shape == SmoothTerrainGenerator.TerrainShape.CENTER_LOW, "Centre-low bowl"),
                "Shape: centre gently slopes down", "Independent from the material choice"));

        inventory.setItem(UNDERGROUND_SLOT, item(Material.STONE,
                selected(!holder.surfaceOnly, "Underground: 200 blocks"),
                "Surface to bedrock: up to 200 blocks", "Bedrock is kept above world minimum Y"));
        inventory.setItem(SURFACE_ONLY_SLOT, item(Material.GLASS,
                selected(holder.surfaceOnly, "Surface only"),
                "Still creates at least 2 underground layers", "Then one safe bedrock layer"));

        inventory.setItem(HEIGHT_ZERO_SLOT, item(Material.SNOWBALL,
                selected(holder.maxHeightDelta == 0, "Max height difference: 0"),
                "Flat surface"));
        inventory.setItem(HEIGHT_TWO_SLOT, item(Material.SNOW_BLOCK,
                selected(holder.maxHeightDelta == 2, "Max height difference: 2"),
                "Gentle terrain"));
        inventory.setItem(HEIGHT_FOUR_SLOT, item(Material.COBBLESTONE,
                selected(holder.maxHeightDelta == 4, "Max height difference: 4"),
                "Maximum smooth variation"));

        inventory.setItem(START_SLOT, item(Material.EMERALD_BLOCK, "Generate test arena",
                "Click to create the selected 100 x 100 map"));
        inventory.setItem(53, item(Material.BARRIER, "Close", "No map will be created."));
    }

    private static String selected(boolean selected, String name) {
        return selected ? "Selected: " + name : name;
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
            case UNDERGROUND_SLOT -> holder.surfaceOnly = false;
            case SURFACE_ONLY_SLOT -> holder.surfaceOnly = true;
            case HEIGHT_ZERO_SLOT -> holder.maxHeightDelta = 0;
            case HEIGHT_TWO_SLOT -> holder.maxHeightDelta = 2;
            case HEIGHT_FOUR_SLOT -> holder.maxHeightDelta = 4;
            case START_SLOT -> {
                player.closeInventory();
                start(player, holder.settings());
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(args[0], "spawn", "delete", "cancel");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn")
                || args[0].equalsIgnoreCase("spawnde"))) {
            return TabCompletions.filter(args[1], "grass-stone", "sand-sandstone", "red-sand-red-sandstone");
        }
        return List.of();
    }

    private static final class MapMenuHolder implements InventoryHolder {
        private Inventory inventory;
        private SmoothTerrainGenerator.TerrainMap map = SmoothTerrainGenerator.TerrainMap.GRASS_STONE;
        private SmoothTerrainGenerator.TerrainShape shape = SmoothTerrainGenerator.TerrainShape.RANDOM;
        private boolean surfaceOnly;
        private int maxHeightDelta = SmoothTerrainGenerator.MAX_HEIGHT_DELTA;

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        SmoothTerrainGenerator.TerrainSettings settings() {
            return new SmoothTerrainGenerator.TerrainSettings(map, shape, surfaceOnly, maxHeightDelta);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
