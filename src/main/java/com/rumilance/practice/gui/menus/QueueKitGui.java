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
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Ranked / Unranked kit queue selector. The 28-slot content grid (rows 1-4, cols 1-7) lists
 * every enabled kit with its live queue count and a ranked/unranked accent; the bottom bar
 * holds the close button. Disabled kits render as a barrier with an explanation.
 */
public final class QueueKitGui extends AbstractGui {

    private final KitService kitService;
    private final QueueService queueService;
    private final QueueCoordinator queueCoordinator;
    private final boolean ranked;
    private KitPreviewGui previewGui;
    private com.rumilance.practice.database.repository.WinStreakRepository winStreakRepository;

    public QueueKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            QueueService queueService,
            QueueCoordinator queueCoordinator,
            boolean ranked
    ) {
        super(registry, sounds, ranked ? GuiType.RANKED_QUEUE : GuiType.UNRANKED_QUEUE, 6, ranked);
        this.kitService = kitService;
        this.queueService = queueService;
        this.queueCoordinator = queueCoordinator;
        this.ranked = ranked;
        this.winStreakRepository = null;
    }

    public void setPreviewGui(KitPreviewGui previewGui) {
        this.previewGui = previewGui;
    }

    public void openPreview(Player player, String kitId) {
        if (previewGui == null) {
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), previewGui.type(), previewGui.rows());
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(previewGui, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setRanked(ranked);
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text(ranked ? "⚔ Ranked Queue" : "♟ Unranked Queue",
                ranked ? UiTheme.PRIMARY : UiTheme.SECONDARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<KitDefinition> kits = kitService.enabled();
        int index = 0;
        for (KitDefinition kit : kits) {
            if (index >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++), kitIcon(player, kit));
        }

        // Info tile showing total queue depth.
        PlayerPlatform platform = PlayerPlatform.of(player);
        int totalWaiting = kits.stream()
                .filter(k -> kitService.isQueueEnabled(k.name()))
                .mapToInt(k -> queueService.waitingCount(mode(), k.name(), platform))
                .sum();
        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.of(Material.CLOCK)
                        .name(Component.text("In Queue", UiTheme.MUTED))
                        .lore(UiTheme.labelValue("Players", String.valueOf(totalWaiting)))
                        .action("decorate")
                        .build());

        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(Component.text("You", UiTheme.VALUE))
                        .skullOwner(player)
                        .lore(
                                UiTheme.labelValue("Mode", ranked ? "Ranked" : "Unranked"),
                                UiTheme.hint("Pick a kit to queue")
                        )
                        .action("decorate")
                        .build());

        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.action(Material.BARRIER,
                        Component.text("Close", UiTheme.DANGER), "close"));
    }

    private ItemStack kitIcon(Player player, KitDefinition kit) {
        boolean queueOn = kitService.isQueueEnabled(kit.name());
        int waiting = queueService.waitingCount(mode(), kit.name(), PlayerPlatform.of(player));

        if (!queueOn) {
            return ItemBuilder.of(Material.BARRIER)
                    .nameMini(kit.prettyDisplayName())
                    .lore(
                            UiTheme.divider(),
                            UiTheme.status("DISABLED", UiTheme.DANGER),
                            UiTheme.line("Queue is currently closed.")
                    )
                    .action("decorate")
                    .build();
        }

        Material icon = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);
        ItemBuilder builder = ItemBuilder.of(icon)
                .nameMini(kit.prettyDisplayName())
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("Mode", ranked ? "Ranked" : "Unranked"),
                        UiTheme.labelValue("Waiting", String.valueOf(waiting))
                );
        if (ranked) {
            builder.lore(
                    UiTheme.labelValue("Arena", kit.hasFixedArena()
                            ? com.rumilance.practice.util.KitNames.pretty(kit.arenaName()) : "Random"),
                    UiTheme.labelValue("Ranked", "Yes")
            );
        }
        builder.lore(
                UiTheme.blank(),
                UiTheme.hint("Left-click to join queue"),
                UiTheme.hint("Right-click to preview kit")
        );
        return builder
                .glint(waiting > 0)
                .action("kit:" + kit.name())
                .tag(ItemKeys.kitName(), kit.name())
                .build();
    }

    private MatchMode mode() {
        return ranked ? MatchMode.RANKED : MatchMode.UNRANKED;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action.startsWith("kit:")) {
            String kitId = action.substring(4);
            if (!kitService.isQueueEnabled(kitId)) {
                sounds.play(player, "error");
                return;
            }
            // Right-click opens a read-only kit preview; left-click (and any other click) joins the queue.
            if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
                sounds.play(player, "gui-click");
                player.closeInventory();
                openPreview(player, kitId);
                return;
            }
            sounds.play(player, "kit-select");
            player.closeInventory();
            queueCoordinator.join(player, kitId, mode());
        }
    }
}
