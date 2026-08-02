package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Minimalist profile GUI: a compact summary (matches / wins / losses / win-rate / K-D /
 * best streak / best kit / best Elo) plus one row-block of per-kit ranked stats.
 *
 * <p>Design policy: no bold anywhere, labels in gray, values in white — the only accents
 * come from the item icons themselves, so the screen stays quiet while still showing as
 * much ranked detail as possible.</p>
 */
public final class ProfileGui extends AbstractGui {

    private final KitService kitService;
    private final StatsService statsService;

    public ProfileGui(GuiSessionRegistry registry, SoundService sounds,
                      KitService kitService, StatsService statsService) {
        super(registry, sounds, GuiType.PROFILE, 6, true);
        this.kitService = kitService;
        this.statsService = statsService;
    }

    public void openFor(Player viewer, UUID target) {
        GuiSession session = registry.open(viewer.getUniqueId(), type(), rows);
        session.setTargetPlayer(target);
        PracticeGuiOpen.open(this, viewer, session);
        sounds.play(viewer, "gui-open");
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        if (session.targetPlayer() == null) {
            session.setTargetPlayer(player.getUniqueId());
        }
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        UUID target = session.targetPlayer() == null ? player.getUniqueId() : session.targetPlayer();
        return Component.text(StatsService.nameOf(target), NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        UUID target = session.targetPlayer() == null ? player.getUniqueId() : session.targetPlayer();

        List<RankedKitStats> kits;
        try {
            kits = statsService.allKits(target);
        } catch (Exception e) {
            kits = List.of();
        }
        int matches = kits.stream().mapToInt(RankedKitStats::gamesPlayed).sum();
        int wins = kits.stream().mapToInt(RankedKitStats::wins).sum();
        int losses = kits.stream().mapToInt(RankedKitStats::losses).sum();
        int bestStreak = kits.stream().mapToInt(RankedKitStats::winStreak).max().orElse(0);
        int bestElo = kits.stream().mapToInt(RankedKitStats::bestElo).max().orElse(1000);
        String bestKit = kits.stream().max(Comparator.comparingInt(RankedKitStats::wins))
                .map(RankedKitStats::kit).orElse("-");
        double winRate = matches == 0 ? 0 : 100.0 * wins / matches;
        double kd = (double) wins / Math.max(1, losses);

        // Header: player head.
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        Player online = Bukkit.getPlayer(target);
        skull.setOwningPlayer(online != null ? online : Bukkit.getOfflinePlayer(target));
        skull.displayName(Component.text(StatsService.nameOf(target), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        skull.lore(List.of(Component.text(
                online != null ? "Ping: " + online.getPing() + "ms" : "Offline",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        head.setItemMeta(skull);
        inventory.setItem(GuiSlots.slot(0, 4), head);

        // Summary rows.
        inventory.setItem(GuiSlots.slot(1, 1), summary(Material.BOOK, "総試合数", String.valueOf(matches)));
        inventory.setItem(GuiSlots.slot(1, 3), summary(Material.DIAMOND_SWORD, "勝利", String.valueOf(wins)));
        inventory.setItem(GuiSlots.slot(1, 5), summary(Material.SHIELD, "敗北", String.valueOf(losses)));
        inventory.setItem(GuiSlots.slot(1, 7), summary(Material.TARGET, "勝率",
                matches < 21 ? "計測中 " + matches + "/21" : String.format("%.1f%%", winRate)));
        inventory.setItem(GuiSlots.slot(2, 1), summary(Material.NETHERITE_SWORD, "K/D", String.format("%.2f", kd)));
        inventory.setItem(GuiSlots.slot(2, 3), summary(Material.EMERALD, "最高連勝", String.valueOf(bestStreak)));
        inventory.setItem(GuiSlots.slot(2, 5), summary(Material.NETHER_STAR, "得意キット", bestKit));
        inventory.setItem(GuiSlots.slot(2, 7), summary(Material.DIAMOND, "Best Elo", String.valueOf(bestElo)));

        // Per-kit breakdown (rows 3-4).
        int index = 0;
        for (KitDefinition kit : kitService.enabled()) {
            if (index >= 14) {
                break;
            }
            int row = 3 + index / 7;
            int col = 1 + index % 7;
            inventory.setItem(GuiSlots.slot(row, col), kitIcon(kit, target, kits));
            index++;
        }

        inventory.setItem(GuiSlots.slot(5, 4), GuiDecorator.button(Material.BARRIER,
                Component.text("閉じる", NamedTextColor.GRAY), "close"));
    }

    private ItemStack summary(Material material, String label, String value) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(value, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack kitIcon(KitDefinition kit, UUID target, List<RankedKitStats> kits) {
        Material material = Material.matchMaterial(kit.icon());
        ItemStack stack = new ItemStack(material == null ? Material.DIAMOND_SWORD : material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(kit.displayName())
                .decoration(TextDecoration.ITALIC, false));
        RankedKitStats stats = kits.stream()
                .filter(s -> s.kit().equalsIgnoreCase(kit.name()))
                .findFirst()
                .orElse(RankedKitStats.starting(target, kit.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Elo", String.valueOf(stats.elo())));
        lore.add(line("W/L", stats.wins() + "/" + stats.losses()));
        lore.add(line("勝率", stats.gamesPlayed() < 21
                ? "計測中 " + stats.gamesPlayed() + "/21"
                : String.format("%.1f%%", stats.winRate() * 100)));
        lore.add(line("K/D", String.format("%.2f", statsService.kd(stats))));
        lore.add(line("連勝", String.valueOf(stats.winStreak())));
        lore.add(line("Best", String.valueOf(stats.bestElo())));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
        }
    }
}
