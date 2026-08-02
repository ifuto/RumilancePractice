package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchState;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpectateListGui extends AbstractGui {

    private final MatchRegistry matchRegistry;
    private final SpectatorService spectatorService;

    public SpectateListGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            MatchRegistry matchRegistry,
            SpectatorService spectatorService
    ) {
        super(registry, sounds, GuiType.SPECTATE_LIST, 6, true);
        this.matchRegistry = matchRegistry;
        this.spectatorService = spectatorService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Spectate Matches", NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        int index = 0;
        for (MatchSession match : matchRegistry.all()) {
            if (match.state() != MatchState.ACTIVE || index >= 28) {
                continue;
            }
            ItemStack icon = new ItemStack(Material.IRON_SWORD);
            ItemMeta meta = icon.getItemMeta();
            UUID a = match.participants().get(0);
            UUID b = match.participants().size() > 1 ? match.participants().get(1) : a;
            meta.displayName(Component.text(StatsService.nameOf(a) + " vs " + StatsService.nameOf(b), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Kit: " + match.kitName(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Mode: " + match.mode(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                    "spec:" + a);
            icon.setItemMeta(meta);
            inventory.setItem(GuiSlots.slot(1 + index / 7, 1 + index % 7), icon);
            index++;
        }
        inventory.setItem(GuiSlots.slot(5, 4), GuiDecorator.button(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED), "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("spec:")) {
            Player target = Bukkit.getPlayer(UUID.fromString(action.substring(5)));
            player.closeInventory();
            if (target != null) {
                spectatorService.trySpectate(player, target);
            }
        }
    }
}
