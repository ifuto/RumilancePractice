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
                t(player, "gui.settings-title").color(UiTheme.PRIMARY));
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        PlayerSettings s = settingsService.get(player);
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        inventory.setItem(GuiSlots.slot(2, 1), toggle(player, Material.BARRIER, "gui.deny-duels",
                !s.acceptDuelRequests(), "deny_duels",
                "gui.deny-duels-lore"));
        inventory.setItem(GuiSlots.slot(2, 3), toggle(player, Material.COMPASS, "gui.auto-requeue",
                s.autoRequeue(), "auto_requeue",
                "gui.auto-requeue-lore"));
        inventory.setItem(GuiSlots.slot(2, 5), toggle(player, Material.ENDER_EYE, "gui.allow-spectate",
                s.spectateVisible(), "spectators",
                "gui.allow-spectate-lore"));
        inventory.setItem(GuiSlots.slot(2, 7), toggle(player, Material.PAPER, "gui.hide-chat",
                s.hideOtherChat(), "hide_chat",
                "gui.hide-chat-lore"));
        inventory.setItem(GuiSlots.slot(3, 1), toggle(player, Material.NOTE_BLOCK, "gui.sounds",
                s.soundsEnabled(), "sounds",
                "gui.sounds-lore"));
        inventory.setItem(GuiSlots.slot(3, 3), toggle(player, Material.WRITABLE_BOOK, "gui.match-report",
                s.showMatchReport(), "match_report",
                "gui.match-report-lore"));
        inventory.setItem(GuiSlots.slot(3, 5), toggle(player, Material.PAINTING, "gui.scoreboard",
                s.scoreboardEnabled(), "scoreboard",
                "gui.scoreboard-lore"));
        inventory.setItem(GuiSlots.slot(3, 7), toggle(player, Material.GLOWSTONE_DUST, "gui.ally-glow",
                s.teamGlow(), "team_glow",
                "gui.ally-glow-lore"));
        inventory.setItem(GuiSlots.slot(4, 3), toggle(player, Material.LEATHER_CHESTPLATE, "gui.team-leather",
                s.teamColoredArmor(), "team_armor",
                "gui.team-leather-lore"));
        inventory.setItem(GuiSlots.slot(4, 5),
                ItemBuilder.of(Material.OAK_SIGN)
                        .name(t(player, "gui.chat-whitelist").color(UiTheme.SECONDARY))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.labelValue(line(player, "gui.chat-whitelist-count"),
                                        String.valueOf(s.chatWhitelist().size())),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.chat-whitelist-hint"))
                        )
                        .action("whitelist")
                        .build());

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
                player.sendActionBar(t(player, "gui.settings-cooldown",
                        com.rumilance.practice.locale.MessageService.tags("secs", String.valueOf(waitSecs)))
                        .color(UiTheme.WARNING));
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
