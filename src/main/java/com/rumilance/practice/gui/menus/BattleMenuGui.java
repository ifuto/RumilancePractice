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
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Combat entry: Ranked / Unranked / Player Duel / FFA / Bot.
 */
public final class BattleMenuGui extends AbstractGui {

    private final QueueKitGui rankedGui;
    private final QueueKitGui unrankedGui;
    private final PlayersGui playersGui;
    private final FfaListGui ffaListGui;
    private final BotMenuGui botMenuGui;
    private final MessageService messageService;

    public BattleMenuGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            QueueKitGui rankedGui,
            QueueKitGui unrankedGui,
            PlayersGui playersGui,
            FfaListGui ffaListGui,
            BotMenuGui botMenuGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.BATTLE_MENU, 6, true);
        this.rankedGui = rankedGui;
        this.unrankedGui = unrankedGui;
        this.playersGui = playersGui;
        this.ffaListGui = ffaListGui;
        this.botMenuGui = botMenuGui;
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

        inventory.setItem(GuiSlots.slot(2, 1), ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(text(player, "menu.ranked").color(UiTheme.PRIMARY))
                .lore(UiTheme.divider(), UiTheme.line(raw(player, "menu.ranked-lore")),
                        UiTheme.blank(), UiTheme.hint(raw(player, "menu.click")))
                .action("ranked").build());

        inventory.setItem(GuiSlots.slot(2, 3), ItemBuilder.of(Material.IRON_SWORD)
                .name(text(player, "menu.unranked").color(UiTheme.VALUE))
                .lore(UiTheme.divider(), UiTheme.line(raw(player, "menu.unranked-lore")),
                        UiTheme.blank(), UiTheme.hint(raw(player, "menu.click")))
                .action("unranked").build());

        inventory.setItem(GuiSlots.slot(2, 5), ItemBuilder.of(Material.PLAYER_HEAD)
                .name(text(player, "menu.player-duel").color(UiTheme.SECONDARY))
                .lore(UiTheme.divider(), UiTheme.line(raw(player, "menu.player-duel-lore")),
                        UiTheme.blank(), UiTheme.hint(raw(player, "menu.click")))
                .action("player-duel").build());

        inventory.setItem(GuiSlots.slot(2, 7), ItemBuilder.of(Material.END_CRYSTAL)
                .name(text(player, "menu.ffa").color(UiTheme.WARNING))
                .lore(UiTheme.divider(), UiTheme.line(raw(player, "menu.ffa-lore")),
                        UiTheme.blank(), UiTheme.hint(raw(player, "menu.click")))
                .action("ffa").build());

        inventory.setItem(GuiSlots.slot(3, 4), ItemBuilder.of(Material.ZOMBIE_HEAD)
                .name(text(player, "menu.bot").color(UiTheme.PRIMARY))
                .lore(UiTheme.divider(), UiTheme.line(raw(player, "menu.bot-lore")),
                        UiTheme.blank(), UiTheme.hint(raw(player, "menu.click")))
                .action("bot").build());

        MenuScaffold.backButton(inventory, text(player, "menu.back").color(UiTheme.WARNING));
        MenuScaffold.closeButton(inventory, text(player, "menu.close").color(UiTheme.DANGER));
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
            case "bot" -> openChild(player, botMenuGui::open);
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
