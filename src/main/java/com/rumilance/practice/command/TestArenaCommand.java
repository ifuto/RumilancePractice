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

/** Admin entry point and map selector for the generated smooth terrain test arena. */
public final class TestArenaCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int GRASS_SLOT = 2;
    private static final int SAND_SLOT = 6;

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
                            "Unknown map. Use grass-stone or sand-sandstone.", NamedTextColor.RED));
                    return true;
                }
                start(player, map);
            } else {
                openMapMenu(player);
            }
            return true;
        }
        player.sendMessage(Component.text(
                "Unknown testarena action. Use /testarena spawn.", NamedTextColor.RED));
        return true;
    }

    private void start(Player player, SmoothTerrainGenerator.TerrainMap map) {
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
        player.sendMessage(Component.text(
                "Planning a 100x100 smooth " + map.displayName()
                        + " terrain asynchronously. Block placement is batched to protect TPS...",
                NamedTextColor.AQUA));
        generator.generate(player, map, seed, result -> player.sendMessage(Component.text(
                "Test arena ready: " + result.map().displayName() + ", " + result.width() + "x"
                        + result.width() + ", 50 layers, height " + result.minimumHeight() + ".."
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
                "Deleting the previous test arena in low-lag batches...", NamedTextColor.AQUA));
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
        Inventory inventory = Bukkit.createInventory(holder, 9,
                Component.text("TestArena: choose a map", NamedTextColor.DARK_AQUA));
        holder.bind(inventory);
        inventory.setItem(GRASS_SLOT, item(Material.GRASS_BLOCK,
                "Map A: Grass / Stone",
                "Layer 1: grass block", "Layers 2-3: dirt", "Layers 4-50: stone"));
        inventory.setItem(SAND_SLOT, item(Material.SAND,
                "Map B: Sand / Sandstone",
                "Layers 1-4: sand", "Layers 5-50: sandstone"));
        inventory.setItem(4, item(Material.BARRIER, "Close", "No map will be created."));
        player.openInventory(inventory);
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
        if (!(event.getView().getTopInventory().getHolder() instanceof MapMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        SmoothTerrainGenerator.TerrainMap map = switch (event.getRawSlot()) {
            case GRASS_SLOT -> SmoothTerrainGenerator.TerrainMap.GRASS_STONE;
            case SAND_SLOT -> SmoothTerrainGenerator.TerrainMap.SAND_SANDSTONE;
            default -> null;
        };
        if (map == null) {
            return;
        }
        player.closeInventory();
        start(player, map);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(args[0], "spawn", "delete", "cancel");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn")
                || args[0].equalsIgnoreCase("spawnde"))) {
            return TabCompletions.filter(args[1], "grass-stone", "sand-sandstone");
        }
        return List.of();
    }

    private static final class MapMenuHolder implements InventoryHolder {
        private Inventory inventory;

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
