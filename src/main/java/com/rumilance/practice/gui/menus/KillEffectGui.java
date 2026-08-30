package com.rumilance.practice.gui.menus;

import com.rumilance.practice.cosmetic.kill.KillEffect;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Picker for the paid kill-effect cosmetics. The free "None" option is always available; every
 * actual effect is VIP+ only and is shown locked for non-donors. Paging follows the standard
 * 28-slot content grid.
 */
public final class KillEffectGui extends AbstractGui {

    private static final int PAGE_SIZE = 28;

    private final SettingsService settingsService;
    private final RankService rankService;

    public KillEffectGui(GuiSessionRegistry registry, SoundService sounds,
                         SettingsService settingsService, RankService rankService) {
        super(registry, sounds, GuiType.KILL_EFFECT, 6, true);
        this.settingsService = settingsService;
        this.rankService = rankService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.kill-effect-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<KillEffect> effects = KillEffect.all();
        int pages = Math.max(1, (effects.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (session.page() >= pages) {
            session.setPage(pages - 1);
        }
        int page = session.page();
        int offset = page * PAGE_SIZE;
        boolean premium = rankService != null && rankService.isVipPlusOrAbove(player);
        String selected = settingsService.get(player).killEffect();

        int placed = 0;
        for (int i = offset; i < effects.size() && placed < PAGE_SIZE; i++, placed++) {
            KillEffect effect = effects.get(i);
            inventory.setItem(GuiSlots.slot(1 + placed / 7, 1 + placed % 7),
                    icon(player, effect, selected, premium));
        }

        paintPaging(player, inventory, page, effects.size());
        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private ItemStack icon(Player player, KillEffect effect, String selectedId, boolean premium) {
        boolean active = effect.id().equalsIgnoreCase(selectedId);
        boolean isNone = effect.isNone();
        boolean locked = !isNone && !premium;
        ItemBuilder builder = ItemBuilder.of(effect.icon() == null ? Material.NETHER_STAR : effect.icon())
                .nameMini(effect.displayName())
                .glint(active);
        if (isNone) {
            builder.lore(
                    UiTheme.divider(),
                    active ? UiTheme.status(line(player, "gui.kill-effect-active"), UiTheme.SUCCESS)
                            : UiTheme.hint(line(player, "gui.kit-click-select"))
            ).action("fx:" + effect.id());
            return builder.build();
        }
        if (locked) {
            builder.lore(
                    UiTheme.divider(),
                    UiTheme.status(line(player, "gui.kill-effect-locked"), UiTheme.DANGER),
                    UiTheme.line(line(player, "gui.kill-effect-vip"))
            ).action("locked");
        } else {
            builder.lore(
                    UiTheme.divider(),
                    active ? UiTheme.status(line(player, "gui.kill-effect-active"), UiTheme.SUCCESS)
                            : UiTheme.hint(line(player, "gui.kit-click-select")),
                    UiTheme.hint(line(player, "gui.kill-effect-preview"))
            ).action("fx:" + effect.id());
        }
        return builder.build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(Math.max(0, session.page() - 1));
            refresh(player, session, inventory);
            return;
        }
        if ("page:next".equals(action)) {
            session.setPage(session.page() + 1);
            refresh(player, session, inventory);
            return;
        }
        if ("locked".equals(action)) {
            sounds.play(player, "gui-back");
            player.sendMessage(t(player, "gui.kill-effect-denied"));
            return;
        }
        if (action.startsWith("fx:")) {
            String id = action.substring(3);
            KillEffect effect = KillEffect.byId(id);
            if (!effect.isNone() && (rankService == null || !rankService.isVipPlusOrAbove(player))) {
                sounds.play(player, "gui-back");
                player.sendMessage(t(player, "gui.kill-effect-denied"));
                return;
            }
            settingsService.update(settingsService.get(player).withKillEffect(id));
            sounds.play(player, "select");
            player.sendMessage(t(player, "gui.kill-effect-set",
                    com.rumilance.practice.locale.MessageService.tags("id", id)));
            refresh(player, session, inventory);
        }
    }
}
