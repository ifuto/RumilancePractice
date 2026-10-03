package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Multi-Queue セレクタ: 複数のキットキューに同時に参加できる GUI。
 *
 * <ul>
 *   <li>キット一覧 — 参加中のキットはグロー表示</li>
 *   <li>クリック → そのキットのキューに参加/退出トグル (GUI は閉じない)</li>
 *   <li>「全部のQueueに参加する」ボタン</li>
 *   <li>「全部のQueueから退出する」ボタン</li>
 * </ul>
 */
public final class MultiQueueGui extends AbstractGui {

    private final KitService kitService;
    private final QueueService queueService;
    private final QueueCoordinator queueCoordinator;
    private final MessageService messageService;
    private final MatchMode mode;

    public MultiQueueGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            QueueService queueService,
            QueueCoordinator queueCoordinator,
            MessageService messageService,
            MatchMode mode
    ) {
        super(registry, sounds,
                mode == MatchMode.RANKED ? GuiType.RANKED_QUEUE : GuiType.UNRANKED_QUEUE,
                6, true);
        this.kitService = kitService;
        this.queueService = queueService;
        this.queueCoordinator = queueCoordinator;
        this.messageService = messageService;
        this.mode = mode;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return mode == MatchMode.RANKED
                ? com.rumilance.practice.gui.GuiFrame.Theme.YELLOW
                : com.rumilance.practice.gui.GuiFrame.Theme.CYAN;
    }

    @Override
    protected Material titleIcon() {
        return Material.CLOCK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return messageService.render(messageService.resolveLocale(player),
                mode == MatchMode.RANKED ? "gui.ranked-queue" : "gui.unranked-queue");
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        String locale = messageService.resolveLocale(player);
        PlayerPlatform platform = PlayerPlatform.of(player);
        Set<String> queuedKits = queueService.queuedKitIds(player.getUniqueId(), mode);

        List<KitDefinition> kits = kitService.enabled();

        // キットグリッド (4行 × 7列 = 28 スロット、Row 1-4)
        int slot = 0;
        for (KitDefinition kit : kits) {
            if (slot >= 28) break;
            String id = kitService.playableId(kit.name());
            if (!kitService.isQueueEnabled(id)) continue;

            boolean queued = queuedKits.contains(id) || queuedKits.contains(kit.name().toLowerCase());
            int waiting = queueService.waitingCount(mode, id, platform);
            Material mat = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);

            ItemBuilder builder = ItemBuilder.of(mat)
                    .nameMini(kit.prettyDisplayName());

            if (queued) {
                builder.lore(
                        UiTheme.divider(),
                        UiTheme.status(line(player, "gui.queue-now"), UiTheme.SUCCESS),
                        UiTheme.hint(line(player, "gui.queue-leave-click"))
                );
            } else {
                builder.lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.queue-count"), String.valueOf(waiting)),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.queue-left-join"))
                );
            }

            builder.glint(queued)
                    .action("kit:" + kit.name())
                    .tag(ItemKeys.kitName(), kit.name());

            inventory.setItem(GuiSlots.slot(0, 0) + slot, builder.build());
            slot++;
        }

        // 全参加ボタン (Row 5, Col 2)
        inventory.setItem(GuiSlots.slot(4, 2), ItemBuilder.of(Material.LIME_DYE)
                .name(Component.text("▶ 全部のQueueに参加する", NamedTextColor.GREEN))
                .lore(UiTheme.divider(),
                        UiTheme.hint(line(player, "gui.queue-join-all-hint")))
                .action("join-all")
                .build());

        // 全退出ボタン (Row 5, Col 6)
        inventory.setItem(GuiSlots.slot(4, 6), ItemBuilder.of(Material.RED_DYE)
                .name(Component.text("■ 全部のQueueから退出する", NamedTextColor.RED))
                .lore(UiTheme.divider(),
                        UiTheme.hint(line(player, "gui.queue-leave-all-hint")))
                .action("leave-all")
                .build());

        // 現在のキュー数表示 (Row 5, Col 4)
        int queuedCount = queuedKits.size();
        inventory.setItem(GuiSlots.slot(4, 4), ItemBuilder.of(Material.PAPER)
                .name(Component.text("キュー参加中: " + queuedCount + " キット", UiTheme.VALUE))
                .action("decorate")
                .build());

        // 戻るボタン (Row 5, Col 0)
        inventory.setItem(GuiSlots.slot(4, 0), ItemBuilder.of(Material.BARRIER)
                .name(t(player, "menu.close"))
                .action("close")
                .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("join-all".equals(action)) {
            sounds.play(player, "gui-click");
            queueCoordinator.joinAll(player, mode);
            refresh(player, session, inventory);
            return;
        }
        if ("leave-all".equals(action)) {
            sounds.play(player, "queue-leave");
            queueCoordinator.leaveAll(player);
            refresh(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("kit:")) {
            String kitId = action.substring(4);
            if (!kitService.isQueueEnabled(kitId)
                    || !kitService.isQueueEnabled(kitService.playableId(kitId))) {
                sounds.play(player, "error");
                return;
            }
            sounds.play(player, "gui-click");
            queueCoordinator.join(player, kitService.playableId(kitId), mode);
            // GUI を閉じずに再描画
            refresh(player, session, inventory);
        }
    }
}