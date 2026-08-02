package com.rumilance.practice.originalkit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.OriginalKitRepository;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
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
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Original kit management: per-paper-slot kits, plan-gated slot unlocks, monthly edit
 * budget, and the inventory stash/restore lifecycle used by the OrPlusGUI editor.
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
    private final Map<UUID, Integer> monthlyEdits = new ConcurrentHashMap<>();
    private final Map<UUID, YearMonth> monthKey = new ConcurrentHashMap<>();
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
            this.category = "武器/防具";
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

    /** Top plan (VIP_PLUS) skips the confirm screen. */
    public boolean canEditWithoutConfirm(Plan plan) {
        return plan == Plan.VIP_PLUS;
    }

    public int monthlyEdits(UUID uuid) {
        YearMonth now = YearMonth.now();
        YearMonth stored = monthKey.get(uuid);
        if (stored == null || !stored.equals(now)) {
            monthKey.put(uuid, now);
            monthlyEdits.put(uuid, 0);
        }
        return monthlyEdits.getOrDefault(uuid, 0);
    }

    public int monthlyEditLimit(Plan plan) {
        String path = "original-kit.monthly-edits." + switch (plan) {
            case DEFAULT -> "default";
            case MEMBER -> "member";
            case VIP -> "vip";
            case VIP_PLUS -> "vip_plus";
        };
        return configService.plans().getInt(path, switch (plan) {
            case DEFAULT -> 10;
            case MEMBER -> 30;
            case VIP -> 100;
            case VIP_PLUS -> -1;
        });
    }

    /** Label for the confirm screen: "今月の残り編集可能回数 : <n>" (∞ for unlimited). */
    public String remainingEditsLabel(Player player) {
        int limit = monthlyEditLimit(planOf(player));
        if (limit < 0) {
            return "∞";
        }
        return String.valueOf(Math.max(0, limit - monthlyEdits(player.getUniqueId())));
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

    public void saveLayout(Player player, int slot, ItemStack[] layout) {
        Plan plan = planOf(player);
        int limit = monthlyEditLimit(plan);
        int used = monthlyEdits(player.getUniqueId());
        if (limit >= 0 && used >= limit) {
            player.sendMessage(Component.text("今月のオリジナルキット編集回数の上限に達しました (" + limit + "回).",
                    NamedTextColor.RED));
            return;
        }
        String items = ItemSerializer.toBase64(layout);
        OriginalKitSnapshot snapshot = new OriginalKitSnapshot(player.getUniqueId(), slot, items, null, Instant.now());
        monthlyEdits.merge(player.getUniqueId(), 1, Integer::sum);
        monthKey.put(player.getUniqueId(), YearMonth.now());
        asyncExecutor.execute(() -> {
            try {
                repository.upsert(snapshot);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed saving original kit", e);
            }
        });
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

    // ---- helpers ----

    public static ItemStack[] layoutFromOfficial(KitDefinition kit) {
        ItemStack[] layout = new ItemStack[41];
        for (KitItemEntry entry : kit.items()) {
            Material material = Material.matchMaterial(entry.material());
            if (material != null && entry.slot() >= 0 && entry.slot() < 36) {
                layout[entry.slot()] = new ItemStack(material, Math.max(1, entry.amount()));
            }
        }
        layout[36] = materialOrNull(kit.armor().get("helmet"));
        layout[37] = materialOrNull(kit.armor().get("chestplate"));
        layout[38] = materialOrNull(kit.armor().get("leggings"));
        layout[39] = materialOrNull(kit.armor().get("boots"));
        return layout;
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
        Material material = Material.matchMaterial(name);
        return material == null ? null : new ItemStack(material);
    }
}
