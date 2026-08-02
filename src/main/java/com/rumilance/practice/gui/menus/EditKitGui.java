package com.rumilance.practice.gui.menus;

import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import com.rumilance.practice.model.KitLayoutSnapshot;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Edit official kit slot layout only (GUI 5). Click-swap rearrange, content validated on save.
 */
public final class EditKitGui extends AbstractGui {

    private final KitService kitService;
    private final KitLayoutRepository layoutRepository;
    private final KitLayoutCache layoutCache;
    private final AsyncExecutor asyncExecutor;
    private final PlayerStateManager stateManager;

    public EditKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            KitLayoutRepository layoutRepository,
            KitLayoutCache layoutCache,
            AsyncExecutor asyncExecutor,
            PlayerStateManager stateManager
    ) {
        super(registry, sounds, GuiType.EDIT_KIT, 5, true);
        this.kitService = kitService;
        this.layoutRepository = layoutRepository;
        this.layoutCache = layoutCache;
        this.asyncExecutor = asyncExecutor;
        this.stateManager = stateManager;
    }

    /** Opens the editor directly in edit mode for the given kit (/ekit select flow). */
    public void openKitEditor(Player player, String kitName) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitName);
        session.put("mode", "edit");
        session.put("selected_slot", null);
        try {
            if (stateManager.getState(player.getUniqueId()) == PlayerState.LOBBY
                    || stateManager.getState(player.getUniqueId()) == PlayerState.OPENING_GUI) {
                stateManager.transition(player.getUniqueId(), PlayerState.EDITING_KIT);
            }
        } catch (Exception ignored) {
            // keep going
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    public void openKitPicker(Player player) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("mode", "picker");
        try {
            if (stateManager.getState(player.getUniqueId()) == PlayerState.LOBBY
                    || stateManager.getState(player.getUniqueId()) == PlayerState.OPENING_GUI) {
                stateManager.transition(player.getUniqueId(), PlayerState.EDITING_KIT);
            }
        } catch (Exception ignored) {
            // keep going
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit();
        return Component.text(kit == null ? "Edit Kit" : "Edit: " + kit, NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        if ("picker".equals(session.get("mode", String.class)) || session.selectedKit() == null) {
            int i = 0;
            for (KitDefinition kit : kitService.enabled()) {
                if (i >= 21) {
                    break;
                }
                Material mat = Material.matchMaterial(kit.icon());
                ItemStack icon = new ItemStack(mat == null ? Material.DIAMOND_SWORD : mat);
                ItemMeta meta = icon.getItemMeta();
                meta.displayName(Component.text(kit.name(), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                        "editkit:" + kit.name());
                icon.setItemMeta(meta);
                inventory.setItem(GuiSlots.slot(1 + i / 7, 1 + i % 7), icon);
                i++;
            }
            inventory.setItem(GuiSlots.slot(4, 4), GuiDecorator.button(Material.BARRIER,
                    Component.text("Close", NamedTextColor.RED), "close"));
            return;
        }
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            return;
        }
        ItemStack[] layout = loadLayout(player.getUniqueId(), kit);
        // armor row visually: helmet/chest/legs/boots + offhand
        inventory.setItem(GuiSlots.slot(0, 1), tagged(layout.length > 36 ? layout[36] : null, "slot:36"));
        inventory.setItem(GuiSlots.slot(0, 2), tagged(layout.length > 37 ? layout[37] : null, "slot:37"));
        inventory.setItem(GuiSlots.slot(0, 3), tagged(layout.length > 38 ? layout[38] : null, "slot:38"));
        inventory.setItem(GuiSlots.slot(0, 4), tagged(layout.length > 39 ? layout[39] : null, "slot:39"));
        inventory.setItem(GuiSlots.slot(0, 6), tagged(layout.length > 40 ? layout[40] : null, "slot:40"));
        // main inv slots 9-35 -> rows 1-3 cols 1-7 simplified mapping to center
        for (int inv = 9; inv < 36; inv++) {
            int local = inv - 9;
            int row = 1 + local / 9;
            int col = local % 9;
            if (col == 0 || col == 8 || row > 3) {
                continue;
            }
            inventory.setItem(GuiSlots.slot(row, col), tagged(layout[inv], "slot:" + inv));
        }
        // hotbar row 4
        for (int hot = 0; hot < 9; hot++) {
            inventory.setItem(GuiSlots.slot(4, hot), tagged(layout[hot], "slot:" + hot));
        }
        inventory.setItem(GuiSlots.slot(0, 8), GuiDecorator.button(Material.LIME_DYE,
                Component.text("Save", NamedTextColor.GREEN), "save"));
        inventory.setItem(GuiSlots.slot(0, 0), GuiDecorator.button(Material.RED_DYE,
                Component.text("Back", NamedTextColor.RED), "back"));
        session.put("layout", layout);
    }

    private ItemStack[] loadLayout(UUID uuid, KitDefinition kit) {
        try {
            var snap = layoutRepository.find(uuid, kit.name());
            if (snap.isPresent()) {
                return ItemSerializer.fromBase64(snap.get().itemDataBase64());
            }
        } catch (Exception ignored) {
            // fall through
        }
        ItemStack[] layout = new ItemStack[41];
        for (KitItemEntry entry : kit.items()) {
            Material mat = Material.matchMaterial(entry.material());
            if (mat != null && entry.slot() >= 0 && entry.slot() < 36) {
                layout[entry.slot()] = new ItemStack(mat, entry.amount());
            }
        }
        layout[36] = material(kit.armor().get("helmet"));
        layout[37] = material(kit.armor().get("chestplate"));
        layout[38] = material(kit.armor().get("leggings"));
        layout[39] = material(kit.armor().get("boots"));
        return layout;
    }

    private static ItemStack material(String name) {
        if (name == null) {
            return null;
        }
        Material mat = Material.matchMaterial(name);
        return mat == null ? null : new ItemStack(mat);
    }

    private ItemStack tagged(ItemStack stack, String action) {
        if (stack == null || stack.getType().isAir()) {
            ItemStack glass = GuiDecorator.decorative(Material.GRAY_STAINED_GLASS_PANE, " ");
            ItemMeta meta = glass.getItemMeta();
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, action);
            glass.setItemMeta(meta);
            return glass;
        }
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, action);
        copy.setItemMeta(meta);
        return copy;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action) || "back".equals(action)) {
            if ("back".equals(action) && session.selectedKit() != null) {
                session.setSelectedKit(null);
                session.put("mode", "picker");
                render(player, session, inventory);
                return;
            }
            stateManager.resetToLobby(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (action.startsWith("editkit:")) {
            session.setSelectedKit(action.substring(8));
            session.put("mode", "edit");
            session.put("selected_slot", null);
            render(player, session, inventory);
            sounds.play(player, "kit-select");
            return;
        }
        if (action.startsWith("slot:")) {
            int clicked = Integer.parseInt(action.substring(5));
            Integer selected = session.get("selected_slot", Integer.class);
            ItemStack[] layout = session.get("layout", ItemStack[].class);
            if (layout == null) {
                return;
            }
            if (selected == null) {
                session.put("selected_slot", clicked);
                sounds.play(player, "gui-click");
                player.sendActionBar(Component.text("Selected slot " + clicked, NamedTextColor.YELLOW));
            } else {
                ItemStack tmp = layout[selected];
                layout[selected] = layout[clicked];
                layout[clicked] = tmp;
                session.put("selected_slot", null);
                session.put("layout", layout);
                sounds.play(player, "select");
                render(player, session, inventory);
            }
            return;
        }
        if ("save".equals(action)) {
            save(player, session);
        }
    }

    private void save(Player player, GuiSession session) {
        String kitId = session.selectedKit();
        KitDefinition kit = kitService.get(kitId).orElse(null);
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        if (kit == null || layout == null) {
            return;
        }
        ItemStack[] baseline = loadLayout(UUID.randomUUID(), kit); // fresh from kit definition only
        // rebuild baseline from kit definition only
        baseline = new ItemStack[41];
        for (KitItemEntry entry : kit.items()) {
            Material mat = Material.matchMaterial(entry.material());
            if (mat != null && entry.slot() >= 0 && entry.slot() < 36) {
                baseline[entry.slot()] = new ItemStack(mat, entry.amount());
            }
        }
        baseline[36] = material(kit.armor().get("helmet"));
        baseline[37] = material(kit.armor().get("chestplate"));
        baseline[38] = material(kit.armor().get("leggings"));
        baseline[39] = material(kit.armor().get("boots"));
        // offhand starts empty unless kit items include slot 40

        if (!sameContents(baseline, layout)) {
            sounds.play(player, "error");
            player.sendMessage(Component.text("Layout content mismatch. Only rearranging is allowed.", NamedTextColor.RED));
            return;
        }
        String base64 = ItemSerializer.toBase64(layout);
        KitLayoutSnapshot snap = KitLayoutSnapshot.create(player.getUniqueId(), kitId, base64);
        layoutCache.put(player.getUniqueId(), kitId, layout);
        asyncExecutor.execute(() -> {
            try {
                layoutRepository.upsert(snap);
                player.getServer().getScheduler().runTask(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("RumilancePractice"),
                        () -> {
                            sounds.play(player, "select");
                            player.sendMessage(Component.text("Kit layout saved.", NamedTextColor.GREEN));
                        });
            } catch (Exception e) {
                player.getServer().getScheduler().runTask(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("RumilancePractice"),
                        () -> player.sendMessage(Component.text("Save failed.", NamedTextColor.RED)));
            }
        });
    }

    private static boolean sameContents(ItemStack[] a, ItemStack[] b) {
        List<String> left = canonicalize(a);
        List<String> right = canonicalize(b);
        return left.equals(right);
    }

    private static List<String> canonicalize(ItemStack[] items) {
        List<String> list = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            list.add(item.getType().name() + ":" + item.getAmount());
        }
        list.sort(Comparator.naturalOrder());
        return list;
    }
}
