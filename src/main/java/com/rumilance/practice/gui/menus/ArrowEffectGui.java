package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arrow.ArrowEffectService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Arrow particle trail picker. Effects are laid out in a single horizontal row; the currently
 * selected effect glints and shows an "ACTIVE" badge, and a preview hint is shown beneath. A
 * "None" option (barrier) is always first.
 */
public final class ArrowEffectGui extends AbstractGui {

    private final ArrowEffectService arrowEffectService;
    private final SettingsService settingsService;

    public ArrowEffectGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            ArrowEffectService arrowEffectService,
            SettingsService settingsService
    ) {
        super(registry, sounds, GuiType.ARROW_EFFECT, 4, true);
        this.arrowEffectService = arrowEffectService;
        this.settingsService = settingsService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.arrow-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        String selected = settingsService.get(player).arrowEffect();
        List<String> ids = arrowEffectService.effectIds().stream().sorted().toList();

        int total = ids.size();
        // Centre the effect row: for N effects start at column (4 - N/2), clamped to [1,7].
        int startCol = Math.max(1, Math.min(7, 4 - (total - 1) / 2));
        for (int i = 0; i < total && startCol + i <= 7; i++) {
            String id = ids.get(i);
            inventory.setItem(GuiSlots.slot(2, startCol + i), effectIcon(player, id, selected));
        }

        inventory.setItem(GuiSlots.slot(3, 4),
                ItemBuilder.of(Material.BOOK)
                        .name(t(player, "gui.arrow-preview").color(UiTheme.MUTED))
                        .lore(
                                UiTheme.line(line(player, "gui.arrow-preview-1")),
                                UiTheme.line(line(player, "gui.arrow-preview-2")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.arrow-current"), selected)
                        )
                        .action("decorate")
                        .build());

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private ItemStack effectIcon(Player player, String id, String selectedId) {
        ArrowEffectService.EffectDef def = arrowEffectService.get(id);
        boolean active = id.equalsIgnoreCase(selectedId);
        boolean none = "none".equalsIgnoreCase(id);
        Material material = none ? Material.BARRIER : Material.ARROW;
        String displayName = def != null ? def.displayName() : "<gray>" + id;
        return ItemBuilder.of(material)
                .nameMini(displayName)
                .lore(
                        UiTheme.divider(),
                        active ? UiTheme.status(line(player, "gui.arrow-active"), UiTheme.SUCCESS)
                                : UiTheme.hint(line(player, "gui.kit-click-select")),
                        def != null && def.particlesPerTick() > 0
                                ? UiTheme.labelValue(line(player, "gui.arrow-particles"), String.valueOf(def.particlesPerTick()))
                                : UiTheme.line(line(player, "gui.arrow-none"))
                )
                .glint(active)
                .action("fx:" + id)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action.startsWith("fx:")) {
            String id = action.substring(3);
            settingsService.update(settingsService.get(player).withArrowEffect(id));
            sounds.play(player, "select");
            player.sendMessage(t(player, "gui.arrow-set", MessageService.tags("id", id)));
            refresh(player, session, inventory);
        }
    }
}
