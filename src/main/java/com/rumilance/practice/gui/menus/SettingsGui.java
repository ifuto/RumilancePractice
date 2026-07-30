package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class SettingsGui extends AbstractGui {

    private final SettingsService settingsService;

    public SettingsGui(GuiSessionRegistry registry, SoundService sounds, SettingsService settingsService) {
        super(registry, sounds, GuiType.SETTINGS, 4, true);
        this.settingsService = settingsService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Settings", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        PlayerSettings s = settingsService.get(player);
        inventory.setItem(GuiSlots.slot(1, 1), toggle(Material.BARRIER, "Deny Duel Requests", !s.acceptDuelRequests()));
        inventory.setItem(GuiSlots.slot(1, 2), toggle(Material.COMPASS, "Auto Requeue", s.autoRequeue()));
        inventory.setItem(GuiSlots.slot(1, 3), toggle(Material.ENDER_EYE, "Allow Spectators", s.spectateVisible()));
        inventory.setItem(GuiSlots.slot(1, 4), toggle(Material.PAPER, "Hide Other Chat", s.hideOtherChat()));
        inventory.setItem(GuiSlots.slot(1, 5), toggle(Material.NOTE_BLOCK, "Duel Sounds", s.soundsEnabled()));
        inventory.setItem(GuiSlots.slot(1, 6), toggle(Material.PAINTING, "Scoreboard", s.scoreboardEnabled()));
        inventory.setItem(GuiSlots.slot(2, 1), GuiDecorator.button(Material.OAK_SIGN,
                Component.text("Chat Whitelist: " + s.chatWhitelist().size(), NamedTextColor.AQUA), "whitelist"));
        inventory.setItem(GuiSlots.slot(3, 4), GuiDecorator.button(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED), "close"));
    }

    private ItemStack toggle(Material material, String name, boolean enabled) {
        ItemStack stack = GuiDecorator.button(material,
                Component.text(name, enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                "toggle:" + name);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text(enabled ? "ON" : "OFF",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        PlayerSettings s = settingsService.get(player);
        PlayerSettings next = switch (action) {
            case "toggle:Deny Duel Requests" -> s.withAcceptDuelRequests(!s.acceptDuelRequests());
            case "toggle:Auto Requeue" -> s.withAutoRequeue(!s.autoRequeue());
            case "toggle:Allow Spectators" -> s.withSpectateVisible(!s.spectateVisible());
            case "toggle:Hide Other Chat" -> s.withHideOtherChat(!s.hideOtherChat());
            case "toggle:Duel Sounds" -> s.withSoundsEnabled(!s.soundsEnabled());
            case "toggle:Scoreboard" -> s.withScoreboardEnabled(!s.scoreboardEnabled());
            case "whitelist" -> {
                player.closeInventory();
                player.sendMessage(Component.text("Type a player name in chat to add to whitelist, or 'clear' to reset.",
                        NamedTextColor.YELLOW));
                session.put("await_whitelist", Boolean.TRUE);
                yield s;
            }
            default -> s;
        };
        if (next != s) {
            settingsService.update(next);
            sounds.play(player, "gui-click");
            render(player, session, inventory);
        }
    }
}
