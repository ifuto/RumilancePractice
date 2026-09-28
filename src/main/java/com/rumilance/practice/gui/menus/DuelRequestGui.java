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
    private com.rumilance.practice.match.MatchService matchService;
    /** 中キット (inner kits): the preset this duel fights with, chosen by right-clicking a kit. */
    private com.rumilance.practice.kit.InnerKitService innerKits;

    public void setInnerKits(com.rumilance.practice.kit.InnerKitService innerKits) {
        this.innerKits = innerKits;
    }

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

    public void setMatchService(com.rumilance.practice.match.MatchService matchService) {
        this.matchService = matchService;
    }

    public DuelMapSelectGui mapSelectGui() {
        return mapSelectGui;
    }

    public void openFor(Player sender, Player target, boolean ranked) {
        openFor(sender, target, ranked, null, null, 0);
    }

    /**
     * Opens the request GUI carrying the caller's prior choices (kit / map / best-of). The
     * values MUST be set on the freshly created session BEFORE it renders — otherwise the GUI
     * is drawn with the default kit and only updated afterwards, which looked like the kit
     * selection never changed (always showed the first kit, e.g. Sword).
     */
    public void openFor(Player sender, Player target, boolean ranked,
                        String kit, String map, int bestOf) {
        openFor(sender, target, ranked, kit, map, bestOf, null);
    }

    /**
     * Same, carrying the chosen 中キット (inner kit). Like the kit itself the choice MUST be on the
     * session before the first render, or the kit tile is drawn as if the default were selected.
     */
    public void openFor(Player sender, Player target, boolean ranked,
                        String kit, String map, int bestOf, String innerKit) {
        GuiSession session = registry.open(sender.getUniqueId(), type(), rows);
        if (!com.rumilance.practice.kit.InnerKitService.isDefault(innerKit)) {
            session.put(InnerKitSelectGui.CHOICE_KEY,
                    com.rumilance.practice.kit.InnerKitService.normalizeId(innerKit));
        }
        session.setTargetPlayer(target.getUniqueId());
        session.setRanked(ranked);
        if (bestOf >= 1) {
            session.setBestOf(bestOf);
        } else if (session.bestOf() < 1) {
            session.setBestOf(1);
        }
        if (map != null && !map.isBlank()) {
            session.setSelectedMap(map);
        }
        String chosenKit = (kit != null && !kit.isBlank()) ? kit
                : session.selectedKit();
        if (chosenKit == null || chosenKit.isBlank()) {
            kitService.enabled().stream().findFirst()
                    .ifPresent(k -> session.setSelectedKit(kitService.playableId(k.name())));
        } else {
            // Keep the caller's kit, but fall back if it has since been disabled/removed.
            // フォルダ(中メニュー)が渡されたらデフォルトの子に解決する。子キットは enabled() の
            // 一覧には出ないので、妥当性の判定は get() 側で行う。
            String resolved = kitService.playableId(chosenKit);
            boolean usable = kitService.get(resolved).map(k -> k.enabled()).orElse(false);
            if (usable) {
                session.setSelectedKit(resolved);
            } else {
                kitService.enabled().stream().findFirst()
                        .ifPresent(k -> session.setSelectedKit(kitService.playableId(k.name())));
            }
        }
        PracticeGuiOpen.open(this, sender, session);
        sounds.play(sender, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.LIGHT_BLUE;
    }

    @Override
    protected Material titleIcon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return messageService.render(messageService.resolveLocale(player),
                session.ranked() ? "duel-gui.title-ranked" : "duel-gui.title-unranked");
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
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
        // 中キットを選んでいれば、その名前をキット名に続けて表示する（相手にも同じ文言が届く）。
        inventory.setItem(GuiSlots.slot(2, 3), GuiDecorator.button(Material.DIAMOND_SWORD,
                messageService.render(locale, "duel-gui.kit-select",
                        MessageService.tags("kit", kitLabel(session))), "kit"));
        String mapLabel = session.selectedMap() == null || session.selectedMap().isBlank()
                || "random".equalsIgnoreCase(session.selectedMap())
                ? "Random"
                : com.rumilance.practice.util.KitNames.pretty(session.selectedMap());
        inventory.setItem(GuiSlots.slot(2, 5), GuiDecorator.button(Material.GRASS_BLOCK,
                messageService.render(locale, "duel-gui.map-select",
                        MessageService.tags("map", mapLabel)), "map"));
        // Symmetric config row: FT (先取点数) on the left, queue mode on the right.
        // FT: click +1, shift-click +5, 40 → ∞ → 1. ∞ means no score limit.
        inventory.setItem(GuiSlots.slot(3, 3),
                com.rumilance.practice.gui.ItemBuilder.of(Material.NETHERITE_SCRAP)
                        .name(messageService.render(locale, "duel-gui.ft", MessageService.tags(
                                "n", com.rumilance.practice.match.FirstTo.label(session.firstTo()))))
                        .lore(
                                com.rumilance.practice.gui.UiTheme.divider(),
                                messageService.render(locale, "duel-gui.ft-lore"),
                                com.rumilance.practice.gui.UiTheme.blank(),
                                messageService.render(locale, "duel-gui.ft-hint")
                        )
                        .glint(session.firstTo() > 0)
                        .action("ft")
                        .build());
        ItemStack modeButton = GuiDecorator.button(
                session.ranked() ? Material.PURPLE_DYE : Material.BLUE_DYE,
                messageService.render(locale, session.ranked() ? "duel-gui.mode-ranked" : "duel-gui.mode-unranked"), "mode");
        modeButton.editMeta(meta -> meta.setEnchantmentGlintOverride(session.ranked()));
        inventory.setItem(GuiSlots.slot(3, 5), modeButton);
        // Footer: dismiss at the far left corner, the hero action (SEND) centred on the bottom row.
        inventory.setItem(GuiSlots.slot(4, 0), GuiDecorator.button(Material.BARRIER,
                messageService.render(locale, "duel-gui.cancel"), "cancel"));
        boolean pending = Boolean.TRUE.equals(session.get("pending", Boolean.class));
        inventory.setItem(GuiSlots.slot(4, 4), GuiDecorator.button(
                pending ? Material.YELLOW_GLAZED_TERRACOTTA : Material.EMERALD,
                messageService.render(locale, pending ? "duel-gui.pending" : "duel-gui.send"), "send"));
    }

    /** FT needs the click type: plain click +1, shift click +5. */
    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("ft".equals(action)) {
            boolean shift = clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                    || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
            session.setFirstTo(com.rumilance.practice.match.FirstTo.step(session.firstTo(),
                    shift ? com.rumilance.practice.match.FirstTo.STEP_LARGE
                          : com.rumilance.practice.match.FirstTo.STEP_SMALL));
            sounds.play(player, "gui-click");
            render(player, session, inventory);
            return;
        }
        handleClick(player, session, inventory, slot, action);
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
        if (matchService != null && matchService.isBusyForSoloDuel(player.getUniqueId())) {
            sounds.play(player, "error");
            messageService.send(player, "duel.already-in-match");
            return;
        }
        UUID targetId = session.targetPlayer();
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            sounds.play(player, "error");
            return;
        }
        // A 1v1 request can never be honoured against a party member, or against someone committed
        // to a fight — including a teammate eliminated from the party match (watching it).
        if (teamService != null && teamService.teamOf(targetId).isPresent()) {
            sounds.play(player, "error");
            messageService.send(player, "party.solo-only");
            return;
        }
        if (matchService != null && matchService.isBusyForSoloDuel(targetId)) {
            sounds.play(player, "error");
            messageService.send(player, "duel.target-already-in-match",
                    MessageService.tags("target", target.getName()));
            return;
        }
        if (!settingsService.get(target).acceptDuelRequests()) {
            sounds.play(player, "error");
            messageService.send(player, "duel.target-denying");
            return;
        }
        String kit = session.selectedKit() == null ? "nodebuff" : session.selectedKit();
        String innerChoice = session.get(InnerKitSelectGui.CHOICE_KEY, String.class);
        String kitLabel = kitLabel(session);
        String map = session.selectedMap();
        int cooldown = duelRequestService.remainingCooldownSeconds(player.getUniqueId(), targetId);
        if (cooldown > 0) {
            sounds.play(player, "error");
            messageService.send(player, "duel.request-cooldown",
                    MessageService.tags("secs", String.valueOf(cooldown)));
            return;
        }
        if (duelRequestService.create(player.getUniqueId(), targetId, kit, session.ranked(),
                session.bestOf(), map, session.firstTo(), innerChoice).isEmpty()) {
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
                                "kit", kitLabel, "target", target.getName()))
                .append(Component.newline())
                .append(Component.text("[CANCEL]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rpcancel"))));
        target.sendMessage(messageService.render(targetLocale, "duel.request-received",
                        MessageService.tags("mode", messageService.modeWord(target, ranked),
                                "kit", kitLabel, "sender", player.getName()))
                .append(Component.newline())
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rpaccept " + player.getName())))
                .append(Component.space())
                .append(Component.text("[DENY]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rpdeny " + player.getName()))));
        render(player, session, inventory);
    }

    /**
     * What the kit tile and both request messages call this fight's kit: the plain kit name, or
     * the 中キット's display name when one was picked by right-clicking the kit.
     */
    private String kitLabel(GuiSession session) {
        String kit = session.selectedKit() == null ? "nodebuff" : session.selectedKit();
        String inner = session.get(InnerKitSelectGui.CHOICE_KEY, String.class);
        String pretty = kitService.get(kit)
                .map(com.rumilance.practice.gui.KitDisplayNames::plain).orElse(kit);
        if (innerKits == null || com.rumilance.practice.kit.InnerKitService.isDefault(inner)) {
            return pretty; // a child is a normal kit; show that kit's own display name
        }
        return innerKits.displayOf(kit, inner, pretty);
    }
}
