package com.rumilance.practice.gui.menus;

import com.rumilance.practice.bot.BotDifficulty;
import com.rumilance.practice.bot.SwordBotService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Sword Bot picker: choose difficulty then an enabled kit, then start a private bot duel.
 */
public final class BotMenuGui extends AbstractGui {

    private final SwordBotService botService;
    private final KitService kitService;

    public BotMenuGui(GuiSessionRegistry registry, SoundService sounds,
                      SwordBotService botService, KitService kitService) {
        super(registry, sounds, GuiType.BOT_MENU, 6, true);
        this.botService = botService;
        this.kitService = kitService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String diff = session.get("bot_diff", String.class);
        if (diff != null) {
            return Component.text("Sword Bot · " + diff, UiTheme.PRIMARY)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return Component.text("Sword Bot", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));
        String diff = session.get("bot_diff", String.class);
        if (diff == null) {
            placeDiff(inventory, GuiSlots.slot(2, 1), BotDifficulty.NOOB, Material.WOODEN_SWORD);
            placeDiff(inventory, GuiSlots.slot(2, 2), BotDifficulty.EASY, Material.STONE_SWORD);
            placeDiff(inventory, GuiSlots.slot(2, 4), BotDifficulty.NORMAL, Material.IRON_SWORD);
            placeDiff(inventory, GuiSlots.slot(2, 6), BotDifficulty.PRO, Material.DIAMOND_SWORD);
            placeDiff(inventory, GuiSlots.slot(2, 7), BotDifficulty.MARLOWWW, Material.NETHERITE_SWORD);
            MenuScaffold.backButton(inventory);
            MenuScaffold.closeButton(inventory);
            return;
        }
        int i = 0;
        for (KitDefinition kit : kitService.enabled()) {
            if (i >= 28) {
                break;
            }
            Material icon = Material.matchMaterial(kit.icon());
            inventory.setItem(MenuScaffold.gridSlot(i++), ItemBuilder.of(icon == null ? Material.DIAMOND_SWORD : icon)
                    .name(Component.text(kit.prettyDisplayName(), UiTheme.VALUE))
                    .lore(UiTheme.line("Difficulty: " + diff),
                            UiTheme.blank(),
                            UiTheme.hint("Click to fight"))
                    .action("kit:" + kit.name()).build());
        }
        MenuScaffold.backButton(inventory, Component.text("Difficulties", UiTheme.WARNING));
        MenuScaffold.closeButton(inventory);
    }

    private void placeDiff(Inventory inventory, int slot, BotDifficulty diff, Material material) {
        inventory.setItem(slot, ItemBuilder.of(material)
                .name(Component.text(diff.name(), UiTheme.PRIMARY))
                .lore(UiTheme.line(diffBlurb(diff)),
                        UiTheme.blank(),
                        UiTheme.hint("Click to pick a kit"))
                .action("diff:" + diff.name()).build());
    }

    private static String diffBlurb(BotDifficulty d) {
        return switch (d) {
            case NOOB -> "Slow swings. Almost no technique.";
            case EASY -> "Basic hits. Occasional sprint.";
            case NORMAL -> "W-tap + light strafing.";
            case PRO -> "Solid W-tap, crits, S-tap.";
            case MARLOWWW -> "Aggressive modern sword PvP.";
        };
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            if (session.get("bot_diff", String.class) != null) {
                session.put("bot_diff", null);
                refresh(player, session, inventory);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (action.startsWith("diff:")) {
            session.put("bot_diff", action.substring(5));
            refresh(player, session, inventory);
            sounds.play(player, "gui-click");
            return;
        }
        if (action.startsWith("kit:")) {
            String kit = action.substring(4);
            BotDifficulty diff = BotDifficulty.fromToken(session.get("bot_diff", String.class));
            player.closeInventory();
            botService.start(player, kit, diff);
        }
    }
}
