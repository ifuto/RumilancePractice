package com.rumilance.practice.testarena;

import com.rumilance.practice.locale.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Anvil-naming prompt for the TestArena side length: the player renames a piece of PAPER to a
 * number (21-256) and takes the result. Mirrors {@code KitAnvilRenameService} so the flow feels
 * native, but this one needs no rank gate — the map is disposable admin tooling. The callbacks
 * (apply/invalid/cancel) are recorded per player so a single listener serves any host.</p>
 */
public final class SideLengthAnvilService implements Listener {

    public record Pending(BiConsumer<Player, Integer> apply,
                          BiConsumer<Player, String> invalid,
                          java.util.function.Consumer<Player> cancel) {
    }

    private final Plugin plugin;
    private final MessageService messages;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public SideLengthAnvilService(Plugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public boolean isPromptOpen(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public enum OpenResult {
        OPENED, FAILED, BUSY, NONE
    }

    /**
     * Opens the rename prompt for {@code player}. {@code currentSize} pre-fills the input; the
     * callbacks receive the parsed result. Returns why it didn't open, if it didn't.
     */
    public OpenResult open(Player player, int currentSize, BiConsumer<Player, Integer> apply,
                           BiConsumer<Player, String> invalid, java.util.function.Consumer<Player> cancel) {
        if (pending.containsKey(player.getUniqueId())) {
            return OpenResult.BUSY;
        }
        Pending pendingEntry = new Pending(apply, invalid, cancel);
        pending.put(player.getUniqueId(), pendingEntry);
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                pending.remove(player.getUniqueId());
                return;
            }
            InventoryView view = player.openAnvil(player.getLocation(), true);
            if (view == null) {
                pending.remove(player.getUniqueId());
                player.sendMessage(messages == null
                        ? Component.text("Could not open the side length prompt.", NamedTextColor.RED)
                        : messages.render(player, "gui.testarena-anvil-fail"));
                return;
            }
            AnvilInventory anvil = (AnvilInventory) view.getTopInventory();
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.displayName(Component.text(String.valueOf(currentSize)));
            paper.setItemMeta(meta);
            anvil.setItem(0, paper);
            anvil.setRepairCost(0);
            player.sendMessage(messages == null
                    ? Component.text("Type a side length in blocks, then take the paper. Valid sizes: "
                            + SmoothTerrainGenerator.MIN_WIDTH + "-" + SmoothTerrainGenerator.MAX_WIDTH
                            + ".", NamedTextColor.YELLOW)
                    : messages.render(player, "gui.testarena-anvil-hint"));
            player.sendMessage(messages == null
                    ? Component.text("Valid sizes: " + SmoothTerrainGenerator.MIN_WIDTH + "-"
                            + SmoothTerrainGenerator.MAX_WIDTH, NamedTextColor.GRAY)
                    : messages.render(player, "gui.testarena-anvil-limit"));
        });
        return OpenResult.OPENED;
    }

    /** Picks up the paper result: parse it, then run the apply / invalid callback. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Pending prompt = pending.get(player.getUniqueId());
        if (prompt == null || !(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        if (event.getRawSlot() != 2 || event.getClickedInventory() != anvil) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }
        String name = plainName(result);
        event.setCancelled(true);
        event.setCurrentItem(null);
        player.setItemOnCursor(null);
        pending.remove(player.getUniqueId());
        anvil.clear();
        Integer parsed = parse(name);
        BiConsumer<Player, Integer> apply = prompt.apply();
        BiConsumer<Player, String> invalid = prompt.invalid();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.closeInventory();
            if (parsed == null) {
                if (messages != null) {
                    player.sendMessage(messages.render(player, "gui.testarena-anvil-invalid",
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
                                    .parsed("value", name == null ? "" : name)));
                } else {
                    player.sendMessage(Component.text(
                            "'" + name + "' is not a valid side length. Use "
                                    + SmoothTerrainGenerator.MIN_WIDTH + "-"
                                    + SmoothTerrainGenerator.MAX_WIDTH + ".",
                            NamedTextColor.RED));
                }
                invalid.accept(player, name);
            } else {
                apply.accept(player, parsed);
            }
        });
    }

    /** Live-validation: null out any result that is not a valid side length. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        if (!pending.containsKey(player.getUniqueId())) {
            return;
        }
        event.getInventory().setRepairCost(0);
        ItemStack result = event.getResult();
        if (result != null && !result.getType().isAir() && parse(plainName(result)) == null) {
            event.setResult(null);
        }
    }

    /** Cleanup when the prompt is dismissed without taking the result. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        Pending prompt = pending.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        anvil.clear();
        java.util.function.Consumer<Player> cancel = prompt.cancel();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                cancel.accept(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private static String plainName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
    }

    private static Integer parse(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        try {
            int value = Integer.parseInt(cleaned);
            if (value < SmoothTerrainGenerator.MIN_WIDTH || value > SmoothTerrainGenerator.MAX_WIDTH) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
