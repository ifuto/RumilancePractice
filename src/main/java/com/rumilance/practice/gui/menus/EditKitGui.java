package com.rumilance.practice.gui.menus;

import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitLayoutEditor;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitLayoutContents;
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
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Edit official kit slot layout only (GUI 5). Rearrange with vanilla-style click/drag;
 * content validated on save (no adding/removing items).
 */
public final class EditKitGui extends AbstractGui {

    private final KitService kitService;
    private final KitLayoutRepository layoutRepository;
    private final KitLayoutCache layoutCache;
    private final AsyncExecutor asyncExecutor;
    private final PlayerStateManager stateManager;
    private final com.rumilance.practice.kit.PresetItems presetItems;
    private KitPresetPickerGui presetPickerGui;
    private com.rumilance.practice.gui.KitAnvilRenameService kitAnvilRenameService;
    private SmithingTrimGui smithingTrimGui;

    public void setKitAnvilRenameService(com.rumilance.practice.gui.KitAnvilRenameService kitAnvilRenameService) {
        this.kitAnvilRenameService = kitAnvilRenameService;
    }

    public void setSmithingTrimGui(SmithingTrimGui smithingTrimGui) {
        this.smithingTrimGui = smithingTrimGui;
    }

    public EditKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            KitLayoutRepository layoutRepository,
            KitLayoutCache layoutCache,
            AsyncExecutor asyncExecutor,
            PlayerStateManager stateManager
    ) {
        this(registry, sounds, kitService, layoutRepository, layoutCache, asyncExecutor, stateManager, null);
    }

    public EditKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            KitLayoutRepository layoutRepository,
            KitLayoutCache layoutCache,
            AsyncExecutor asyncExecutor,
            PlayerStateManager stateManager,
            com.rumilance.practice.kit.PresetItems presetItems
    ) {
        super(registry, sounds, GuiType.EDIT_KIT, 5, true);
        this.kitService = kitService;
        this.layoutRepository = layoutRepository;
        this.layoutCache = layoutCache;
        this.asyncExecutor = asyncExecutor;
        this.stateManager = stateManager;
        this.presetItems = presetItems;
    }

    public void setPresetPickerGui(KitPresetPickerGui presetPickerGui) {
        this.presetPickerGui = presetPickerGui;
    }

    public void reopenWithLayout(Player player, String kitName, ItemStack[] layout) {
        openKitEditor(player, kitName, null);
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session != null && layout != null) {
            session.put("layout", layout);
            render(player, session, player.getOpenInventory().getTopInventory());
        }
    }

    /** Opens the editor directly in edit mode for the given kit (/ekit select flow). */
    public void openKitEditor(Player player, String kitName) {
        openKitEditor(player, kitName, null);
    }

    /** Opens the kit editor; {@code preset} is stored on the session when non-blank. */
    public void openKitEditor(Player player, String kitName, String preset) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitName);
        session.put("mode", "edit");
        if (preset != null && !preset.isBlank()) {
            session.put("preset", preset);
        }
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

    /**
     * Applies an anvil-renamed tool back into the kit layout and reopens the editor.
     */
    public void applyRenamedItem(Player player, String kitId, String preset, int layoutSlot, ItemStack renamed) {
        openKitEditor(player, kitId, preset);
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null || renamed == null) {
            return;
        }
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        if (layout == null || layoutSlot < 0 || layoutSlot >= layout.length) {
            return;
        }
        layout[layoutSlot] = renamed.clone();
        session.put("layout", layout);
        Inventory top = player.getOpenInventory().getTopInventory();
        render(player, session, top);
    }

    public void applyTrimmedItem(Player player, String kitId, String preset, int layoutSlot, ItemStack trimmed) {
        openKitEditor(player, kitId, preset);
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null || trimmed == null) {
            return;
        }
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        if (layout == null || layoutSlot < 0 || layoutSlot >= layout.length) {
            return;
        }
        layout[layoutSlot] = trimmed.clone();
        session.put("layout", layout);
        Inventory top = player.getOpenInventory().getTopInventory();
        render(player, session, top);
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
        return Component.text(kit == null ? "Edit Kit"
                : "Edit: " + com.rumilance.practice.util.KitNames.pretty(kit), UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.editorChrome(inventory);
        if ("picker".equals(session.get("mode", String.class)) || session.selectedKit() == null) {
            int i = 0;
            for (KitDefinition kit : kitService.enabled()) {
                if (i >= 21) {
                    break;
                }
                Material mat = Material.matchMaterial(kit.icon());
                ItemStack icon = new ItemStack(mat == null ? Material.DIAMOND_SWORD : mat);
                ItemMeta meta = icon.getItemMeta();
                meta.displayName(Component.text(kit.prettyDisplayName(), UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false));
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                        "editkit:" + kit.name());
                icon.setItemMeta(meta);
                inventory.setItem(GuiSlots.slot(1 + i / 7, 1 + i % 7), icon);
                i++;
            }
            inventory.setItem(GuiSlots.slot(4, 4),
                    ItemBuilder.action(UiTheme.CLOSE, Component.text("Close", UiTheme.DANGER), "close"));
            return;
        }
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            return;
        }
        // Keep in-session rearranges across re-render; reloading from disk wiped swaps before Save.
        ItemStack[] layout = KitLayoutContents.retainOrLoad(
                session.get("layout", ItemStack[].class),
                loadLayout(player.getUniqueId(), kit));
        // armor row visually: helmet/chest/legs/boots + offhand
        inventory.setItem(GuiSlots.slot(0, 1), tagged(layout.length > 36 ? layout[36] : null, "slot:36"));
        inventory.setItem(GuiSlots.slot(0, 2), tagged(layout.length > 37 ? layout[37] : null, "slot:37"));
        inventory.setItem(GuiSlots.slot(0, 3), tagged(layout.length > 38 ? layout[38] : null, "slot:38"));
        inventory.setItem(GuiSlots.slot(0, 4), tagged(layout.length > 39 ? layout[39] : null, "slot:39"));
        inventory.setItem(GuiSlots.slot(0, 6), tagged(layout.length > 40 ? layout[40] : null, "slot:40"));
        // Main inventory slots 9-35 -> menu rows 1-3, ALL 9 columns (27 slots exactly).
        // (Previously columns 0 and 8 were skipped, hiding the edge slots of each row.)
        for (int inv = 9; inv < 36; inv++) {
            int local = inv - 9;
            int row = 1 + local / 9;
            int col = local % 9;
            inventory.setItem(GuiSlots.slot(row, col), tagged(layout[inv], "slot:" + inv));
        }
        // hotbar row 4
        for (int hot = 0; hot < 9; hot++) {
            inventory.setItem(GuiSlots.slot(4, hot), tagged(layout[hot], "slot:" + hot));
        }
        inventory.setItem(GuiSlots.slot(0, 0),
                ItemBuilder.action(UiTheme.BACK, Component.text("Back", UiTheme.WARNING), "back"));
        if (kit.presetEnabled() && presetItems != null) {
            inventory.setItem(GuiSlots.slot(0, 7), ItemBuilder.action(Material.CHEST,
                    Component.text("Preset Items", UiTheme.SECONDARY), "open:preset"));
        }
        inventory.setItem(GuiSlots.slot(0, 8),
                ItemBuilder.action(UiTheme.CONFIRM, Component.text("Save", UiTheme.SUCCESS), "save"));
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
            ItemStack stack = kitEntryStack(entry);
            if (stack == null) {
                continue;
            }
            if (entry.slot() >= 0 && entry.slot() < 36) {
                layout[entry.slot()] = stack;
            } else if (entry.slot() == 40) {
                layout[40] = stack;
            }
        }
        layout[36] = material(kit.armor().get("helmet"));
        layout[37] = material(kit.armor().get("chestplate"));
        layout[38] = material(kit.armor().get("leggings"));
        layout[39] = material(kit.armor().get("boots"));
        return layout;
    }

    /** Full-NBT kit entry when available; plain material+amount otherwise. */
    private static ItemStack kitEntryStack(KitItemEntry entry) {
        if (entry.hasSerializedItem()) {
            ItemStack decoded = ItemSerializer.singleFromBase64(entry.itemDataBase64());
            if (decoded != null) {
                return decoded;
            }
        }
        Material mat = Material.matchMaterial(entry.material());
        return mat == null || mat.isAir() ? null : new ItemStack(mat, Math.max(1, entry.amount()));
    }

    private static ItemStack material(String name) {
        if (name == null) {
            return null;
        }
        // "data:<base64>" armor values carry full NBT.
        if (name.startsWith("data:")) {
            ItemStack decoded = ItemSerializer.singleFromBase64(name.substring("data:".length()));
            if (decoded != null) {
                return decoded;
            }
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
        handleClick(player, session, inventory, slot, action, org.bukkit.event.inventory.ClickType.LEFT);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT && action.startsWith("slot:")) {
            int layoutIndex = Integer.parseInt(action.substring(5));
            ItemStack[] layout = session.get("layout", ItemStack[].class);
            if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length) {
                ItemStack item = layout[layoutIndex];
                String kitId = session.selectedKit();
                String preset = session.get("preset", String.class);
                if (item != null && kitAnvilRenameService != null
                        && com.rumilance.practice.gui.KitAnvilRenameService.isRenameableTool(item.getType())
                        && kitAnvilRenameService.tryOpenRename(player, item, layoutIndex, kitId, preset)) {
                    return;
                }
                if (item != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta
                        && smithingTrimGui != null && kitId != null) {
                    smithingTrimGui.openForLayoutSlot(player, item, layoutIndex, kitId, preset);
                    return;
                }
            }
        }
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
            render(player, session, inventory);
            sounds.play(player, "kit-select");
            return;
        }
        if (action.startsWith("slot:")) {
            int layoutIndex = Integer.parseInt(action.substring(5));
            ItemStack[] layout = session.get("layout", ItemStack[].class);
            if (layout == null) {
                return;
            }
            KitLayoutEditor.handleSlotPickup(player, layout, layoutIndex);
            session.put("layout", layout);
            sounds.play(player, "gui-click");
            render(player, session, inventory);
            return;
        }
        if ("save".equals(action)) {
            save(player, session);
            return;
        }
        if ("open:preset".equals(action)) {
            if (presetPickerGui != null && session.selectedKit() != null) {
                ItemStack[] layout = session.get("layout", ItemStack[].class);
                presetPickerGui.open(player, session.selectedKit(), layout);
            }
            return;
        }
    }

    private void save(Player player, GuiSession session) {
        String kitId = session.selectedKit();
        KitDefinition kit = kitService.get(kitId).orElse(null);
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        if (kit == null || layout == null) {
            return;
        }
        KitLayoutEditor.syncLayoutFromTopInventory(player.getOpenInventory().getTopInventory(), layout);
        session.put("layout", layout);
        // Baseline straight from the kit definition (full NBT items included).
        ItemStack[] baseline = new ItemStack[41];
        for (KitItemEntry entry : kit.items()) {
            ItemStack stack = kitEntryStack(entry);
            if (stack == null) {
                continue;
            }
            if (entry.slot() >= 0 && entry.slot() < 36) {
                baseline[entry.slot()] = stack;
            } else if (entry.slot() == 40) {
                baseline[40] = stack;
            }
        }
        baseline[36] = material(kit.armor().get("helmet"));
        baseline[37] = material(kit.armor().get("chestplate"));
        baseline[38] = material(kit.armor().get("leggings"));
        baseline[39] = material(kit.armor().get("boots"));

        if (!kit.presetEnabled() && !com.rumilance.practice.guard.PracticeGuards.kitLayoutUnchanged(baseline, layout)) {
            sounds.play(player, "error");
            player.sendMessage(Component.text("Layout content mismatch. Only rearranging is allowed.", UiTheme.DANGER));
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
                            player.sendMessage(Component.text("Kit layout saved.", UiTheme.SUCCESS));
                        });
            } catch (Exception e) {
                player.getServer().getScheduler().runTask(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("RumilancePractice"),
                        () -> player.sendMessage(Component.text("Save failed.", UiTheme.DANGER)));
            }
        });
    }

}
