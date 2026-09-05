package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.signqueue.SignQueueService;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Admin kit picker for queue signs: clicking a kit hands the operator a queue-sign item
 * bound to that kit (unlimited supply). Placing the sign creates an Unranked Queue sign
 * for that kit.
 */
public final class SignKitSelectGui extends AbstractGui {

    private final KitService kitService;
    private volatile SignQueueService signQueueService;

    public SignKitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.SIGN_KIT_SELECT, 6, false);
        this.kitService = kitService;
    }

    public void setSignQueueService(SignQueueService signQueueService) {
        this.signQueueService = signQueueService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.sign-kit-title").color(NamedTextColor.AQUA);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        List<KitDefinition> kits = kitService.enabled();
        int index = 0;
        for (KitDefinition kit : kits) {
            if (index >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++), kitTile(player, kit));
        }
        paintNav(player, session, inventory);
    }

    private ItemStack kitTile(Player player, KitDefinition kit) {
        return ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                .nameMini(kit.prettyDisplayName())
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.sign-kit-give-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click"))
                )
                .action("give:" + kit.name())
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action) {
        if (action == null || "decorate".equals(action)) {
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action.startsWith("give:")) {
            SignQueueService service = signQueueService;
            if (service == null) {
                return;
            }
            String kitId = action.substring("give:".length());
            if (kitService.get(kitId).isEmpty()) {
                sounds.play(player, "error");
                return;
            }
            ItemStack signItem = service.createSignItem(player, kitId);
            var leftovers = player.getInventory().addItem(signItem);
            leftovers.values().forEach(extra ->
                    player.getWorld().dropItemNaturally(player.getLocation(), extra));
            sounds.play(player, "gui-click");
        }
    }
}
