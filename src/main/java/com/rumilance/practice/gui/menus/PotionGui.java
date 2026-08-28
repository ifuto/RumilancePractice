package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.PotionRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Potion picker for the potion category: variant (飲用/スプラッシュ/残留) x level x
 * extended, all within vanilla survival ranges.
 */
public final class PotionGui extends AbstractGui {

    private final OriginalKitService service;
    private final OriginalKitEditGui editGui;

    public PotionGui(GuiSessionRegistry registry, SoundService sounds,
                     OriginalKitService service, OriginalKitEditGui editGui) {
        super(registry, sounds, GuiType.POTION, 6, false);
        this.service = service;
        this.editGui = editGui;
    }

    public void open(Player player, String effectKey) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("effect", effectKey);
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        if (ctx != null) {
            ctx.suppressRestore = true;
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("ポーション選択", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        String effect = session.get("effect", String.class);
        PotionRules.Option opt = PotionRules.option(effect);
        List<ItemStack> choices = new ArrayList<>();
        for (String variant : List.of("drink", "splash", "lingering")) {
            for (int level = 1; level <= opt.maxLevel(); level++) {
                List<Boolean> extendedOptions = opt.extendable() ? List.of(false, true) : List.of(false);
                for (boolean extended : extendedOptions) {
                    ItemStack potion = PotionRules.buildPotion(effect, level, extended, variant);
                    ItemMeta meta = potion.getItemMeta();
                    meta.displayName(Component.text(
                                    PotionRules.variantLabel(variant) + " " + opt.display()
                                            + (opt.maxLevel() > 1 ? " レベル" + roman(level) : "")
                                            + (extended ? " 延長" : ""),
                                    UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(List.of(Component.text("クリックでキットに追加", UiTheme.MUTED)
                            .decoration(TextDecoration.ITALIC, false)));
                    meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                            "pick:" + variant + ":" + level + ":" + extended);
                    potion.setItemMeta(meta);
                    choices.add(potion);
                }
            }
        }
        for (int i = 0; i < choices.size() && i < 45; i++) {
            inventory.setItem(i, choices.get(i));
        }
        inventory.setItem(45, ItemBuilder.action(UiTheme.BACK,
                Component.text("戻る", UiTheme.WARNING), "back"));
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "back" -> reopenEdit(player);
            default -> {
                if (action != null && action.startsWith("pick:")) {
                    String[] parts = action.substring(5).split(":");
                    String variant = parts[0];
                    int level = Integer.parseInt(parts[1]);
                    boolean extended = Boolean.parseBoolean(parts[2]);
                    String effect = session.get("effect", String.class);
                    ItemStack potion = PotionRules.buildPotion(effect, level, extended, variant);
                    OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
                    if (ctx == null) {
                        return;
                    }
                    boolean placed = OriginalKitService.addToLayout(ctx, potion);
                    if (!placed) {
                        player.sendMessage(Component.text("キットのインベントリが満杯です。", UiTheme.DANGER));
                        return;
                    }
                    sounds.play(player, "select");
                    reopenEdit(player);
                }
            }
        }
    }

    private void reopenEdit(Player player) {
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        service.markNavigating(player.getUniqueId());
        player.closeInventory();
        if (ctx != null) {
            editGui.open(player, ctx.slot, ctx.layout);
        }
    }
}
