package com.rumilance.practice.gui.menus;

import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class DuelRequestGui extends AbstractGui {

    private final KitService kitService;
    private final DuelRequestService duelRequestService;
    private final SettingsService settingsService;
    private final StatsService statsService;
    private final KitSelectGui kitSelectGui;
    private final MessageService messageService;
    private DuelMapSelectGui mapSelectGui;
    private com.rumilance.practice.team.TeamService teamService;

    public DuelRequestGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            DuelRequestService duelRequestService,
            SettingsService settingsService,
            StatsService statsService,
            KitSelectGui kitSelectGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.DUEL_REQUEST, 6, true);
        this.kitService = kitService;
        this.duelRequestService = duelRequestService;
        this.settingsService = settingsService;
        this.statsService = statsService;
        this.kitSelectGui = kitSelectGui;
        this.messageService = messageService;
    }

    public void setMapSelectGui(DuelMapSelectGui mapSelectGui) {
        this.mapSelectGui = mapSelectGui;
    }

    public void setTeamService(com.rumilance.practice.team.TeamService teamService) {
        this.teamService = teamService;
    }

    public DuelMapSelectGui mapSelectGui() {
        return mapSelectGui;
    }

    public void openFor(Player sender, Player target, boolean ranked) {
        GuiSession session = registry.open(sender.getUniqueId(), type(), rows);
        session.setTargetPlayer(target.getUniqueId());
        session.setRanked(ranked);
        if (session.bestOf() < 1) {
            session.setBestOf(1);
        }
        if (session.selectedKit() == null) {
            kitService.enabled().stream().findFirst().ifPresent(k -> session.setSelectedKit(k.name()));
        }
        PracticeGuiOpen.open(this, sender, session);
        sounds.play(sender, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return messageService.render(messageService.resolveLocale(player),
                session.ranked() ? "duel-gui.title-ranked" : "duel-gui.title-unranked");
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        String locale = messageService.resolveLocale(player);
        UUID targetId = session.targetPlayer();
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);

        // Opponent head on the top bar with ping, W/L and K/D beneath it.
        ItemBuilder headBuilder = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(target != null
                        ? Component.text(target.getName(), NamedTextColor.YELLOW)
                        : Component.text("?", NamedTextColor.GRAY))
                .skullOwner(target)
                .action("head");
        if (target != null) {
            headBuilder.lore(
                    messageService.render(locale, "duel-gui.ping",
                            MessageService.tags("n", String.valueOf(target.getPing())))
                            .decoration(TextDecoration.ITALIC, false));
            try {
                RankedKitStats stats = statsService.kitStats(targetId,
                                session.selectedKit() == null ? "nodebuff" : session.selectedKit())
                        .orElse(RankedKitStats.starting(targetId, "nodebuff"));
                headBuilder.lore(
                        messageService.render(locale, "duel-gui.record",
                                        MessageService.tags("wins", String.valueOf(stats.wins()),
                                                "losses", String.valueOf(stats.losses())))
                                .decoration(TextDecoration.ITALIC, false),
                        messageService.render(locale, "duel-gui.kd",
                                        MessageService.tags("kd", String.format("%.2f", statsService.kd(stats)),
                                                "wr", statsService.winRateLabel(stats)))
                                .decoration(TextDecoration.ITALIC, false));
            } catch (Exception ignored) {
                // Stats are best-effort; the head still renders without them.
            }
        }
        inventory.setItem(GuiSlots.slot(0, 4), headBuilder.build());

        // Configuration tiles.
        inventory.setItem(GuiSlots.slot(2, 3), GuiDecorator.button(Material.DIAMOND_SWORD,
                messageService.render(locale, "duel-gui.kit-select",
                        MessageService.tags("kit", session.selectedKit() == null ? "nodebuff" : session.selectedKit())), "kit"));
        String mapLabel = session.selectedMap() == null || session.selectedMap().isBlank()
                || "random".equalsIgnoreCase(session.selectedMap())
                ? "Random"
                : com.rumilance.practice.util.KitNames.pretty(session.selectedMap());
        inventory.setItem(GuiSlots.slot(2, 5), GuiDecorator.button(Material.GRASS_BLOCK,
                messageService.render(locale, "duel-gui.map-select",
                        MessageService.tags("map", mapLabel)), "map"));
        ItemStack modeButton = GuiDecorator.button(
                session.ranked() ? Material.PURPLE_DYE : Material.BLUE_DYE,
                messageService.render(locale, session.ranked() ? "duel-gui.mode-ranked" : "duel-gui.mode-unranked"), "mode");
        modeButton.editMeta(meta -> meta.setEnchantmentGlintOverride(session.ranked()));
        inventory.setItem(GuiSlots.slot(3, 4), modeButton);
        inventory.setItem(GuiSlots.slot(4, 2), GuiDecorator.button(Material.BARRIER,
                messageService.render(locale, "duel-gui.cancel"), "cancel"));
        inventory.setItem(GuiSlots.slot(4, 4), GuiDecorator.button(Material.CLOCK,
                messageService.render(locale, "duel-gui.best-of", MessageService.tags("n", String.valueOf(session.bestOf()))), "bestof"));
        boolean pending = Boolean.TRUE.equals(session.get("pending", Boolean.class));
        inventory.setItem(GuiSlots.slot(4, 6), GuiDecorator.button(
                pending ? Material.YELLOW_GLAZED_TERRACOTTA : Material.EMERALD,
                messageService.render(locale, pending ? "duel-gui.pending" : "duel-gui.send"), "send"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "cancel" -> {
                sounds.play(player, "cancel");
                player.closeInventory();
            }
            case "kit" -> {
                player.closeInventory();
                kitSelectGui.openFor(player, session);
            }
            case "map" -> {
                if (mapSelectGui != null) {
                    player.closeInventory();
                    mapSelectGui.openFor(player, session);
                } else {
                    sounds.play(player, "error");
                }
            }
            case "mode" -> {
                session.setRanked(!session.ranked());
                sounds.play(player, "gui-click");
                render(player, session, inventory);
            }
            case "bestof" -> {
                session.setBestOf(session.bestOf() == 1 ? 3 : session.bestOf() == 3 ? 5 : 1);
                sounds.play(player, "gui-click");
                render(player, session, inventory);
            }
            case "send" -> send(player, session, inventory);
            default -> {
            }
        }
    }

    private void send(Player player, GuiSession session, Inventory inventory) {
        if (Boolean.TRUE.equals(session.get("pending", Boolean.class))) {
            return;
        }
        if (teamService != null && teamService.teamOf(player.getUniqueId()).isPresent()) {
            sounds.play(player, "error");
            messageService.send(player, "party.solo-only");
            return;
        }
        UUID targetId = session.targetPlayer();
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            sounds.play(player, "error");
            return;
        }
        if (!settingsService.get(target).acceptDuelRequests()) {
            sounds.play(player, "error");
            messageService.send(player, "duel.target-denying");
            return;
        }
        String kit = session.selectedKit() == null ? "nodebuff" : session.selectedKit();
        String map = session.selectedMap();
        int cooldown = duelRequestService.remainingCooldownSeconds(player.getUniqueId(), targetId);
        if (cooldown > 0) {
            sounds.play(player, "error");
            messageService.send(player, "duel.request-cooldown",
                    MessageService.tags("secs", String.valueOf(cooldown)));
            return;
        }
        if (duelRequestService.create(player.getUniqueId(), targetId, kit, session.ranked(),
                session.bestOf(), map).isEmpty()) {
            sounds.play(player, "error");
            messageService.send(player, "duel.could-not-send");
            return;
        }
        session.put("pending", Boolean.TRUE);
        sounds.play(player, "duel-request-sent");
        if (settingsService.get(target).soundsEnabled()) {
            sounds.play(target, "duel-request-received");
        }
        boolean ranked = session.ranked();
        String senderLocale = messageService.resolveLocale(player);
        String targetLocale = messageService.resolveLocale(target);
        player.sendMessage(messageService.render(senderLocale, "duel.request-sent",
                        MessageService.tags("mode", messageService.modeWord(player, ranked),
                                "kit", kit, "target", target.getName()))
                .append(Component.newline())
                .append(Component.text("[CANCEL]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rpcancel"))));
        target.sendMessage(messageService.render(targetLocale, "duel.request-received",
                        MessageService.tags("mode", messageService.modeWord(target, ranked),
                                "kit", kit, "sender", player.getName()))
                .append(Component.newline())
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rpaccept " + player.getName())))
                .append(Component.space())
                .append(Component.text("[DENY]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rpdeny " + player.getName()))));
        render(player, session, inventory);
    }
}
