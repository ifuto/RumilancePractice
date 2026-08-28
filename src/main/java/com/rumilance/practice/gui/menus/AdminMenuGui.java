package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * OP setup hub. Prefer this UI over typing admin subcommands for day-to-day kit/preset work.
 */
public final class AdminMenuGui extends AbstractGui {

    private Consumer<Player> openKitAdmin = p -> { };
    private Consumer<Player> openPresetAdmin = p -> { };
    private Consumer<Player> openEkitAdmin = p -> { };

    public AdminMenuGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.ADMIN_MENU, 6, false);
    }

    public void setOpenKitAdmin(Consumer<Player> openKitAdmin) {
        this.openKitAdmin = openKitAdmin == null ? p -> { } : openKitAdmin;
    }

    public void setOpenPresetAdmin(Consumer<Player> openPresetAdmin) {
        this.openPresetAdmin = openPresetAdmin == null ? p -> { } : openPresetAdmin;
    }

    public void setOpenEkitAdmin(Consumer<Player> openEkitAdmin) {
        this.openEkitAdmin = openEkitAdmin == null ? p -> { } : openEkitAdmin;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("N Arena 管理メニュー", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);

        inventory.setItem(GuiSlots.slot(0, 4), ItemBuilder.of(Material.NETHER_STAR)
                .name(Component.text("管理ハブ", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line("基本操作はここから UI で完結"),
                        UiTheme.line("コマンドは緊急・高度設定用")
                )
                .glint(true)
                .action("decorate")
                .build());

        inventory.setItem(GuiSlots.slot(2, 2), ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(Component.text("キット管理", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line("有効化 / ランク / アリーナ"),
                        UiTheme.line("プリセット編集 ON・OFF"),
                        UiTheme.blank(),
                        UiTheme.hint("クリックで開く")
                )
                .action("kits")
                .build());

        inventory.setItem(GuiSlots.slot(2, 4), ItemBuilder.of(Material.CHEST)
                .name(Component.text("プリセット候補", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line("カテゴリ別の候補アイテム"),
                        UiTheme.line("自由配置・複数ページ・NBT保持"),
                        UiTheme.blank(),
                        UiTheme.hint("クリックで開く")
                )
                .action("presets")
                .build());

        inventory.setItem(GuiSlots.slot(2, 6), ItemBuilder.of(Material.BOOK)
                .name(Component.text("オリジナルキット候補", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line("ekit 用のアイテムプール編集"),
                        UiTheme.blank(),
                        UiTheme.hint("クリックで開く")
                )
                .action("ekitadmin")
                .build());

        inventory.setItem(GuiSlots.slot(4, 4), ItemBuilder.of(Material.BLAZE_ROD)
                .name(Component.text("範囲選択について", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line("ホットバーのブレイズロッドで"),
                        UiTheme.line("左クリック = pos1 / 右 = pos2"),
                        UiTheme.line("その後 /arena や /ffa で確定")
                )
                .action("noop")
                .build());

        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.of(Material.BARRIER)
                .name(Component.text("閉じる", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                .action("close")
                .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || "noop".equals(action) || "decorate".equals(action)) {
            return;
        }
        switch (action) {
            case "close" -> player.closeInventory();
            case "kits" -> {
                sounds.play(player, "gui-click");
                openKitAdmin.accept(player);
            }
            case "presets" -> {
                sounds.play(player, "gui-click");
                openPresetAdmin.accept(player);
            }
            case "ekitadmin" -> {
                sounds.play(player, "gui-click");
                openEkitAdmin.accept(player);
            }
            default -> { }
        }
    }
}
