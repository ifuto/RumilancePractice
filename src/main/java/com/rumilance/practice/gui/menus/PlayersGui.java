package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayersGui extends AbstractGui {

    private final PlayerStateManager stateManager;
    private final StatsService statsService;
    private final DuelRequestGui duelRequestGui;

    public PlayersGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            PlayerStateManager stateManager,
            StatsService statsService,
            DuelRequestGui duelRequestGui
    ) {
        super(registry, sounds, GuiType.PLAYERS, 6, true);
        this.stateManager = stateManager;
        this.statsService = statsService;
        this.duelRequestGui = duelRequestGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Players", NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
        int page = session.page();
        int perPage = 28;
        int start = page * perPage;
        int end = Math.min(start + perPage, online.size());
        int index = 0;
        for (int i = start; i < end; i++) {
            Player target = online.get(i);
            int row = 1 + index / 7;
            int col = 1 + index % 7;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            PlayerState state = stateManager.getState(target.getUniqueId());
            lore.add(Component.text("State: " + state.name(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Ping: " + target.getPing(), NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            try {
                var kits = statsService.allKits(target.getUniqueId());
                int wins = kits.stream().mapToInt(s -> s.wins()).sum();
                int matches = kits.stream().mapToInt(s -> s.gamesPlayed()).sum();
                lore.add(Component.text("WR: " + (matches < 21 ? ("計測中 " + matches + "/21")
                                : String.format("%.1f%%", matches == 0 ? 0 : 100.0 * wins / matches)),
                        NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            } catch (Exception ignored) {
                lore.add(Component.text("Stats: -", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                    "player:" + target.getUniqueId());
            meta.getPersistentDataContainer().set(ItemKeys.targetUuid(), PersistentDataType.STRING,
                    target.getUniqueId().toString());
            head.setItemMeta(meta);
            inventory.setItem(GuiSlots.slot(row, col), head);
            index++;
        }
        if (page > 0) {
            inventory.setItem(GuiSlots.slot(5, 0), GuiDecorator.button(Material.ARROW,
                    Component.text("Previous", NamedTextColor.YELLOW), "prev"));
        }
        if (end < online.size()) {
            inventory.setItem(GuiSlots.slot(5, 8), GuiDecorator.button(Material.ARROW,
                    Component.text("Next", NamedTextColor.YELLOW), "next"));
        }
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("prev".equals(action)) {
            session.setPage(Math.max(0, session.page() - 1));
            render(player, session, inventory);
            return;
        }
        if ("next".equals(action)) {
            session.setPage(session.page() + 1);
            render(player, session, inventory);
            return;
        }
        if (action.startsWith("player:")) {
            UUID target = UUID.fromString(action.substring(7));
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                sounds.play(player, "error");
                return;
            }
            player.closeInventory();
            duelRequestGui.openFor(player, targetPlayer, true);
        }
    }
}
