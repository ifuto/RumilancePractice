package com.rumilance.practice.originalkit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.OriginalKitRepository;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import com.rumilance.practice.model.OriginalKitSettings;
import com.rumilance.practice.model.OriginalKitSnapshot;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Original kit management: per-paper-slot kits, plan-gated slot unlocks, per-slot battle
 * settings, and the inventory stash/restore lifecycle used by the physical kit room.
 *
 * <p>Plans: DEFAULT (1 paper) / MEMBER (5 papers) / VIP (cross, svip is an alias of vip) /
 * VIP_PLUS (all except slot 44).</p>
 */
public final class OriginalKitService {

    public enum Plan { DEFAULT, MEMBER, VIP, VIP_PLUS }

    private final OriginalKitRepository repository;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;
    private final ConfigService configService;
    private volatile OriginalKitRoomService roomService;
    private final Map<UUID, ItemStack[]> pendingInventory = new ConcurrentHashMap<>();
    private final Map<UUID, EditContext> editContexts = new ConcurrentHashMap<>();
    private final Set<UUID> navigating = ConcurrentHashMap.newKeySet();

    /** Mutable per-player editor state while an OrPlusGUI is open. */
    public static final class EditContext {
        public final int slot;
        public final ItemStack[] layout;
        public String category;
        public int page;
        public Integer selectedSlot;
        public boolean suppressRestore;

        public EditContext(int slot, ItemStack[] layout) {
            this.slot = slot;
            this.layout = layout;
            this.category = com.rumilance.practice.kit.CategoryKeys.EKIT.getFirst();
            this.page = 0;
        }
    }

    public OriginalKitService(OriginalKitRepository repository, AsyncExecutor asyncExecutor,
                              Logger logger, ConfigService configService) {
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
        this.configService = configService;
    }

    public Plan planOf(Player player) {
        if (player.hasPermission("rumilance.user.vip_plus")) {
            return Plan.VIP_PLUS;
        }
        if (player.hasPermission("rumilance.user.vip") || player.hasPermission("rumilance.user.svip")) {
            return Plan.VIP;
        }
        if (player.hasPermission("rumilance.user.mem")) {
            return Plan.MEMBER;
        }
        return Plan.DEFAULT;
    }

    public boolean isSlotUnlocked(Plan plan, int slot) {
        return switch (plan) {
            case DEFAULT -> slot == 22;
            case MEMBER -> slot == 13 || slot == 21 || slot == 22 || slot == 23 || slot == 31;
            case VIP -> isVipCross(slot);
            case VIP_PLUS -> slot != 44;
        };
    }

    private static boolean isVipCross(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return (row == 0 && col == 4)
                || (row == 1 && col >= 3 && col <= 5)
                || (row == 2 && col >= 2 && col <= 6)
                || (row == 3 && col >= 3 && col <= 5)
                || (row == 4 && col == 4);
    }

    /** Barrier lore for a locked slot, from the viewer's plan. */
    public String barrierLabel(Plan viewerPlan, int slot) {
        if (viewerPlan == Plan.VIP || viewerPlan == Plan.VIP_PLUS) {
            return "VIP以上で開放";
        }
        return isVipCross(slot) ? "SVIP以上で開放" : "VIPで開放";
    }

    public boolean hasSaved(UUID uuid, int slot) {
        return find(uuid, slot).isPresent();
    }

    public Optional<OriginalKitSnapshot> find(UUID uuid, int slot) {
        try {
            return repository.find(uuid, slot);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed loading original kit " + uuid + "/" + slot, e);
            return Optional.empty();
        }
    }

    /** The player's most recently saved original kit (any paper slot), if any. */
    public Optional<OriginalKitSnapshot> latestSaved(UUID uuid) {
        try {
            return repository.findAllForPlayer(uuid).stream()
                    .max(java.util.Comparator.comparing(OriginalKitSnapshot::savedAt));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed listing original kits for " + uuid, e);
            return Optional.empty();
        }
    }

    public ItemStack[] loadLayout(UUID uuid, int slot) {
        try {
            Optional<OriginalKitSnapshot> snapshot = repository.find(uuid, slot);
            if (snapshot.isPresent()) {
                return ItemSerializer.fromBase64(snapshot.get().itemDataBase64());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed loading original kit layout " + uuid + "/" + slot, e);
        }
        return new ItemStack[41];
    }

    /** The settings for a slot (vanilla defaults when never configured or the row is missing). */
    public OriginalKitSettings settingsOf(UUID uuid, int slot) {
        return find(uuid, slot).map(OriginalKitSnapshot::settings)
                .orElseGet(OriginalKitSettings::defaults);
    }

    /** Persists only the settings for a slot, leaving the kit loadout untouched. */
    public void saveSettings(Player player, int slot, OriginalKitSettings settings) {
        UUID uuid = player.getUniqueId();
        Optional<OriginalKitSnapshot> current = find(uuid, slot);
        if (current.isEmpty()) {
            // No loadout yet: store an empty layout row so the slot shows as "saved" and the
            // settings stick even though there is nothing to equip yet.
            OriginalKitSnapshot fresh = new OriginalKitSnapshot(uuid, slot,
                    ItemSerializer.toBase64(new ItemStack[41]), null,
                    settings == null ? null : settings.serialize(), Instant.now());
            persist(fresh);
            player.sendMessage(Component.text("オリジナルキットの設定を保存しました。", NamedTextColor.GREEN));
            return;
        }
        OriginalKitSnapshot updated = current.get().withSettings(settings);
        persist(updated);
        player.sendMessage(Component.text("オリジナルキットの設定を保存しました。", NamedTextColor.GREEN));
    }

    private void persist(OriginalKitSnapshot snapshot) {
        asyncExecutor.execute(() -> {
            try {
                repository.upsert(snapshot);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed saving original kit", e);
            }
        });
    }

    private void persistLayout(Player player, int slot, ItemStack[] layout) {
        String items = ItemSerializer.toBase64(layout);
        // Keep any settings already configured on this slot across a loadout re-save.
        UUID uuid = player.getUniqueId();
        Optional<OriginalKitSnapshot> current = find(uuid, slot);
        String settings = current.map(OriginalKitSnapshot::settingsJson).orElse(null);
        OriginalKitSnapshot snapshot = new OriginalKitSnapshot(uuid, slot, items, null, settings, Instant.now());
        persist(snapshot);
    }

    public void saveLayout(Player player, int slot, ItemStack[] layout) {
        persistLayout(player, slot, layout);
        player.sendMessage(Component.text("オリジナルキットを保存しました。", NamedTextColor.GREEN));
    }

    // ---- inventory stash / restore (OrPlusGUI lifecycle) ----

    public void stashInventory(Player player) {
        pendingInventory.put(player.getUniqueId(), player.getInventory().getContents());
        player.getInventory().clear();
    }

    public void restoreInventory(Player player) {
        ItemStack[] saved = pendingInventory.remove(player.getUniqueId());
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
    }

    public void restoreOnQuit(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            restoreInventory(player);
        }
        pendingInventory.remove(uuid);
        editContexts.remove(uuid);
    }

    // ---- editor context ----

    public void setRoomService(OriginalKitRoomService roomService) {
        this.roomService = roomService;
    }

    public boolean isEditing(UUID uuid) {
        return editContexts.containsKey(uuid);
    }

    /** Opens an edit session for a slot and sends the player into the room (creative after TP). */
    public void enterRoomEditor(Player player, int slot, ItemStack[] layout) {
        if (roomService == null || !roomService.isConfigured()) {
            // Never stash the lobby inventory without a room to edit in — the player would
            // be stranded in the edit state. Report the missing room instead.
            if (roomService != null) {
                roomService.enter(player);
            }
            return;
        }
        editContexts.computeIfAbsent(player.getUniqueId(), id -> new EditContext(slot, layout));
        stashInventory(player);
        // Restore the existing kit contents for editing.
        if (layout != null) {
            ItemStack[] copy = new ItemStack[layout.length];
            for (int i = 0; i < layout.length; i++) {
                copy[i] = layout[i] == null ? null : layout[i].clone();
            }
            player.getInventory().setContents(pad(copy));
        }
        if (roomService != null) {
            roomService.enter(player);
        }
    }

    private ItemStack[] pad(ItemStack[] contents) {
        ItemStack[] full = new ItemStack[41];
        System.arraycopy(contents, 0, full, 0, Math.min(contents.length, full.length));
        return full;
    }

    public void beginEdit(Player player, int slot, ItemStack[] layout) {
        editContexts.computeIfAbsent(player.getUniqueId(), id -> new EditContext(slot, layout));
    }

    public EditContext context(UUID uuid) {
        return editContexts.get(uuid);
    }

    public void endEdit(Player player) {
        editContexts.remove(player.getUniqueId());
        restoreInventory(player);
    }

    /**
     * Emergency teardown when a match starts while the player is still editing an original kit:
     * force-save the in-progress kit (losing it would punish the player for a match they did
     * not choose), then clear the edit session and leave the room so room isolation
     * (no-collision, hidden) never leaks into the fight — a leftover {@code collidable=false}
     * makes melee attacks pass straight through.
     */
    public void forceSaveAndExitForMatch(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        boolean inRoom = roomService != null && roomService.isEditing(id);
        EditContext ctx = editContexts.remove(id);
        navigating.remove(id);
        // The stashed pre-edit inventory (lobby contents) is superseded: the match applies a
        // kit now and hands lobby items back afterwards.
        pendingInventory.remove(id);
        if (!inRoom && ctx == null) {
            return;
        }
        // Room editors build the kit in their live inventory; GUI editors in the layout array.
        ItemStack[] layout = inRoom ? player.getInventory().getContents() : ctx.layout;
        int slot = ctx != null ? ctx.slot : 22;
        if (layout != null && layout.length > 0) {
            OriginalKitSaveValidator.Result result =
                    OriginalKitSaveValidator.validate(java.util.Arrays.asList(layout));
            if (result.severity() == OriginalKitSaveValidator.Severity.OK) {
                forceSaveLayout(player, slot, layout);
            } else {
                // Never kick here — the match is about to start. Just drop the invalid draft.
                player.sendMessage(Component.text(
                        "試合開始のためキット編集を中断しました。編集内容に不正なアイテムがあったため保存されませんでした: "
                                + result.reason(), NamedTextColor.RED));
            }
        }
        if (inRoom) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            roomService.exit(player);
        }
    }

    /** Saves a kit layout, bypassing any guards, when a match forcibly ends the edit session. */
    public void forceSaveLayout(Player player, int slot, ItemStack[] layout) {
        persistLayout(player, slot, layout);
        player.sendMessage(Component.text("試合が始まるため、編集中のオリジナルキットを強制保存しました。",
                NamedTextColor.GREEN));
    }

    /** Saves the room editor's inventory, leaves the room and hands the stashed lobby items back. */
    public void finishRoomEdit(Player player, int slot) {
        saveLayout(player, slot, player.getInventory().getContents());
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        if (roomService != null) {
            roomService.exit(player);
        }
        endEdit(player);
    }

    public boolean isStashed(UUID uuid) {
        return pendingInventory.containsKey(uuid);
    }

    /** Mark an intentional navigation between flow GUIs (suppresses ESC-restore). */
    public void markNavigating(UUID uuid) {
        navigating.add(uuid);
    }

    public boolean consumeNavigating(UUID uuid) {
        return navigating.remove(uuid);
    }

    /** Abort the whole flow: clear context and hand the stashed inventory back. */
    public void abortFlow(UUID uuid) {
        editContexts.remove(uuid);
        navigating.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            restoreInventory(player);
        }
    }

    /** Called from InventoryCloseEvent: restore unless we are navigating to a sub-GUI. */
    public void onEditGuiClosed(UUID uuid) {
        EditContext context = editContexts.get(uuid);
        if (context == null) {
            return;
        }
        if (context.suppressRestore) {
            context.suppressRestore = false;
            return;
        }
        endEdit(Bukkit.getPlayer(uuid));
    }

    // ---- rules synthesis ----

    /** Name under which a synthesized rules kit for {@code owner}+{@code slot} is addressed. */
    public static String syntheticKitName(UUID owner, int slot) {
        return "@original:" + owner.toString() + ":" + slot;
    }

    /**
     * Builds a rules-only {@link KitDefinition} from the slot's {@link OriginalKitSettings}.
     * The loadout comes from the saved original layout, not from {@link #syntheticKitName}
     * items — the synthesized kit simply carries the rule switches so the match runtime's
     * existing {@code kitService.get(session.kitName())} lookups read the owner's settings
     * instead of a shared match kit. This is how "fight with the original kit, rules included"
     * reuses every existing enforcement path without changing the listeners.
     */
    public KitDefinition synthesizeKit(UUID owner, int slot) {
        return synthesizeKit(owner, slot, settingsOf(owner, slot));
    }

    public KitDefinition synthesizeKit(UUID owner, int slot, OriginalKitSettings settings) {
        String name = syntheticKitName(owner, slot);
        String display = "Original Kit #" + (slot + 1);
        return KitDefinition.builder(name)
                .displayName(display)
                .icon("PAPER")
                .enabled(true)
                .maxHealth(settings.maxHealth())
                .naturalHealthRegen(settings.naturalRegen())
                .autoFood(settings.autoFood())
                .swordShieldBreak(settings.swordShieldBreak())
                .blockPlace(settings.blockPlace())
                .blockBreak(settings.blockBreak())
                .breakPlayerPlacedOnly(false)
                .pearl(settings.pearl())
                .totem(settings.totem())
                .forceAdventure(settings.forceAdventure())
                .timeoutSeconds(settings.timeoutSeconds())
                .bedExplosion(settings.bedExplosion())
                .build();
    }

    // ---- helpers ----

    public static ItemStack[] layoutFromOfficial(KitDefinition kit) {
        ItemStack[] layout = new ItemStack[41];
        for (KitItemEntry entry : kit.items()) {
            ItemStack stack = resolveEntry(entry);
            if (stack == null) {
                continue;
            }
            if (entry.slot() >= 0 && entry.slot() < 36) {
                layout[entry.slot()] = stack;
            } else if (entry.slot() == 40) {
                layout[40] = stack;
            }
        }
        layout[36] = materialOrNull(kit.armor().get("helmet"));
        layout[37] = materialOrNull(kit.armor().get("chestplate"));
        layout[38] = materialOrNull(kit.armor().get("leggings"));
        layout[39] = materialOrNull(kit.armor().get("boots"));
        return layout;
    }

    /** Rebuilds a kit item with full NBT when available, material+amount otherwise. */
    private static ItemStack resolveEntry(KitItemEntry entry) {
        if (entry.hasSerializedItem()) {
            ItemStack decoded = com.rumilance.practice.util.ItemSerializer
                    .singleFromBase64(entry.itemDataBase64());
            if (decoded != null) {
                return decoded;
            }
        }
        Material material = Material.matchMaterial(entry.material());
        return material == null || material.isAir() ? null : new ItemStack(material, Math.max(1, entry.amount()));
    }

    /** @return true if the item is a helmet/chestplate/leggings/boots. */
    public static boolean isArmor(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    /** @return the layout index of the matching armor slot (36-39), or -1 for non-armor. */
    public static int armorSlotIndex(ItemStack item) {
        if (item == null) {
            return -1;
        }
        String name = item.getType().name();
        if (name.endsWith("_HELMET")) return 36;
        if (name.endsWith("_CHESTPLATE")) return 37;
        if (name.endsWith("_LEGGINGS")) return 38;
        if (name.endsWith("_BOOTS")) return 39;
        return -1;
    }

    /**
     * Places an item into the layout: armor is forced into the armor slot (overwriting),
     * other items go to the selected slot (if empty) or the first free inventory/hotbar slot.
     *
     * @return true if placed.
     */
    public static boolean addToLayout(EditContext context, ItemStack item) {
        if (item == null || context == null) {
            return false;
        }
        int armorSlot = armorSlotIndex(item);
        if (armorSlot >= 0) {
            context.layout[armorSlot] = item.clone();
            return true;
        }
        if (context.selectedSlot != null && context.selectedSlot >= 0 && context.selectedSlot < 41
                && isEmpty(context.layout[context.selectedSlot])) {
            context.layout[context.selectedSlot] = item.clone();
            context.selectedSlot = null;
            return true;
        }
        for (int i = 9; i <= 35; i++) {
            if (isEmpty(context.layout[i])) {
                context.layout[i] = item.clone();
                return true;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (isEmpty(context.layout[i])) {
                context.layout[i] = item.clone();
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private static ItemStack materialOrNull(String name) {
        if (name == null) {
            return null;
        }
        // Armor stored as "data:<base64>" carries full NBT (enchantments, trims, ...).
        if (name.startsWith("data:")) {
            ItemStack decoded = com.rumilance.practice.util.ItemSerializer
                    .singleFromBase64(name.substring("data:".length()));
            if (decoded != null) {
                return decoded;
            }
            return null;
        }
        Material material = Material.matchMaterial(name);
        return material == null ? null : new ItemStack(material);
    }
}
