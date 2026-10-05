package com.rumilance.practice.gui.menus;

import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.BottomInventoryClickHandler;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.PracticeGuiHolder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.CrystalFfaStore;
import com.rumilance.practice.kit.KitLayoutEditor;
import com.rumilance.practice.kit.KitLoadout;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitLayoutContents;
import com.rumilance.practice.kit.KitVariantsStore;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.kit.PresetItems;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import com.rumilance.practice.model.KitLayoutSnapshot;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.EnchantmentRules;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.ItemSerializer;
import com.rumilance.practice.util.KitLayoutDelta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Edit official kit slot layout only (GUI 5). Rearrange with vanilla-style click/drag;
 * content validated on save (no adding/removing items).
 */
public final class EditKitGui extends AbstractGui implements BottomInventoryClickHandler {

    private static final int PRESET_ITEMS_PER_PAGE = 27;

    private final KitService kitService;
    private final KitLayoutRepository layoutRepository;
    private final KitLayoutCache layoutCache;
    private final AsyncExecutor asyncExecutor;
    private final PlayerStateManager stateManager;
    private final PresetItems presetItems;
    private com.rumilance.practice.lobby.LobbyService lobbyService;
    private com.rumilance.practice.gui.KitAnvilRenameService kitAnvilRenameService;
    private com.rumilance.practice.gui.KitEditStash kitEditStash;
    private SmithingTrimGui smithingTrimGui;
    private ShieldPatternGui shieldPatternGui;
    private com.rumilance.practice.hiddenrank.HiddenRankService hiddenRankService;
    private com.rumilance.practice.rank.RankService rankService;
    /** 中キット (inner kits): preset loadouts stored inside a kit. Wired from bootstrap. */
    private com.rumilance.practice.kit.InnerKitService innerKits;
    private InnerKitSelectGui innerKitSelectGui;
    private InnerKitAdminGui innerKitAdminGui;
    private KitAdminGui kitAdminGui;

    public void setKitAdminGui(KitAdminGui kitAdminGui) {
        this.kitAdminGui = kitAdminGui;
    }

    /** Official edits live in kits.yml, not in the editor player's personal kit_layouts row. */
    private static boolean isOfficialEdit(GuiSession session) {
        return session != null && Boolean.TRUE.equals(session.get("official-edit", Boolean.class));
    }

    public void setInnerKits(com.rumilance.practice.kit.InnerKitService innerKits) {
        this.innerKits = innerKits;
    }

    public void setInnerKitSelectGui(InnerKitSelectGui innerKitSelectGui) {
        this.innerKitSelectGui = innerKitSelectGui;
    }

    /** 中キットの管理画面（Admin 専用）。ここからでも右クリックで開けるようにする。 */
    public void setInnerKitAdminGui(InnerKitAdminGui innerKitAdminGui) {
        this.innerKitAdminGui = innerKitAdminGui;
    }

    /** The 中キット being edited, or null for the kit's own (default) loadout. */
    private static String innerKit(GuiSession session) {
        return session == null ? null : session.get(InnerKitSelectGui.CHOICE_KEY, String.class);
    }

    public void setRankService(com.rumilance.practice.rank.RankService rankService) {
        this.rankService = rankService;
    }
    private EkitSelectGui ekitSelectGui;

    public void setEkitSelectGui(EkitSelectGui ekitSelectGui) {
        this.ekitSelectGui = ekitSelectGui;
    }

    /** KIT1..9 variant picker (crystal kits): BACK from a variant editor returns here. */
    private CrystalKitSlotsGui crystalKitSlotsGui;

    public void setCrystalKitSlotsGui(CrystalKitSlotsGui crystalKitSlotsGui) {
        this.crystalKitSlotsGui = crystalKitSlotsGui;
    }

    public void setKitAnvilRenameService(com.rumilance.practice.gui.KitAnvilRenameService kitAnvilRenameService) {
        this.kitAnvilRenameService = kitAnvilRenameService;
    }

    public void setLobbyService(com.rumilance.practice.lobby.LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    public void setSmithingTrimGui(SmithingTrimGui smithingTrimGui) {
        this.smithingTrimGui = smithingTrimGui;
    }

    public void setShieldPatternGui(ShieldPatternGui shieldPatternGui) {
        this.shieldPatternGui = shieldPatternGui;
    }

    public void setHiddenRankService(com.rumilance.practice.hiddenrank.HiddenRankService hiddenRankService) {
        this.hiddenRankService = hiddenRankService;
    }

    public void setKitEditStash(com.rumilance.practice.gui.KitEditStash kitEditStash) {
        this.kitEditStash = kitEditStash;
    }

    public void stashCurrentLayout(Player player, GuiSession session) {
        if (isViewOnly(session) || kitEditStash == null || session == null || session.selectedKit() == null) {
            return;
        }
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        kitEditStash.putLayout(player.getUniqueId(), session.selectedKit(),
                session.get("preset", String.class), layout, isOfficialEdit(session));
    }

    public void restoreLobbyHands(Player player) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        if (lobbyService != null) {
            lobbyService.applyLobbyInventory(player);
        }
    }

    public void reopenFromStash(Player player) {
        if (kitEditStash == null) {
            return;
        }
        com.rumilance.practice.gui.KitEditStash.Snapshot snapshot = kitEditStash.get(player.getUniqueId());
        if (snapshot == null || snapshot.kitId() == null || snapshot.kitId().isBlank()) {
            return;
        }
        reopenWithLayout(player, snapshot.kitId(), snapshot.layout());
    }

    /**
     * Emergency save when a match/party pulls an editing player out of the kit editor -
     * the main GUI twin of {@code OriginalKitService#forceSaveAndExitForMatch}. The draft
     * persists through the normal save path (with the "kit-saved" toast, so the player SEES
     * the pull did not eat their edit), the lobby inventory comes back, the overlay stash is
     * cleared, and the editor session + window close cleanly. No-op for picker-mode opens
     * (they hold no layout) or players not in the editor at all.
     */
    public void forceSaveForMatch(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null || session.type() != type()
                || !"edit".equals(session.get("mode", String.class))) {
            return;
        }
        persistLayout(player, session, true);
        restoreLobbyHands(player);
        if (kitEditStash != null) {
            kitEditStash.clear(player.getUniqueId());
        }
        registry.close(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Universal "leaving for a fight" sweep for whatever GUI state this player is in:
     * force-saves an open edit session ({@link #forceSaveForMatch}), drops a rename typed
     * mid-prompt, and closes any still-open window. The kit GUI layers hold nothing else
     * that could survive into the match after this (menus persist nothing on close).
     */
    public void closeAnyForMatch(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        forceSaveForMatch(player);
        if (kitAnvilRenameService != null
                && kitAnvilRenameService.isRenaming(player.getUniqueId())) {
            kitAnvilRenameService.cancelForMatch(player.getUniqueId());
        }
        player.closeInventory();
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
            PresetItems presetItems
    ) {
        super(registry, sounds, GuiType.EDIT_KIT, 6, true);
        this.kitService = kitService;
        this.layoutRepository = layoutRepository;
        this.layoutCache = layoutCache;
        this.asyncExecutor = asyncExecutor;
        this.stateManager = stateManager;
        this.presetItems = presetItems;
    }

    /** True when this session is the tester's immutable view of another player's layout. */
    public boolean isViewOnly(GuiSession session) {
        return session != null && "view".equals(session.get("mode", String.class));
    }

    /** True when editing a kit with preset candidates enabled (hotbar palette + Q-drop delete). */
    /** Session key: the preset entry whose config screen is open in the bottom inventory. */
    private static final String K_PRESET_CONFIG = "preset_config";
    /** Enchantment customiser (More Enchant Item): stage, entry, working stack, pick. */
    private static final String K_ENCHANT_STAGE = "enchant_stage";
    private static final String K_ENCHANT_ENTRY = "enchant_entry";
    private static final String K_ENCHANT_ITEM = "enchant_item";
    private static final String K_ENCHANT_PICK = "enchant_pick";
    private static final String STAGE_LIST = "list";
    private static final String STAGE_LEVELS = "levels";
    private static final String STAGE_RESULT = "result";

    public boolean isPresetEdit(GuiSession session) {
        if (session == null || presetItems == null || !"edit".equals(session.get("mode", String.class))) {
            return false;
        }
        String kitId = session.selectedKit();
        if (kitId == null) {
            return false;
        }
        return kitService.get(kitId).map(KitDefinition::presetEnabled).orElse(false);
    }

    public void onEditorClosed(Player player, GuiSession session) {
        if (isViewOnly(session)) {
            // A tester view never borrowed or changed the viewer's lobby inventory.
            return;
        }
        if (kitAnvilRenameService != null && kitAnvilRenameService.isRenaming(player.getUniqueId())) {
            return;
        }
        if (registry.get(player.getUniqueId())
                .map(s -> s.type() != GuiType.EDIT_KIT)
                .orElse(false)) {
            return;
        }
        restoreLobbyHands(player);
        if (kitEditStash != null) {
            kitEditStash.clear(player.getUniqueId());
        }
    }

    /**
     * Applies an anvil-renamed tool back into the kit layout and reopens the editor.
     */
    public void applyRenamedItem(Player player, String kitId, String preset, int layoutSlot,
                                ItemStack renamed, ItemStack[] layoutSnapshot) {
        ItemStack[] layout = layoutSnapshot;
        if (layout == null && kitEditStash != null) {
            layout = kitEditStash.layoutCopy(player.getUniqueId());
        }
        if (layout == null) {
            layout = new ItemStack[41];
        } else {
            layout = layout.clone();
        }
        if (layoutSlot >= 0 && layoutSlot < layout.length && renamed != null) {
            layout[layoutSlot] = KitLayoutEditor.stripEditorTags(renamed.clone());
        }
        reopenWithLayout(player, kitId, layout);
    }

    /** Q / Ctrl+Q on a kit slot removes the item from the layout (preset kits only). */
    public void handleKitSlotDrop(Player player, GuiSession session, Inventory top, int guiSlot) {
        int layoutIndex = KitLayoutEditor.layoutIndexForGuiSlot(guiSlot);
        if (layoutIndex < 0) {
            return;
        }
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        if (layout == null || layoutIndex >= layout.length) {
            return;
        }
        ItemStack current = layout[layoutIndex];
        if (current == null || current.getType().isAir()) {
            return;
        }
        layout[layoutIndex] = null;
        session.put("layout", layout);
        sounds.play(player, "delete");
        render(player, session, top);
    }

    /** Clears the cursor and plays delete feedback (preset palette delete zones). */
    public void deleteCursorItem(Player player) {
        player.getOpenInventory().setCursor(null);
        sounds.play(player, "delete");
    }

    public void reopenWithLayout(Player player, String kitName, ItemStack[] layout) {
        // Keep the crystal variant, the K1..K4 variant AND the 中キット across reopens (anvil
        // rename / trim apply relaunch the editor): without this a preset edit would land on
        // the kit's base layout.
        GuiSession previous = registry.get(player.getUniqueId()).orElse(null);
        Integer crystal = crystalVariant(previous);
        Integer variant = variantSlot(previous);
        boolean official = isOfficialEdit(previous) || (kitEditStash != null
                && kitEditStash.get(player.getUniqueId()) != null
                && kitEditStash.get(player.getUniqueId()).officialEdit());
        if (official) {
            openOfficialEditor(player, kitName);
        } else {
            openKitEditor(player, kitName, null, crystal, innerKit(previous), variant);
        }
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session != null && layout != null) {
            session.put("layout", layout);
            render(player, session, player.getOpenInventory().getTopInventory());
        }
    }

    /** Opens the editor directly in edit mode for the given kit (/ekit select flow). */
    public void openKitEditor(Player player, String kitName) {
        openKitEditor(player, kitName, null, null);
    }

    /** Opens the kit editor; {@code preset} is stored on the session when non-blank. */
    public void openKitEditor(Player player, String kitName, String preset) {
        openKitEditor(player, kitName, preset, null);
    }

    /**
     * Opens the kit editor. {@code crystalVariant} (1..9) edits a Crystal FFA variant slot
     * instead of the kit's base layout — its layout is stored under
     * {@link CrystalFfaStore#variantKey}.
     */
    public void openKitEditor(Player player, String kitName, String preset, Integer crystalVariant) {
        openKitEditor(player, kitName, preset, crystalVariant, null);
    }

    /**
     * Opens the kit editor and records the MAIN/SUB category + page BACK should return to.
     * Used by the Ekit kit chooser so backing out lands on the page the player came from.
     */
    public void openKitEditorWithReturn(Player player, String kitName,
                                        String backCategory, int backPage) {
        openKitEditor(player, kitName, null, null);
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session != null) {
            session.put("back-category", backCategory);
            session.put("back-page", backPage);
        }
    }

    /**
     * Opens the kit editor on one of the player's K1..K4 variant slots for a general kit
     * (the KIT SELECT GUI mockup's 本/紙 chips decide which slot is active; BACK returns to
     * that chooser page). The layout is stored under {@link KitVariantsStore#variantKey}.
     */
    public void openKitVariantEditorWithReturn(Player player, String kitName, int variant,
                                               String backCategory, int backPage) {
        openKitEditor(player, kitName, null, null, null, KitVariantsStore.clamp(variant));
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session != null) {
            session.put("back-category", backCategory);
            session.put("back-page", backPage);
        }
    }

    /**
     * Opens the kit editor on one 中キット (inner kit): {@code innerKitId} edits that preset's
     * loadout, which lives in kits.yml under the kit, instead of the player's own rearrangement
     * of the kit. Null / blank / {@code default} edits the kit itself, as always.
     */
    public void openKitEditor(Player player, String kitName, String preset, Integer crystalVariant,
                              String innerKitId) {
        openKitEditor(player, kitName, preset, crystalVariant, innerKitId, null);
    }

    /** Full form: also takes a general-kit K1..K4 variant slot (see {@link KitVariantsStore}). */
    public void openKitEditor(Player player, String kitName, String preset, Integer crystalVariant,
                              String innerKitId, Integer variantSlot) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        // フォルダ(中メニュー)を渡されたらデフォルトの子を編集する。
        session.setSelectedKit(kitService.playableId(kitName));
        session.put("mode", "edit");
        if (preset != null && !preset.isBlank()) {
            session.put("preset", preset);
        }
        if (crystalVariant != null) {
            session.put("crystal", crystalVariant);
        }
        if (variantSlot != null) {
            session.put("variant-slot", KitVariantsStore.clamp(variantSlot));
        }
        if (!com.rumilance.practice.kit.InnerKitService.isDefault(innerKitId)) {
            session.put(InnerKitSelectGui.CHOICE_KEY,
                    com.rumilance.practice.kit.InnerKitService.normalizeId(innerKitId));
        }
        initPresetSession(session);
        PlayerState state = stateManager.getState(player.getUniqueId());
        try {
            if (state != PlayerState.EDITING_KIT) {
                stateManager.transition(player.getUniqueId(), PlayerState.EDITING_KIT);
            }
        } catch (Exception ignored) {
            // keep going — party / nested GUI may already be OPENING_GUI
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    /**
     * Admin editing the SHARED kit contents in kits.yml (including every child kit). Players
     * continue to use openKitEditor to save only their personal layout. This uses the same 41-slot
     * editor and the same snapshot/trim/rename tools, but its Save path writes the kit itself.
     */
    public void openOfficialEditor(Player player, String kitName) {
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(t(player, "general.no-permission"));
            return;
        }
        KitDefinition kit = kitService.playable(kitName).orElse(null);
        if (kit == null) {
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kit.name());
        session.put("mode", "edit");
        session.put("official-edit", Boolean.TRUE);
        initPresetSession(session);
        try {
            if (stateManager.getState(player.getUniqueId()) != PlayerState.EDITING_KIT) {
                stateManager.transition(player.getUniqueId(), PlayerState.EDITING_KIT);
            }
        } catch (Exception ignored) {
            // Do not eat a kit edit on a transition from another admin GUI.
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    /** Opens one of another player's saved official-kit layouts without entering edit mode. */
    public void openKitViewer(Player viewer, UUID targetId, String targetName, String kitName) {
        openWithSession(viewer, session -> {
            session.setSelectedKit(kitService.playableId(kitName));
            session.setTargetPlayer(targetId);
            session.put("mode", "view");
            session.put("viewer-target-name", targetName == null ? "?" : targetName);
        });
    }

    public void applyTrimmedItem(Player player, String kitId, String preset, int layoutSlot, ItemStack trimmed) {
        ItemStack[] layout = kitEditStash == null ? null : kitEditStash.layoutCopy(player.getUniqueId());
        if (layout == null) {
            GuiSession session = registry.get(player.getUniqueId()).orElse(null);
            layout = session == null ? null : session.get("layout", ItemStack[].class);
        }
        if (layout == null) {
            layout = new ItemStack[41];
        } else {
            layout = layout.clone();
        }
        if (trimmed != null && layoutSlot >= 0 && layoutSlot < layout.length) {
            layout[layoutSlot] = KitLayoutEditor.stripEditorTags(trimmed.clone());
        }
        reopenWithLayout(player, kitId, layout);
        persistLayout(player, kitId, layout, false);
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
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.BOOK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit();
        Integer crystal = crystalVariant(session);
        String pretty = kit == null ? "" : com.rumilance.practice.util.KitNames.pretty(kit);
        if (isViewOnly(session)) {
            String target = session.get("viewer-target-name", String.class);
            return Component.text(line(player, "gui.kit-view-layout-title")
                            .replace("<player>", target == null ? "" : target)
                            .replace("<kit>", pretty), UiTheme.PRIMARY)
                    .decoration(TextDecoration.ITALIC, false);
        }
        String inner = innerKit(session);
        Integer variant = variantSlot(session);
        String innerName = inner == null ? null : innerKits == null ? null
                : innerKits.get(kit, inner).map(com.rumilance.practice.kit.InnerKitService.InnerKit::displayName)
                        .orElse(null);
        StringBuilder label = new StringBuilder(line(player, isOfficialEdit(session)
                ? "gui.kit-shared-layout-title" : "gui.kit-layout-title").replace("<kit>", pretty));
        if (innerName != null) {
            label.append(" \u203a ").append(innerName);
        }
        if (crystal != null) {
            label.append(" (KIT").append(crystal).append(')');
        }
        if (variant != null) {
            label.append(" (K").append(variant).append(')');
        }
        return Component.text(label.toString(), UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        com.rumilance.practice.gui.GuiFrame.frameOpenBottom(inventory,
                com.rumilance.practice.gui.GuiFrame.Theme.YELLOW);
        if ("picker".equals(session.get("mode", String.class)) || session.selectedKit() == null) {
            int i = 0;
            for (KitDefinition kit : kitService.enabled()) {
                if (i >= 21) {
                    break;
                }
                // フォルダでも親のタイルは元の顔。クリックはデフォルトの子を開く。
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
            inventory.setItem(GuiSlots.slot(5, 4),
                    ItemBuilder.action(UiTheme.CLOSE, t(player, "menu.close"), "close"));
            return;
        }
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            return;
        }
        // Keep in-session rearranges across re-render; reloading from disk wiped swaps before Save.
        UUID layoutOwner = isViewOnly(session) && session.targetPlayer() != null
                ? session.targetPlayer() : player.getUniqueId();
        ItemStack[] layout = KitLayoutContents.retainOrLoad(
                session.get("layout", ItemStack[].class),
                isOfficialEdit(session) ? KitLoadout.fromOfficial(kit)
                        : loadLayout(layoutOwner, kit, crystalVariant(session), variantSlot(session),
                                innerKit(session),
                                personalPresetEdit(player)));
        // armor row visually: helmet/chest/legs/boots + offhand shield (mockup KIT EDIT GUI).
        // 6-row editor: row0 = armor + save/reset, row1 = decor + BACK, rows2-4 = main
        // inventory, row5 = hotbar.
        boolean viewOnly = isViewOnly(session);
        inventory.setItem(GuiSlots.slot(0, 0), equipmentSlot(player, layout, 36, "gui.kit-armor", viewOnly));
        inventory.setItem(GuiSlots.slot(0, 1), equipmentSlot(player, layout, 37, "gui.kit-armor", viewOnly));
        inventory.setItem(GuiSlots.slot(0, 2), equipmentSlot(player, layout, 38, "gui.kit-armor", viewOnly));
        inventory.setItem(GuiSlots.slot(0, 3), equipmentSlot(player, layout, 39, "gui.kit-armor", viewOnly));
        inventory.setItem(GuiSlots.slot(0, 5), equipmentSlot(player, layout, 40, "gui.kit-offhand", viewOnly));
        // Mockup gray 装飾 between the armor and shield slots (cols 4 and 6 are chrome).
        inventory.setItem(GuiSlots.slot(0, 4), ItemBuilder.action(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "), "decorate"));
        inventory.setItem(GuiSlots.slot(0, 6), ItemBuilder.action(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "), "decorate"));
        // Main inventory slots 9-35 -> menu rows 2-4, ALL 9 columns (27 slots exactly).
        for (int inv = 9; inv < 36; inv++) {
            int local = inv - 9;
            int row = 2 + local / 9;
            int col = local % 9;
            inventory.setItem(GuiSlots.slot(row, col), taggedSlot(player, layout[inv],
                    isViewOnly(session) ? "decorate" : "slot:" + inv, false));
        }
        // hotbar row 5
        for (int hot = 0; hot < 9; hot++) {
            inventory.setItem(GuiSlots.slot(5, hot), taggedSlot(player, layout[hot],
                    isViewOnly(session) ? "decorate" : "slot:" + hot, true));
        }
        // Decor strip. The KIT EDIT GUI itself has NO Back button — Save or Esc returns
        // (mockup 2026-10-04). Only the special flows sharing this editor keep one:
        // crystal variants go back to the KIT1..9 picker, official edits to the admin
        // config, and read-only viewers to their kit list.
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(1, col), decorPane());
        }
        if (crystalVariant(session) != null || isOfficialEdit(session) || viewOnly) {
            inventory.setItem(GuiSlots.slot(1, 4),
                    ItemBuilder.action(UiTheme.BACK, t(player, "menu.back"), "back"));
        }
        if (isViewOnly(session)) {
            inventory.setItem(GuiSlots.slot(0, 8),
                    ItemBuilder.action(UiTheme.CLOSE, t(player, "menu.close"), "close"));
        } else {
            inventory.setItem(GuiSlots.slot(0, 7), ItemBuilder.action(Material.LIME_WOOL,
                    t(player, "gui.save"), "save"));
            inventory.setItem(GuiSlots.slot(0, 8), ItemBuilder.action(Material.YELLOW_WOOL,
                    t(player, "gui.kit-reset"), "reset"));
        }
        session.put("layout", layout);
        stashCurrentLayout(player, session);
        if (!isViewOnly(session) && kit.presetEnabled() && presetItems != null) {
            schedulePresetPaletteRender(player);
        }
    }

    private void initPresetSession(GuiSession session) {
        String kitId = session.selectedKit();
        if (kitId == null || presetItems == null) {
            return;
        }
        kitService.get(kitId).ifPresent(kit -> {
            if (kit.presetEnabled()) {
                session.put("preset_category", PresetItems.CATEGORIES.getFirst());
                session.put("preset_page", 0);
            }
        });
    }

    private static int presetPage(GuiSession session) {
        Integer page = session.get("preset_page", Integer.class);
        return page == null ? 0 : Math.max(0, page);
    }

    private void setPresetPage(GuiSession session, int page) {
        session.put("preset_page", Math.max(0, page));
    }

    private void schedulePresetPaletteRender(Player player) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(EditKitGui.class);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            GuiSession live = registry.get(player.getUniqueId()).orElse(null);
            if (live == null || !isPresetEdit(live)) {
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof PracticeGuiHolder holder)
                    || holder.type() != GuiType.EDIT_KIT) {
                return;
            }
            renderPlayerPresetPalette(player, live);
        });
    }

    private void renderPlayerPresetPalette(Player player, GuiSession session) {
        String category = session.get("preset_category", String.class);
        if (category == null || !PresetItems.CATEGORIES.contains(category)) {
            category = PresetItems.CATEGORIES.getFirst();
            session.put("preset_category", category);
        }
        int page = presetPage(session);

        PlayerInventory inv = player.getInventory();
        inv.clear();
        String configEntry = session.get(K_PRESET_CONFIG, String.class);
        if (configEntry != null) {
            renderPresetItemConfig(player, session, category, configEntry, inv);
            return;
        }
        if (session.get(K_ENCHANT_STAGE, String.class) != null) {
            renderPresetEnchant(player, session, category, inv);
            return;
        }
        String kitId = session.selectedKit();
        java.util.Map<Integer, String> slotMap = presetItems.slots(kitId, category);
        // Never strand the player on an empty page: clamp to the last page with content
        // (covers stale pages left over from kit/category switches or emptied categories).
        int maxSlot = slotMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        int lastPage = maxSlot < 0 ? 0 : maxSlot / PRESET_ITEMS_PER_PAGE;
        if (page > lastPage) {
            page = lastPage;
        }
        setPresetPage(session, page);
        int start = page * PRESET_ITEMS_PER_PAGE;
        for (int i = 0; i < PRESET_ITEMS_PER_PAGE; i++) {
            int absSlot = start + i;
            String entry = slotMap.get(absSlot);
            if (entry != null) {
                inv.setItem(9 + i, presetItems.displayItem(category, entry).clone());
            }
        }
        for (int i = 0; i < PresetItems.CATEGORIES.size(); i++) {
            String cat = PresetItems.CATEGORIES.get(i);
            boolean selected = cat.equals(category);
            inv.setItem(i, GuiDecorator.button(presetTabMaterial(cat),
                    Component.text(cat, selected ? UiTheme.SUCCESS : UiTheme.VALUE),
                    "preset-cat:" + cat, selected));
        }
        inv.setItem(4, GuiDecorator.decorative(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(5, deleteGlass(player));
        inv.setItem(6, deleteGlass(player));
        if (page > 0) {
            inv.setItem(7, pageButton(line(player, "gui.page-prev"), "preset-prev"));
        }
        if (page < lastPage) {
            inv.setItem(8, pageButton(line(player, "gui.page-next"), "preset-next"));
        }
    }

    private static Material presetTabMaterial(String category) {
        return switch (com.rumilance.practice.kit.CategoryKeys.canonicalPreset(category)) {
            case "Armor" -> Material.NETHERITE_CHESTPLATE;
            case "Gear" -> Material.NETHERITE_SWORD;
            case "Potions" -> Material.SPLASH_POTION;
            case "Consumables" -> Material.GOLDEN_APPLE;
            default -> Material.CHEST;
        };
    }

    // ===================================================== More Enchant Item customiser

    /**
     * The enchantment customiser a normal player walks through when they take a preset entry
     * whose "More Enchant Item" flag is ON (user spec 2026-10-05). Everything happens in the
     * bottom inventory, in three stages:
     *
     * <ul>
     *   <li><b>list</b> — every enchantment the item can take, plus 完了. Click an enchantment
     *       that is already applied to remove it, or one that is not to pick a level.</li>
     *   <li><b>levels</b> — one button per level 1..max; choosing one applies it with the
     *       enchanting-table sound and returns to the list.</li>
     *   <li><b>result</b> — 完了 hands over the finished item: anvil sound, the item appears in
     *       the centre, and taking it drops the player back on the normal palette.</li>
     * </ul>
     */
    private void renderPresetEnchant(Player player, GuiSession session, String category,
                                     PlayerInventory inv) {
        String stage = session.get(K_ENCHANT_STAGE, String.class);
        ItemStack working = session.get(K_ENCHANT_ITEM, ItemStack.class);
        if (working == null) {
            session.put(K_ENCHANT_STAGE, null);
            renderPlayerPresetPalette(player, session);
            return;
        }
        if (STAGE_RESULT.equals(stage)) {
            inv.setItem(13, working.clone());
            return;
        }
        if (STAGE_LEVELS.equals(stage)) {
            org.bukkit.enchantments.Enchantment pick = enchantmentByKey(
                    session.get(K_ENCHANT_PICK, String.class));
            inv.setItem(0, GuiDecorator.button(UiTheme.BACK, t(player, "menu.back"),
                    "enchant:back"));
            if (pick == null) {
                session.put(K_ENCHANT_STAGE, STAGE_LIST);
                renderPlayerPresetPalette(player, session);
                return;
            }
            for (int level = 1; level <= pick.getMaxLevel() && level <= 9; level++) {
                inv.setItem(8 + level, GuiDecorator.button(Material.ENCHANTED_BOOK,
                        Component.text(EnchantmentRules.label(pick) + " " + roman(level),
                                UiTheme.VALUE).decoration(TextDecoration.ITALIC, false),
                        "enchant:level:" + level));
            }
            return;
        }
        // --- list stage ---
        inv.setItem(0, GuiDecorator.button(Material.EMERALD,
                Component.text(line(player, "gui.enchant-done"), UiTheme.SUCCESS)
                        .decoration(TextDecoration.ITALIC, false), "enchant:done"));
        inv.setItem(4, working.clone());
        java.util.List<org.bukkit.enchantments.Enchantment> options =
                EnchantmentRules.applicable(working);
        for (int i = 0; i < options.size() && i < 27; i++) {
            org.bukkit.enchantments.Enchantment ench = options.get(i);
            int level = working.getEnchantmentLevel(ench);
            boolean on = level > 0;
            inv.setItem(9 + i, GuiDecorator.button(Material.ENCHANTED_BOOK,
                    Component.text(EnchantmentRules.label(ench)
                                    + (on ? " " + roman(level) : ""),
                            on ? UiTheme.SUCCESS : UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false),
                    "enchant:" + ench.getKey().getKey()));
        }
    }

    private static org.bukkit.enchantments.Enchantment enchantmentByKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return org.bukkit.Registry.ENCHANTMENT.get(
                    org.bukkit.NamespacedKey.minecraft(key));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String roman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level < numerals.length ? numerals[level] : String.valueOf(level);
    }

    /** Clicks inside the enchantment customiser (bottom inventory). */
    private void handleEnchantClick(Player player, GuiSession session, InventoryClickEvent event) {
        int slot = event.getSlot();
        String stage = session.get(K_ENCHANT_STAGE, String.class);
        ItemStack working = session.get(K_ENCHANT_ITEM, ItemStack.class);
        if (working == null) {
            session.put(K_ENCHANT_STAGE, null);
            renderPlayerPresetPalette(player, session);
            return;
        }
        if (STAGE_RESULT.equals(stage)) {
            if (slot == 13) {
                event.getView().setCursor(working.clone());
                session.put(K_ENCHANT_STAGE, null);
                session.put(K_ENCHANT_ITEM, null);
                session.put(K_ENCHANT_PICK, null);
                renderPlayerPresetPalette(player, session);
                sounds.play(player, "gui-click");
            }
            return;
        }
        if (slot == 0) {
            if (STAGE_LEVELS.equals(stage)) {
                session.put(K_ENCHANT_STAGE, STAGE_LIST);
                renderPlayerPresetPalette(player, session);
                sounds.play(player, "gui-back");
            } else {
                // 完了 — anvil use, then hand the finished item over from the centre.
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                session.put(K_ENCHANT_STAGE, STAGE_RESULT);
                renderPlayerPresetPalette(player, session);
            }
            return;
        }
        if (STAGE_LEVELS.equals(stage)) {
            org.bukkit.enchantments.Enchantment pick =
                    enchantmentByKey(session.get(K_ENCHANT_PICK, String.class));
            if (pick == null) {
                return;
            }
            int level = slot - 8;
            java.util.Map<org.bukkit.enchantments.Enchantment, Integer> existing =
                    new java.util.HashMap<>(working.getEnchantments());
            if (!EnchantmentRules.canApply(working, pick, level, existing)) {
                sounds.play(player, "error");
                return;
            }
            working.removeEnchantment(pick);
            working.addEnchantment(pick, level);
            session.put(K_ENCHANT_ITEM, working);
            // Same sound as enchanting at an enchanting table.
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            session.put(K_ENCHANT_STAGE, STAGE_LIST);
            renderPlayerPresetPalette(player, session);
            return;
        }
        // --- list stage: an enchantment tile ---
        if (slot >= 9 && slot <= 35) {
            java.util.List<org.bukkit.enchantments.Enchantment> options =
                    EnchantmentRules.applicable(working);
            int index = slot - 9;
            if (index >= options.size()) {
                return;
            }
            org.bukkit.enchantments.Enchantment ench = options.get(index);
            if (working.getEnchantmentLevel(ench) > 0) {
                // Already applied — clicking removes it.
                working.removeEnchantment(ench);
                session.put(K_ENCHANT_ITEM, working);
                sounds.play(player, "delete");
                renderPlayerPresetPalette(player, session);
                return;
            }
            java.util.Map<org.bukkit.enchantments.Enchantment, Integer> existing =
                    new java.util.HashMap<>(working.getEnchantments());
            if (!EnchantmentRules.canApply(working, ench, 1, existing)) {
                sounds.play(player, "error");
                return;
            }
            session.put(K_ENCHANT_PICK, ench.getKey().getKey());
            session.put(K_ENCHANT_STAGE, STAGE_LEVELS);
            renderPlayerPresetPalette(player, session);
            sounds.play(player, "gui-click");
        }
    }

    /**
     * Per-item config screen for one preset candidate (user spec 2026-10-05): opened by
     * RIGHT-CLICKING the entry in the palette. It lives in the bottom inventory like the
     * palette itself, so no second GUI type is needed.
     *
     * <ul>
     *   <li>(0) Back — returns to the palette.</li>
     *   <li>(4) the entry itself, as a preview.</li>
     *   <li>(20) Removable — may an OP drop this entry out of the pool?</li>
     *   <li>(24) More Enchant Item — only offered when the entry can take enchantments and
     *       carries none yet. ON means a normal user taking it gets the enchantment
     *       customiser first. Default OFF.</li>
     * </ul>
     */
    private void renderPresetItemConfig(Player player, GuiSession session, String category,
                                        String entry, PlayerInventory inv) {
        ItemStack preview = presetItems.displayItem(category, entry);
        inv.setItem(0, GuiDecorator.button(UiTheme.BACK, t(player, "menu.back"), "preset-cfg:back"));
        inv.setItem(4, preview.clone());
        inv.setItem(20, configToggle(player, "gui.preset-removable", "gui.preset-removable-hint",
                presetItems.isRemovable(category, entry), "preset-cfg:removable"));
        if (canCarryEnchantments(preview)) {
            inv.setItem(24, configToggle(player, "gui.preset-more-enchant",
                    "gui.preset-more-enchant-hint",
                    presetItems.isMoreEnchant(category, entry), "preset-cfg:more-enchant"));
        }
    }

    private ItemStack configToggle(Player player, String nameKey, String hintKey, boolean on,
                                   String action) {
        return GuiDecorator.button(on ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(line(player, nameKey), on ? UiTheme.SUCCESS : UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false), action);
    }

    /**
     * "More Enchant Item" only makes sense for something that CAN be enchanted and is not
     * enchanted yet — a pre-enchanted sword is already finished.
     */
    private static boolean canCarryEnchantments(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Material type = stack.getType();
        if (type == Material.ENCHANTED_BOOK || type == Material.BOOK) {
            return false;
        }
        if (stack.hasItemMeta() && stack.getItemMeta().hasEnchants()) {
            return false;
        }
        String n = type.name();
        return type.getMaxDurability() > 0
                || n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE")
                || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || type == Material.BOW || type == Material.CROSSBOW || type == Material.TRIDENT
                || type == Material.MACE || type == Material.FISHING_ROD
                || type == Material.SHIELD || type == Material.ELYTRA;
    }

    private ItemStack deleteGlass(Player player) {
        ItemStack stack = GuiDecorator.button(Material.BLUE_STAINED_GLASS_PANE,
                t(player, "gui.delete"), "preset-delete");
        stack.editMeta(meta -> meta.lore(List.of(
                Component.text(line(player, "gui.delete-hint"), UiTheme.MUTED))));
        return stack;
    }

    private ItemStack pageButton(String name, String action) {
        return GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                Component.text(name, UiTheme.SUCCESS), action);
    }

    /** The pristine official layout (what a brand-new player receives). */
    private ItemStack[] defaultLayout(KitDefinition kit) {
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

    /** The session's crystal variant slot (1..9), or null when editing the base layout. */
    private static Integer crystalVariant(GuiSession session) {
        return session == null ? null : session.get("crystal", Integer.class);
    }

    /** The general-kit K1..K4 slot being edited, or null for the kit's base layout. */
    private static Integer variantSlot(GuiSession session) {
        Integer v = session == null ? null : session.get("variant-slot", Integer.class);
        return v == null ? null : KitVariantsStore.clamp(v);
    }

    private ItemStack[] loadLayout(UUID uuid, KitDefinition kit) {
        return loadLayout(uuid, kit, null, null, null, false);
    }

    /** Variant-aware load: {@code crystal} 1..9 reads {@code <kit>#v<n>} (falls back to the
     * kit's official layout when that slot was never saved). */
    private ItemStack[] loadLayout(UUID uuid, KitDefinition kit, Integer crystal) {
        return loadLayout(uuid, kit, crystal, null, null, false);
    }

    /**
     * 中キット-aware load: a preset's loadout is shared by everybody and lives in kits.yml, so it
     * wins over the crystal variant slots. An ADMIN edits that shared loadout itself; anybody else
     * edits their own arrangement of it — positions only, the items stay the admin's — which is
     * stored per player under a composite layout key. The array is cloned because the editor writes
     * slots in place.
     */
    private ItemStack[] loadLayout(UUID uuid, KitDefinition kit, Integer crystal,
                                   Integer variantSlot, String innerKit, boolean personal) {
        if (innerKits != null && !com.rumilance.practice.kit.InnerKitService.isDefault(innerKit)) {
            ItemStack[] preset = innerKits.layout(kit.name(), innerKit).orElse(null);
            if (preset != null) {
                if (!personal) {
                    return preset.clone();
                }
                ItemStack[] mine = personalPresetLayout(uuid, kit.name(), innerKit, preset);
                return mine != null ? mine : preset.clone();
            }
        }
        // Storage key priority: crystal slot (#v) > general variant slot (#k) > base layout.
        String key = crystal != null ? CrystalFfaStore.variantKey(kit.name(), crystal)
                : variantSlot != null ? KitVariantsStore.variantKey(kit.name(), variantSlot)
                : kit.name();
        try {
            var snap = layoutRepository.find(uuid, key);
            if (snap.isPresent()) {
                return KitLayoutDelta.decode(snap.get().itemDataBase64(), kit);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return defaultLayout(kit);
    }

    /**
     * The player's saved arrangement of one 中キット, re-applied to the preset's CURRENT contents so
     * a later admin edit of the preset still decides what is in the fight.
     */
    private ItemStack[] personalPresetLayout(UUID uuid, String kitId, String innerKit,
                                             ItemStack[] preset) {
        String key = com.rumilance.practice.kit.InnerKitService.layoutKey(kitId, innerKit);
        layoutCache.loadSyncIfAbsent(uuid, key);
        ItemStack[] saved = layoutCache.get(uuid, key).orElse(null);
        return saved == null
                ? null : com.rumilance.practice.kit.KitLoadout.reorder(preset, saved);
    }

    /**
     * Creating, deleting and rewriting a 中キット stays an admin job. A player who opens one only
     * moves its items around for themselves — the item kinds and amounts cannot change.
     */
    private boolean personalPresetEdit(Player player) {
        return player == null || !player.hasPermission("rumilance.admin");
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

    public boolean isEditorMode(GuiSession session) {
        return session != null
                && session.selectedKit() != null
                && !"picker".equals(session.get("mode", String.class))
                && !isViewOnly(session);
    }

    /** Plain decorated lime pane — the KIT EDIT GUI mockup's row-1 decoration strip. */
    /**
     * The mockup's row-1 strip. docs/design/gui.json「KIT EDIT GUI」row 1 is
     * {@code green_stained_glass_pane} ×9 — NOT lime. Lime is a noticeably brighter,
     * yellower green, and the two are different materials.
     */
    private static ItemStack decorPane() {
        return ItemBuilder.action(Material.GREEN_STAINED_GLASS_PANE,
                Component.text(" "), "decorate");
    }

    /**
     * Placeholder for an empty kit slot (docs/design/gui.json「KIT EDIT GUI」rows 2-5):
     * a plain {@code glass_pane} named ここにキットの中身. KitLayoutContents treats it as a
     * placeholder, so it can never be saved into the kit as a real item.
     */
    private ItemStack contentPlaceholder(Player player, boolean hotbar) {
        return ItemBuilder.of(Material.GLASS_PANE)
                .name(t(player, hotbar ? "gui.kit-content-hotbar" : "gui.kit-content")
                        .color(UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false))
                .build();
    }

    /**
     * Armour / off-hand cell (docs/design/gui.json「KIT EDIT GUI」row 0): the real item when the
     * kit carries one, otherwise a labelled glass pane — the mockup names these cells 装備 and
     * OFF HAND, and leaving them empty just punched holes in the row. The pane is a
     * {@link com.rumilance.practice.kit.KitLayoutContents#isPlaceholder(ItemStack)} material, so
     * {@code KitLayoutEditor#itemFromDisplay} reads it back as "no item" and it can never be
     * saved into the kit.
     */
    private ItemStack equipmentSlot(Player player, ItemStack[] layout, int index, String nameKey,
                                    boolean viewOnly) {
        String action = viewOnly ? "decorate" : "slot:" + index;
        ItemStack stack = index < layout.length ? layout[index] : null;
        if (stack != null && !stack.getType().isAir()) {
            return taggedNonEmpty(player, stack, action);
        }
        ItemStack pane = ItemBuilder.of(Material.GLASS_PANE)
                .name(t(player, nameKey).color(UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false))
                .build();
        pane.editMeta(meta -> meta.getPersistentDataContainer().set(ItemKeys.guiAction(),
                PersistentDataType.STRING, action));
        return pane;
    }

    /**
     * Same as {@link #taggedNonEmpty(Player, ItemStack, String)} but keeps an empty content slot
     * visible: the mockup fills every unused kit cell with a ここにキットの中身 glass pane
     * instead of leaving a hole. The action string survives so the click still resolves to
     * the underlying slot.
     */
    private ItemStack taggedSlot(Player player, ItemStack stack, String action, boolean hotbar) {
        if (stack == null || stack.getType().isAir()) {
            ItemStack placeholder = contentPlaceholder(player, hotbar);
            ItemMeta meta = placeholder.getItemMeta();
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(),
                    PersistentDataType.STRING, action);
            placeholder.setItemMeta(meta);
            return placeholder;
        }
        return taggedNonEmpty(player, stack, action);
    }

    private ItemStack taggedNonEmpty(Player player, ItemStack stack, String action) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        // One hint line per item, never two: a full kit of tools and armour used to
        // stack "右クリックでトリム" over "右クリックで改名" on every second slot.
        String hintKey = null;
        if (!"decorate".equals(action)) {
            boolean trim = stack.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta;
            boolean rename = com.rumilance.practice.gui.KitAnvilRenameService
                    .isRenameableTool(stack.getType());
            if (trim && rename) {
                hintKey = "gui.kit-trim-rename-hint";
            } else if (trim) {
                hintKey = "gui.kit-trim-hint";
            } else if (rename) {
                hintKey = "gui.kit-rename-hint";
            }
        }
        java.util.ArrayList<Component> extra = new java.util.ArrayList<>();
        if (hintKey != null) {
            extra.add(t(player, hintKey).color(UiTheme.MUTED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return KitLayoutEditor.tagLayoutItem(stack, action, extra);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        handleClick(player, session, inventory, slot, action, org.bukkit.event.inventory.ClickType.LEFT);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (isViewOnly(session) && !"back".equals(action) && !"close".equals(action)) {
            return;
        }
        // 中キット: キット一覧での右クリック。Admin は管理画面、プレイヤーは配置変更の一覧へ
        //（左クリックは今まで通り即編集）。
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("editkit:")
                && innerKitSelectGui != null) {
            String pickedKit = action.substring("editkit:".length());
            if (kitService.isFolder(pickedKit)) {
                session.setNavigatingAway(true);
                innerKitSelectGui.openForEdit(player, session, pickedKit);
                return;
            }
            if (innerKitAdminGui != null && player.hasPermission("rumilance.admin")) {
                session.setNavigatingAway(true);
                innerKitAdminGui.open(player, pickedKit, InnerKitAdminGui.ORIGIN_EKIT);
                return;
            }
        }
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("slot:")) {
            int layoutIndex = Integer.parseInt(action.substring(5));
            ItemStack[] layout = session.get("layout", ItemStack[].class);
            if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length) {
                ItemStack item = layout[layoutIndex];
                String kitId = session.selectedKit();
                String preset = session.get("preset", String.class);
                // Shield: VIP+ opens the banner-pattern editor (hidden custom_shield holders
                // keep their operator artwork and cannot restyle it). Right-click still opens
                // the anvil rename via the tool path below when the editor does not apply.
                if (item != null && item.getType() == org.bukkit.Material.SHIELD && kitId != null) {
                    if (hiddenRankService != null && hiddenRankService.hasCustomShield(player.getUniqueId())) {
                        player.sendMessage(line(player, "gui.shield-custom-locked"));
                        sounds.play(player, "error");
                        return;
                    }
                    if (shieldPatternGui != null && rankService != null
                            && rankService.isVipPlusOrAbove(player)) {
                        stashCurrentLayout(player, session);
                        shieldPatternGui.openForLayoutSlot(player, item, layoutIndex, kitId, preset, layout);
                        return;
                    }
                }
                stashCurrentLayout(player, session);
                if (item != null && kitAnvilRenameService != null
                        && com.rumilance.practice.gui.KitAnvilRenameService.isRenameableTool(item.getType())
                        && kitAnvilRenameService.tryOpenRename(player, item, layoutIndex, kitId, preset, layout)) {
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
            if (isViewOnly(session)) {
                if ("back".equals(action) && ekitSelectGui != null && session.targetPlayer() != null) {
                    session.setNavigatingAway(true);
                    ekitSelectGui.openViewer(player, session.targetPlayer(),
                            session.get("viewer-target-name", String.class));
                    return;
                }
                player.closeInventory();
                return;
            }
            restoreLobbyHands(player);
            if (kitEditStash != null) {
                kitEditStash.clear(player.getUniqueId());
            }
            if ("back".equals(action) && isOfficialEdit(session) && kitAdminGui != null) {
                session.setNavigatingAway(true);
                stateManager.resetToLobby(player.getUniqueId());
                kitAdminGui.openConfig(player, session.selectedKit());
                return;
            }
            if ("back".equals(action) && crystalVariant(session) != null
                    && crystalKitSlotsGui != null) {
                // Editing a crystal variant (KIT1..9): BACK goes to that kit's full variant
                // list, not the top-level kit chooser.
                session.setNavigatingAway(true);
                stateManager.resetToLobby(player.getUniqueId());
                crystalKitSlotsGui.openPicker(player, session.selectedKit());
                return;
            }
            if ("back".equals(action) && ekitSelectGui != null) {
                session.setNavigatingAway(true);
                stateManager.resetToLobby(player.getUniqueId());
                String backCategory = session.get("back-category", String.class);
                Integer backPage = session.get("back-page", Integer.class);
                if (backCategory != null) {
                    ekitSelectGui.openAt(player, backCategory,
                            backPage == null ? 0 : backPage);
                } else {
                    ekitSelectGui.open(player);
                }
                return;
            }
            stateManager.resetToLobby(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (action.startsWith("editkit:")) {
            session.setSelectedKit(kitService.playableId(action.substring(8)));
            session.put("mode", "edit");
            initPresetSession(session);
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
            stashCurrentLayout(player, session);
            render(player, session, inventory);
            return;
        }
        if ("save".equals(action)) {
            save(player, session, inventory);
            return;
        }
        if ("reset".equals(action)) {
            resetLayout(player, session, inventory);
            return;
        }
    }

    /**
     * "初期状態にリセット": discard every user rearrangement, rebuild the pristine
     * official kit layout in-session, and drop the stored delta row so the default
     * loads everywhere else too.
     */
    private void resetLayout(Player player, GuiSession session, Inventory inventory) {
        String kitId = session == null ? null : session.selectedKit();
        KitDefinition kit = kitId == null ? null : kitService.get(kitId).orElse(null);
        if (kit == null) {
            return;
        }
        ItemStack[] fresh = defaultLayout(kit);
        if (isOfficialEdit(session)) {
            // Reset an admin draft to the *current official contents*. No personal DB row is
            // touched, and the shared kit is changed only when Save is pressed.
            session.put("layout", fresh);
            stashCurrentLayout(player, session);
            render(player, session, inventory);
            sounds.play(player, "gui-click");
            return;
        }
        String inner = innerKit(session);
        if (innerKits != null && !com.rumilance.practice.kit.InnerKitService.isDefault(inner)) {
            // プリセットの「初期状態」= 保存済みの中身（無ければキット本体の構成）。
            ItemStack[] stored = innerKits.layout(kitId, inner).orElse(null);
            fresh = stored != null ? stored.clone() : fresh;
            if (personalPresetEdit(player)) {
                // プレイヤーの場合は「自分の並び」だけ捨てて、プリセット本来の配置に戻す。
                // kits.yml の共有プリセットには触らない。
                String personalKey = com.rumilance.practice.kit.InnerKitService.layoutKey(kitId, inner);
                layoutCache.invalidate(player.getUniqueId(), personalKey);
                asyncExecutor.execute(() -> {
                    try {
                        layoutRepository.delete(player.getUniqueId(), personalKey);
                    } catch (Exception ignored) {
                        // 未保存なら消すものが無いだけ。
                    }
                });
            }
            session.put("layout", fresh);
            stashCurrentLayout(player, session);
            render(player, session, inventory);
            sounds.play(player, "gui-click");
            player.sendMessage(t(player, "gui.kit-reset-done"));
            return;
        }
        session.put("layout", fresh);
        stashCurrentLayout(player, session);
        layoutCache.put(player.getUniqueId(), kitId, fresh);
        asyncExecutor.execute(() -> {
            try {
                layoutRepository.delete(player.getUniqueId(), kitId);
            } catch (Exception ignored) {
                // Deleting a not-yet-saved row failing silently is fine: default loads anyway.
            }
        });
        render(player, session, inventory);
        sounds.play(player, "gui-click");
        player.sendMessage(t(player, "gui.kit-reset-done"));
    }

    /**
     * Shift-click reset from the KIT SELECT GUI chips (user spec 2026-10-04): drops the
     * player's saved K{@code variant} row so that slot falls back to the kit's official
     * layout in the editor, duels and FFA alike. The Active choice is NOT touched.
     */
    public void resetVariantSlot(Player player, String kitId, int variant) {
        KitDefinition kit = kitId == null ? null : kitService.get(kitId).orElse(null);
        if (kit == null || player == null) {
            return;
        }
        String key = KitVariantsStore.variantKey(kitId, KitVariantsStore.clamp(variant));
        layoutCache.invalidate(player.getUniqueId(), key);
        asyncExecutor.execute(() -> {
            try {
                layoutRepository.delete(player.getUniqueId(), key);
            } catch (Exception ignored) {
                // Nothing saved yet: there is no row to delete, the default loads anyway.
            }
        });
        player.sendMessage(t(player, "gui.kit-reset-done"));
    }

    /** Clicks inside the per-item preset config screen (bottom inventory). */
    private void handlePresetConfigClick(Player player, GuiSession session,
                                         InventoryClickEvent event, String entry) {
        int slot = event.getSlot();
        String category = session.get("preset_category", String.class);
        if (slot == 0) {
            session.put(K_PRESET_CONFIG, null);
            renderPlayerPresetPalette(player, session);
            sounds.play(player, "gui-back");
            return;
        }
        if (slot == 20) {
            presetItems.setRemovable(category, entry,
                    !presetItems.isRemovable(category, entry));
            sounds.play(player, "gui-click");
            renderPlayerPresetPalette(player, session);
            return;
        }
        if (slot == 24) {
            ItemStack preview = presetItems.displayItem(category, entry);
            if (!canCarryEnchantments(preview)) {
                sounds.play(player, "error");
                return;
            }
            presetItems.setMoreEnchant(category, entry,
                    !presetItems.isMoreEnchant(category, entry));
            sounds.play(player, "gui-click");
            renderPlayerPresetPalette(player, session);
            return;
        }
        if (slot == 4) {
            // The preview is decorative — treat a click on it as "take the item".
            event.getView().setCursor(presetItems.displayItem(category, entry).clone());
            session.put(K_PRESET_CONFIG, null);
            renderPlayerPresetPalette(player, session);
            sounds.play(player, "gui-click");
        }
    }

    @Override
    public void handleBottomClick(Player player, GuiSession session, InventoryClickEvent event) {
        if (!isPresetEdit(session)) {
            return;
        }
        String configEntry = session.get(K_PRESET_CONFIG, String.class);
        if (configEntry != null) {
            handlePresetConfigClick(player, session, event, configEntry);
            return;
        }
        if (session.get(K_ENCHANT_STAGE, String.class) != null) {
            handleEnchantClick(player, session, event);
            return;
        }
        int slot = event.getSlot();
        ItemStack cursor = event.getCursor();
        if (slot >= 9 && slot <= 35) {
            if (cursor != null && !cursor.getType().isAir()) {
                return;
            }
            String category = session.get("preset_category", String.class);
            int page = presetPage(session);
            java.util.Map<Integer, String> slotMap = presetItems.slots(session.selectedKit(), category);
            int absSlot = page * PRESET_ITEMS_PER_PAGE + (slot - 9);
            String entry = slotMap.get(absSlot);
            if (entry == null) {
                return;
            }
            ItemStack item = presetItems.displayItem(category, entry);
            if (item == null) {
                return;
            }
            // RIGHT-click opens the entry's own config screen (user spec 2026-10-05).
            org.bukkit.event.inventory.ClickType click = event.getClick();
            if (click == org.bukkit.event.inventory.ClickType.RIGHT
                    || click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                session.put(K_PRESET_CONFIG, entry);
                renderPlayerPresetPalette(player, session);
                sounds.play(player, "gui-open");
                return;
            }
            // "More Enchant Item": a normal player customises the enchantments before the
            // item reaches their cursor. OPs (who curate the pool) still get it raw.
            if (presetItems.isMoreEnchant(category, entry)
                    && !player.hasPermission("rumilance.admin")) {
                session.put(K_ENCHANT_ENTRY, entry);
                session.put(K_ENCHANT_ITEM, item.clone());
                session.put(K_ENCHANT_STAGE, STAGE_LIST);
                session.put(K_ENCHANT_PICK, null);
                renderPlayerPresetPalette(player, session);
                sounds.play(player, "gui-open");
                return;
            }
            event.getView().setCursor(item.clone());
            session.put("preset_cursor_entry", entry);
            sounds.play(player, "gui-click");
            return;
        }
        if (slot <= 8) {
            if (slot >= 0 && slot <= 3) {
                List<String> categories = PresetItems.CATEGORIES;
                if (slot < categories.size()) {
                    session.put("preset_category", categories.get(slot));
                    session.put("preset_page", 0);
                    renderPlayerPresetPalette(player, session);
                    sounds.play(player, "gui-click");
                }
                return;
            }
            if (slot == 7 && presetPage(session) > 0) {
                setPresetPage(session, presetPage(session) - 1);
                renderPlayerPresetPalette(player, session);
                sounds.play(player, "gui-click");
                return;
            }
            if (slot == 8) {
                String category = session.get("preset_category", String.class);
                int page = presetPage(session);
                java.util.Map<Integer, String> slotMap = presetItems.slots(session.selectedKit(), category);
                int maxSlot = slotMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                if ((page + 1) * PRESET_ITEMS_PER_PAGE <= maxSlot) {
                    setPresetPage(session, page + 1);
                    renderPlayerPresetPalette(player, session);
                    sounds.play(player, "gui-click");
                }
                return;
            }
            if (slot == 5 || slot == 6) {
                if (cursor != null && !cursor.getType().isAir()) {
                    // Only entries an OP marked removable may be thrown away.
                    String taken = session.get("preset_cursor_entry", String.class);
                    if (taken != null && !presetItems.isRemovable(
                            session.get("preset_category", String.class), taken)) {
                        sounds.play(player, "error");
                        return;
                    }
                    session.put("preset_cursor_entry", null);
                    event.getView().setCursor(null);
                    sounds.play(player, "delete");
                }
            }
        }
    }

    /**
     * Save, debounced for 1 second per open editor: the async upsert used to be queued once
     * per spam-click, so a player machine-gunning Save could overrun the store with stale
     * envelopes. A successful save plays the level-up jingle and closes the screen (the
     * lobby inventory comes back, the overlay stash and the session are released) — the
     * same flow {@link #forceSaveForMatch} already relied on.
     */
    private void save(Player player, GuiSession session, Inventory inventory) {
        long now = System.currentTimeMillis();
        Long lastSave = session == null ? null : session.get("save_at", Long.class);
        if (lastSave != null && now - lastSave < 1000L) {
            return;
        }
        // Refuse to save while an item is still on the cursor: the cursor is not part of the
        // layout, so saving would close the GUI and destroy it (the "my item vanished" bug).
        if (player.getItemOnCursor() != null
                && !player.getItemOnCursor().getType().isAir()) {
            sounds.play(player, "error");
            player.sendMessage(t(player, "gui.kit-save-cursor-blocked"));
            return;
        }
        if (session != null) {
            session.put("save_at", now);
        }
        boolean ok = persistLayout(player, session, false);
        if (!ok) {
            sounds.play(player, "error");
            player.sendMessage(t(player, "gui.save-failed"));
            return;
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 1.0f);
        player.sendMessage(t(player, "gui.kit-saved"));
        restoreLobbyHands(player);
        if (kitEditStash != null) {
            kitEditStash.clear(player.getUniqueId());
        }
        // The KIT EDIT GUI has no Back: Save is one of its two exits (mockup: Save or Esc
        // で戻る). When the editor was opened from the KIT SELECT GUI, land back on that
        // page; every other entry point keeps the plain close-to-lobby behaviour.
        String backCategory = session.get("back-category", String.class);
        Integer backPage = session.get("back-page", Integer.class);
        if (crystalVariant(session) == null && !isOfficialEdit(session)
                && backCategory != null && ekitSelectGui != null) {
            session.setNavigatingAway(true);
            stateManager.resetToLobby(player.getUniqueId());
            ekitSelectGui.openAt(player, backCategory, backPage == null ? 0 : backPage);
            return;
        }
        registry.close(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Writes the current kit layout to DB. Always succeeds when kit/layout exist — no
     * rearrange-only gate. Used by Save and trim apply.
     */
    /**
     * Writes the current editor layout out. @return true when the write was accepted
     * (the async store enqueue counts) — used by the debounced Save to decide between the
     * close-and-jingle path and the stay-open-with-error path.
     */
    public boolean persistLayout(Player player, GuiSession session, boolean notify) {
        String kitId = session == null ? null : session.selectedKit();
        if (kitId == null) {
            return false;
        }
        ItemStack[] layout = resolveLayoutForSave(player, session);
        if (isOfficialEdit(session)) {
            return saveOfficial(player, kitId, layout, notify);
        }
        // 中キットの編集: 保存先はプレイヤー個人のレイアウト(DB)ではなく kits.yml のプリセット。
        // 個人レイアウトに書くとその人だけの並び替えになり、他の人には反映されない。
        String inner = innerKit(session);
        if (innerKits != null && layout != null
                && !com.rumilance.practice.kit.InnerKitService.isDefault(inner)) {
            if (personalPresetEdit(player)) {
                // 一般プレイヤーは kits.yml を触れない：自分の並びだけを個人レイアウトに保存する。
                persistPersonalPreset(player, session, kitId, inner, layout, notify);
                return true;
            }
            boolean saved = innerKits.saveLayout(kitId, inner, layout);
            if (notify) {
                if (saved) {
                    sounds.play(player, "select");
                    player.sendMessage(t(player, "gui.innerkit-saved"));
                } else {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "gui.innerkit-save-failed"));
                }
            }
            return saved;
        }
        return persistLayout(player, kitId, layout, notify, crystalVariant(session),
                variantSlot(session));
    }

    private boolean saveOfficial(Player player, String kitId, ItemStack[] layout, boolean notify) {
        if (!player.hasPermission("rumilance.admin") || layout == null) {
            if (notify) {
                player.sendMessage(t(player, "general.no-permission"));
            }
            return false;
        }
        boolean saved = kitService.setOfficialLoadout(kitId, layout);
        if (notify) {
            sounds.play(player, saved ? "select" : "error");
            player.sendMessage(t(player, saved
                    ? "gui.innerkit-official-saved" : "gui.innerkit-save-failed"));
        }
        return saved;
    }

    /**
     * Saves one player's own arrangement of a 中キット. The row holds the preset's items placed the
     * way that player wants them, under a composite key ({@code kit#preset#id}) so it can never
     * collide with the kit's own layout or a crystal variant slot. Items that are not part of the
     * preset are dropped here already, so even a tampered editor cannot add anything to the fight.
     */
    private void persistPersonalPreset(Player player, GuiSession session, String kitId, String inner,
                                       ItemStack[] layout, boolean notify) {
        String key = com.rumilance.practice.kit.InnerKitService.layoutKey(kitId, inner);
        ItemStack[] preset = innerKits.layout(kitId, inner).orElse(null);
        ItemStack[] ordered = preset == null
                ? layout.clone() : com.rumilance.practice.kit.KitLoadout.reorder(preset, layout);
        if (session != null) {
            // 保存するのは「プリセットの中身を自分の並びに置き直した結果」。エディタの元データも
            // それに合わせる（足したアイテムは消え、減らしたアイテムは戻る）。
            session.put("layout", ordered.clone());
            stashCurrentLayout(player, session);
        }
        // kit=null は「フルbase64で保存」の意味。差分はキット本体との差になってしまうため使えない。
        KitLayoutSnapshot snap = KitLayoutSnapshot.create(player.getUniqueId(), key,
                KitLayoutDelta.encode(ordered, null));
        layoutCache.put(player.getUniqueId(), key, ordered);
        asyncExecutor.execute(() -> {
            try {
                layoutRepository.upsert(snap);
                if (!notify) {
                    return;
                }
                player.getServer().getScheduler().runTask(
                        JavaPlugin.getProvidingPlugin(EditKitGui.class),
                        () -> {
                            sounds.play(player, "select");
                            player.sendMessage(t(player, "gui.innerkit-order-saved"));
                        });
            } catch (Exception e) {
                if (!notify) {
                    return;
                }
                player.getServer().getScheduler().runTask(
                        JavaPlugin.getProvidingPlugin(EditKitGui.class),
                        () -> player.sendMessage(t(player, "gui.save-failed")));
            }
        });
    }

    public boolean persistLayout(Player player, String kitId, ItemStack[] layout, boolean notify) {
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        return persistLayout(player, kitId, layout, notify,
                crystalVariant(session), variantSlot(session));
    }

    public boolean persistLayout(Player player, String kitId, ItemStack[] layout, boolean notify,
                              Integer crystalVariant) {
        return persistLayout(player, kitId, layout, notify, crystalVariant, null);
    }

    public boolean persistLayout(Player player, String kitId, ItemStack[] layout, boolean notify,
                              Integer crystalVariant, Integer variantSlot) {
        GuiSession current = registry.get(player.getUniqueId()).orElse(null);
        if (isOfficialEdit(current) || (kitEditStash != null
                && kitEditStash.get(player.getUniqueId()) != null
                && kitEditStash.get(player.getUniqueId()).officialEdit())) {
            return saveOfficial(player, kitId, layout, notify);
        }
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null || layout == null) {
            return false;
        }
        // Same key priority as loadLayout: crystal (#v) > general variant (#k) > base kit.
        String storeKey = crystalVariant != null
                ? CrystalFfaStore.variantKey(kitId, crystalVariant)
                : variantSlot != null ? KitVariantsStore.variantKey(kitId, variantSlot) : kitId;
        // Delta storage: only the differences from the kit's official layout are persisted,
        // which keeps the DB rows tiny (legacy full base64 still decodes fine).
        String base64 = KitLayoutDelta.encode(layout, kit);
        KitLayoutSnapshot snap = KitLayoutSnapshot.create(player.getUniqueId(), storeKey, base64);
        layoutCache.put(player.getUniqueId(), storeKey, layout);
        asyncExecutor.execute(() -> {
            try {
                layoutRepository.upsert(snap);
                if (!notify) {
                    return;
                }
                player.getServer().getScheduler().runTask(
                        JavaPlugin.getProvidingPlugin(EditKitGui.class),
                        () -> {
                            sounds.play(player, "select");
                            player.sendMessage(t(player, "gui.kit-saved"));
                        });
            } catch (Exception e) {
                if (!notify) {
                    return;
                }
                player.getServer().getScheduler().runTask(
                        JavaPlugin.getProvidingPlugin(EditKitGui.class),
                        () -> player.sendMessage(t(player, "gui.save-failed")));
            }
        });
        return true;
    }

    private ItemStack[] resolveLayoutForSave(Player player, GuiSession session) {
        // The session layout is the live arrangement the player is looking at; the overlay
        // stash (anvil/smithing carry-bag) is only a fallback — preferring the stash used
        // to resurrect an older layout and silently discard post-overlay rearrangements.
        ItemStack[] layout = null;
        if (session != null) {
            layout = session.get("layout", ItemStack[].class);
        }
        if (layout == null && kitEditStash != null) {
            layout = kitEditStash.layoutCopy(player.getUniqueId());
        }
        if (layout == null) {
            return null;
        }
        layout = layout.clone();
        if (player.getOpenInventory().getTopInventory().getHolder()
                instanceof com.rumilance.practice.gui.PracticeGuiHolder holder
                && holder.type() == GuiType.EDIT_KIT) {
            KitLayoutEditor.syncLayoutFromTopInventory(player.getOpenInventory().getTopInventory(), layout);
            // BUGFIX: a save clicked while an item rides the cursor used to drop that
            // item from the kit entirely. Absorb it into a free slot (armor slots win,
            // then storage, then hotbar) and take it off the cursor instead.
            ItemStack cursor = player.getOpenInventory().getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (KitLayoutEditor.addToLayout(layout, KitLayoutEditor.stripEditorTags(cursor.clone()))) {
                    player.getOpenInventory().setCursor(null);
                }
            }
        }
        if (session != null) {
            session.put("layout", layout);
        }
        return layout;
    }

}
