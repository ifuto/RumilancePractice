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
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StatsKitGui extends AbstractGui {

    private final KitService kitService;
    private final StatsService statsService;

    public StatsKitGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService, StatsService statsService) {
        super(registry, sounds, GuiType.STATS_KIT, 6, true);
        this.kitService = kitService;
        this.statsService = statsService;
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        if (session.targetPlayer() == null) {
            session.setTargetPlayer(player.getUniqueId());
        }
    }

    public void openFor(Player viewer, UUID target) {
        GuiSession session = registry.open(viewer.getUniqueId(), type(), rows);
        session.setTargetPlayer(target);
        session.setRanked(true);
        PracticeGuiOpen.open(this, viewer, session);
        sounds.play(viewer, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Ranked Stats", NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        UUID target = session.targetPlayer() == null ? player.getUniqueId() : session.targetPlayer();
        int index = 0;
        for (KitDefinition kit : kitService.enabled()) {
            if (index >= 28) {
                break;
            }
            int row = 1 + index / 7;
            int col = 1 + index % 7;
            Material mat = Material.matchMaterial(kit.icon());
            ItemStack icon = new ItemStack(mat == null ? Material.DIAMOND_SWORD : mat);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(kit.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            try {
                RankedKitStats stats = statsService.kitStats(target, kit.name())
                        .orElse(RankedKitStats.starting(target, kit.name()));
                lore.add(line("Wins", String.valueOf(stats.wins()), NamedTextColor.GREEN));
                lore.add(line("Losses", String.valueOf(stats.losses()), NamedTextColor.RED));
                lore.add(line("Matches", String.valueOf(stats.gamesPlayed()), NamedTextColor.GRAY));
                lore.add(line("WinRate", statsService.winRateLabel(stats), NamedTextColor.AQUA));
                lore.add(line("K/D", String.format("%.2f", statsService.kd(stats)), NamedTextColor.YELLOW));
                lore.add(line("Streak", String.valueOf(stats.winStreak()), NamedTextColor.LIGHT_PURPLE));
                lore.add(line("Elo", String.valueOf(stats.elo()), NamedTextColor.GOLD));
            } catch (Exception e) {
                lore.add(Component.text("Stats unavailable", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "kit:" + kit.name());
            icon.setItemMeta(meta);
            inventory.setItem(GuiSlots.slot(row, col), icon);
            index++;
        }
        inventory.setItem(GuiSlots.slot(5, 4), GuiDecorator.button(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED), "close"));
    }

    private static Component line(String key, String value, NamedTextColor color) {
        return Component.text(key + ": " + value, color).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
        } else if (action.startsWith("kit:")) {
            sounds.play(player, "kit-select");
        }
    }
}
