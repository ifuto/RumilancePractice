package com.rumilance.practice.originalkit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.OriginalKitRepository;
import com.rumilance.practice.model.OriginalKitSnapshot;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Personal original kit storage with monthly edit counters and plan-gated slot unlocks.
 */
public final class OriginalKitService {

    public enum Plan { DEFAULT, MEMBER, VIP, VIP_PLUS }

    private final OriginalKitRepository repository;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;
    private final ConfigService configService;
    private final Map<UUID, Integer> monthlyEdits = new ConcurrentHashMap<>();
    private final Map<UUID, YearMonth> monthKey = new ConcurrentHashMap<>();
    private final Map<String, UUID> shareCodes = new ConcurrentHashMap<>();

    public OriginalKitService(
            OriginalKitRepository repository,
            AsyncExecutor asyncExecutor,
            Logger logger,
            ConfigService configService
    ) {
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
        this.configService = configService;
    }

    public Plan planOf(Player player) {
        if (player.hasPermission("rumilance.user.vip_plus")) {
            return Plan.VIP_PLUS;
        }
        if (player.hasPermission("rumilance.user.vip")) {
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
            case VIP -> {
                int row = slot / 9;
                int col = slot % 9;
                yield (row == 0 && col == 4)
                        || (row == 1 && col >= 3 && col <= 5)
                        || (row == 2 && col >= 2 && col <= 6)
                        || (row == 3 && col >= 3 && col <= 5)
                        || (row == 4 && col == 4);
            }
            case VIP_PLUS -> slot != 44;
        };
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
        String path = "original-kit.monthly-edits." + plan.name().toLowerCase(Locale.ROOT);
        return configService.plans().getInt(path, switch (plan) {
            case DEFAULT -> 10;
            case MEMBER -> 30;
            case VIP -> 100;
            case VIP_PLUS -> -1;
        });
    }

    public void saveFromInventory(Player player) {
        Plan plan = planOf(player);
        int limit = monthlyEditLimit(plan);
        int used = monthlyEdits(player.getUniqueId());
        if (limit >= 0 && used >= limit) {
            player.sendMessage(Component.text("Monthly original-kit edit limit reached (" + limit + ").",
                    NamedTextColor.RED));
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        String items = ItemSerializer.toBase64(contents);
        String armor = ItemSerializer.toBase64(player.getInventory().getArmorContents());
        OriginalKitSnapshot snap = new OriginalKitSnapshot(player.getUniqueId(), items, armor, Instant.now());
        monthlyEdits.merge(player.getUniqueId(), 1, Integer::sum);
        monthKey.put(player.getUniqueId(), YearMonth.now());
        asyncExecutor.execute(() -> {
            try {
                repository.upsert(snap);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed saving original kit", e);
            }
        });
        player.sendMessage(Component.text("Original kit saved.", NamedTextColor.GREEN));
    }

    public String createShareCode(Player player) {
        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        shareCodes.put(code, player.getUniqueId());
        return code;
    }

    public void loadFromShareCode(Player player, String code) {
        UUID owner = shareCodes.get(code.toUpperCase(Locale.ROOT));
        if (owner == null) {
            player.sendMessage(Component.text("Unknown share code.", NamedTextColor.RED));
            return;
        }
        asyncExecutor.execute(() -> {
            try {
                var opt = repository.find(owner);
                player.getServer().getScheduler().runTask(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("RumilancePractice"),
                        () -> {
                            if (opt.isEmpty()) {
                                player.sendMessage(Component.text("Owner has no saved kit.", NamedTextColor.RED));
                                return;
                            }
                            OriginalKitSnapshot snap = opt.get();
                            player.getInventory().setContents(ItemSerializer.fromBase64(snap.itemDataBase64()));
                            if (snap.armorDataBase64() != null && !snap.armorDataBase64().isBlank()) {
                                player.getInventory().setArmorContents(ItemSerializer.fromBase64(snap.armorDataBase64()));
                            }
                            player.sendMessage(Component.text("Loaded shared original kit.", NamedTextColor.GREEN));
                        });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed loading shared original kit", e);
            }
        });
    }

    public void loadToInventory(Player player) {
        asyncExecutor.execute(() -> {
            try {
                var opt = repository.find(player.getUniqueId());
                player.getServer().getScheduler().runTask(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("RumilancePractice"),
                        () -> {
                            if (opt.isEmpty()) {
                                player.sendMessage(Component.text("No original kit saved.", NamedTextColor.RED));
                                return;
                            }
                            OriginalKitSnapshot snap = opt.get();
                            player.getInventory().setContents(ItemSerializer.fromBase64(snap.itemDataBase64()));
                            if (snap.armorDataBase64() != null && !snap.armorDataBase64().isBlank()) {
                                player.getInventory().setArmorContents(ItemSerializer.fromBase64(snap.armorDataBase64()));
                            }
                            player.sendMessage(Component.text("Original kit loaded.", NamedTextColor.GREEN));
                        });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed loading original kit", e);
            }
        });
    }
}
