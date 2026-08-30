package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Combat entry: Ranked / Unranked / Player Duel / FFA.
 */
public final class BattleMenuGui extends AbstractGui {

    private final QueueKitGui rankedGui;
    private final QueueKitGui unrankedGui;
    private final PlayersGui playersGui;
    private final FfaListGui ffaListGui;
    private final MessageService messageService;

    public BattleMenuGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            QueueKitGui rankedGui,
            QueueKitGui unrankedGui,
            PlayersGui playersGui,
            FfaListGui ffaListGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.BATTLE_MENU, 6, true);
        this.rankedGui = rankedGui;
        this.unrankedGui = unrankedGui;
        this.playersGui = playersGui;
        this.ffaListGui = ffaListGui;
        this.messageService = messageService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return text(player, "menu.battle-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        inventory.setItem(GuiSlots.slot(2, 2), mode(player, Material.DIAMOND_SWORD,
                "menu.ranked", UiTheme.PRIMARY, "menu.ranked-lore", "ranked", true));
        inventory.setItem(GuiSlots.slot(2, 4), mode(player, Material.IRON_SWORD,
                "menu.unranked", UiTheme.VALUE, "menu.unranked-lore", "unranked", false));
        inventory.setItem(GuiSlots.slot(2, 6), mode(player, Material.PLAYER_HEAD,
                "menu.player-duel", UiTheme.SECONDARY, "menu.player-duel-lore", "player-duel", false));

        inventory.setItem(GuiSlots.slot(3, 4), mode(player, Material.END_CRYSTAL,
                "menu.ffa", UiTheme.WARNING, "menu.ffa-lore", "ffa", false));

        paintNav(player, session, inventory);
    }

    private org.bukkit.inventory.ItemStack mode(Player player, Material material,
                                                String nameKey, TextColor color,
                                                String loreKey, String action, boolean glint) {
        return ItemBuilder.of(material)
                .name(text(player, nameKey).color(color))
                .lore(UiTheme.line(raw(player, loreKey)),
                        UiTheme.hint(raw(player, "menu.click")))
                .glint(glint)
                .action(action)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "ranked" -> openChild(player, rankedGui::open);
            case "unranked" -> openChild(player, unrankedGui::open);
            case "player-duel" -> openChild(player, playersGui::open);
            case "ffa" -> openChild(player, ffaListGui::open);
            default -> {
            }
        }
    }

    private void openChild(Player player, java.util.function.Consumer<Player> opener) {
        sounds.play(player, "gui-click");
        opener.accept(player);
        registry.get(player.getUniqueId()).ifPresent(child -> child.setFromBattleMenu(true));
    }

    private Component text(Player player, String key) {
        return messageService.render(messageService.resolveLocale(player), key);
    }

    private String raw(Player player, String key) {
        return messageService.localeService().rawMessage(messageService.resolveLocale(player), key);
    }
}
