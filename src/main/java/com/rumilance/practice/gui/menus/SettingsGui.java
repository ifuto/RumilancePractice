package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.item.FunctionalItemListener;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Personal settings menu. Toggles are laid out in a centred 3x3 panel with consistent
 * ON/OFF materials and a two-line lore (current state + click hint), while the chat
 * whitelist manager and close button live on the bottom bar.
 */
public final class SettingsGui extends AbstractGui {

    private final SettingsService settingsService;
    /** Per-player timestamp of the last accepted toggle (anti spam-click). */
    private final java.util.Map<java.util.UUID, Long> lastToggle = new java.util.concurrent.ConcurrentHashMap<>();
    /** Minimum millis between toggle clicks; configured via gui.toggle-cooldown-seconds. */
    private volatile long toggleCooldownMillis = 2000L;
    private com.rumilance.practice.match.TeamColoredArmorService teamColoredArmorService;

    public SettingsGui(GuiSessionRegistry registry, SoundService sounds, SettingsService settingsService) {
        super(registry, sounds, GuiType.SETTINGS, 6, true);
        this.settingsService = settingsService;
    }

    public void setToggleCooldownSeconds(int seconds) {
        this.toggleCooldownMillis = Math.max(0, seconds) * 1000L;
    }

    public void setTeamColoredArmorService(
            com.rumilance.practice.match.TeamColoredArmorService teamColoredArmorService) {
        this.teamColoredArmorService = teamColoredArmorService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return FunctionalItemListener.stripVariationSelectors(
                Component.text("Settings", UiTheme.PRIMARY)).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        PlayerSettings s = settingsService.get(player);
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        // Central 3x3 panel (rows 2-3): 6 toggles arranged in two rows.
        inventory.setItem(GuiSlots.slot(2, 2), toggle(Material.BARRIER, "Deny Duel Requests",
                !s.acceptDuelRequests(), "deny_duels",
                "Block incoming duel requests from other players."));
        inventory.setItem(GuiSlots.slot(2, 3), toggle(Material.COMPASS, "Auto Requeue",
                s.autoRequeue(), "auto_requeue",
                "Automatically rejoin the queue after a match ends."));
        inventory.setItem(GuiSlots.slot(2, 4), toggle(Material.ENDER_EYE, "Allow Spectators",
                s.spectateVisible(), "spectators",
                "Let other players spectate your matches."));
        inventory.setItem(GuiSlots.slot(2, 5), toggle(Material.PAPER, "Hide Other Chat",
                s.hideOtherChat(), "hide_chat",
                "Hide public chat messages during matches."));
        inventory.setItem(GuiSlots.slot(3, 3), toggle(Material.NOTE_BLOCK, "Duel Sounds",
                s.soundsEnabled(), "sounds",
                "Play menu and match sound effects."));
        inventory.setItem(GuiSlots.slot(3, 4), toggle(Material.WRITABLE_BOOK, "Match Report Book",
                s.showMatchReport(), "match_report",
                "Give a clickable report book after every match."));
        inventory.setItem(GuiSlots.slot(3, 5), toggle(Material.PAINTING, "Scoreboard",
                s.scoreboardEnabled(), "scoreboard",
                "Show the sidebar scoreboard."));
        inventory.setItem(GuiSlots.slot(4, 3), toggle(Material.GLOWSTONE_DUST, "Team Glow (LOS)",
                s.teamGlow(), "team_glow",
                "Outline teammates only with line-of-sight (not through walls)."));
        inventory.setItem(GuiSlots.slot(4, 5), toggle(Material.LEATHER_CHESTPLATE, "Team Leather Look",
                s.teamColoredArmor(), "team_armor",
                "Show packet-only leather armor in team colors (default ON)."));

        // Whitelist manager (bottom-centre of last row).
        inventory.setItem(GuiSlots.slot(MenuScaffold.lastRow(inventory), 4),
                ItemBuilder.of(Material.OAK_SIGN)
                        .name(Component.text("Chat Whitelist", UiTheme.SECONDARY))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.labelValue("Players", String.valueOf(s.chatWhitelist().size())),
                                UiTheme.blank(),
                                UiTheme.hint("Click to manage")
                        )
                        .action("whitelist")
                        .build());

        MenuScaffold.closeButton(inventory);
    }

    private ItemStack toggle(Material material, String name, boolean enabled, String key, String description) {
        return ItemBuilder.of(material)
                .name(Component.text(name, enabled ? UiTheme.SUCCESS : UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(description),
                        UiTheme.blank(),
                        UiTheme.status(enabled ? "ENABLED" : "DISABLED",
                                enabled ? UiTheme.SUCCESS : UiTheme.DANGER),
                        UiTheme.hint("Click to toggle")
                )
                .glint(enabled)
                .action("toggle:" + key)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("whitelist".equals(action)) {
            player.closeInventory();
            player.sendMessage(Component.text(
                    "Type a player name in chat to add to the whitelist, or 'clear' to reset.",
                    UiTheme.WARNING));
            session.put("await_whitelist", Boolean.TRUE);
            sounds.play(player, "gui-click");
            return;
        }
        // Rate-limit toggles: rapid ON/OFF spam would hammer the settings store (DB flushes)
        // and scoreboard rebuilds for no benefit.
        if (action.startsWith("toggle:")) {
            long now = System.currentTimeMillis();
            Long last = lastToggle.get(player.getUniqueId());
            if (last != null && now - last < toggleCooldownMillis) {
                long waitSecs = Math.max(1, (toggleCooldownMillis - (now - last) + 999) / 1000);
                sounds.play(player, "error");
                player.sendActionBar(Component.text("設定変更のクールダウン中… (" + waitSecs + "s)",
                        UiTheme.WARNING));
                return;
            }
            lastToggle.put(player.getUniqueId(), now);
        }
        PlayerSettings s = settingsService.get(player);
        PlayerSettings next = switch (action) {
            case "toggle:deny_duels" -> s.withAcceptDuelRequests(!s.acceptDuelRequests());
            case "toggle:auto_requeue" -> s.withAutoRequeue(!s.autoRequeue());
            case "toggle:spectators" -> s.withSpectateVisible(!s.spectateVisible());
            case "toggle:hide_chat" -> s.withHideOtherChat(!s.hideOtherChat());
            case "toggle:sounds" -> s.withSoundsEnabled(!s.soundsEnabled());
            case "toggle:match_report" -> s.withShowMatchReport(!s.showMatchReport());
            case "toggle:scoreboard" -> s.withScoreboardEnabled(!s.scoreboardEnabled());
            case "toggle:team_glow" -> s.withTeamGlow(!s.teamGlow());
            case "toggle:team_armor" -> s.withTeamColoredArmor(!s.teamColoredArmor());
            default -> s;
        };
        if (next != s) {
            settingsService.update(next);
            sounds.play(player, "gui-click");
            if (("toggle:team_armor".equals(action) || "toggle:team_glow".equals(action))
                    && teamColoredArmorService != null) {
                teamColoredArmorService.scheduleRefreshViewer(player);
            }
            refresh(player, session, inventory);
        }
    }
}
