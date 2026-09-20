package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.DelayedButton;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 「メッセージの受信」— the chat reception screen reached from the settings menu.
 *
 * <p>Five independent switches, one row: global chat, TELL/WHISPER from friends,
 * TELL/WHISPER from everyone else, friend join/quit lines, and other players' join/quit
 * lines. Every one of them is a 木時差式ボタン, so flipping a switch clicks down, pops back
 * 0.2s later and only then repaints with the new state. The decision itself lives in
 * {@link com.rumilance.practice.settings.ChatPolicy}, which the chat, whisper and
 * join/quit paths all consult.</p>
 */
public final class ChatSettingsGui extends AbstractGui {

    private final SettingsService settingsService;
    private SettingsGui settingsGui;

    public ChatSettingsGui(GuiSessionRegistry registry, SoundService sounds, SettingsService settingsService) {
        super(registry, sounds, GuiType.CHAT_SETTINGS, 3, true);
        this.settingsService = settingsService;
    }

    public void setSettingsGui(SettingsGui settingsGui) {
        this.settingsGui = settingsGui;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.CYAN;
    }

    @Override
    protected Material titleIcon() {
        return Material.BOOK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.chat-settings-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        PlayerSettings s = settingsService.get(player);
        paintFrame(player, session, inventory);
        int col = 2;
        inventory.setItem(GuiSlots.slot(1, col++), toggle(player, Material.BOOK, "gui.recv-global-chat",
                s.receiveGlobalChat(), "global_chat", "gui.recv-global-chat-lore"));
        inventory.setItem(GuiSlots.slot(1, col++), toggle(player, Material.WRITTEN_BOOK, "gui.recv-friend-messages",
                s.receiveFriendMessages(), "friend_messages", "gui.recv-friend-messages-lore"));
        inventory.setItem(GuiSlots.slot(1, col++), toggle(player, Material.WRITABLE_BOOK, "gui.recv-stranger-messages",
                s.receiveStrangerMessages(), "stranger_messages", "gui.recv-stranger-messages-lore"));
        inventory.setItem(GuiSlots.slot(1, col++), toggle(player, Material.OAK_DOOR, "gui.recv-friend-joinquit",
                s.receiveFriendJoinQuit(), "friend_join_quit", "gui.recv-friend-joinquit-lore"));
        inventory.setItem(GuiSlots.slot(1, col), toggle(player, Material.IRON_DOOR, "gui.recv-stranger-joinquit",
                s.receiveStrangerJoinQuit(), "stranger_join_quit", "gui.recv-stranger-joinquit-lore"));
        paintNav(player, session, inventory);
    }

    private ItemStack toggle(Player player, Material material, String nameKey, boolean enabled,
                             String key, String descriptionKey) {
        return ItemBuilder.of(material)
                .name(t(player, nameKey).color(enabled ? UiTheme.SUCCESS : UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, descriptionKey)),
                        UiTheme.blank(),
                        UiTheme.status(line(player, enabled ? "gui.toggle-on" : "gui.toggle-off"),
                                enabled ? UiTheme.SUCCESS : UiTheme.DANGER),
                        UiTheme.hint(line(player, "gui.toggle-hint"))
                )
                .glint(enabled)
                .action(DelayedButton.wrap("toggle:" + key))
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("back".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if (settingsGui != null) {
                settingsGui.open(player);
            }
            return;
        }
        if (action == null || !action.startsWith("toggle:")) {
            return;
        }
        PlayerSettings s = settingsService.get(player);
        PlayerSettings next = switch (action) {
            case "toggle:global_chat" -> s.withReceiveGlobalChat(!s.receiveGlobalChat());
            case "toggle:friend_messages" -> s.withReceiveFriendMessages(!s.receiveFriendMessages());
            case "toggle:stranger_messages" -> s.withReceiveStrangerMessages(!s.receiveStrangerMessages());
            case "toggle:friend_join_quit" -> s.withReceiveFriendJoinQuit(!s.receiveFriendJoinQuit());
            case "toggle:stranger_join_quit" -> s.withReceiveStrangerJoinQuit(!s.receiveStrangerJoinQuit());
            default -> s;
        };
        if (next != s) {
            settingsService.update(next);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
        }
    }
}
