package com.rumilance.practice.gui.menus;

import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DuelRequestGui extends AbstractGui {

    private final KitService kitService;
    private final DuelRequestService duelRequestService;
    private final SettingsService settingsService;
    private final StatsService statsService;
    private final MapSelectGui mapSelectGui;
    private final KitSelectGui kitSelectGui;
    private final MessageService messageService;

    public DuelRequestGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            DuelRequestService duelRequestService,
            SettingsService settingsService,
            StatsService statsService,
            MapSelectGui mapSelectGui,
            KitSelectGui kitSelectGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.DUEL_REQUEST, 6, true);
        this.kitService = kitService;
        this.duelRequestService = duelRequestService;
        this.settingsService = settingsService;
        this.statsService = statsService;
        this.mapSelectGui = mapSelectGui;
        this.kitSelectGui = kitSelectGui;
        this.messageService = messageService;
    }

    public void openFor(Player sender, Player target, boolean ranked) {
        GuiSession session = registry.open(sender.getUniqueId(), type(), rows);
        session.setTargetPlayer(target.getUniqueId());
        session.setRanked(ranked);
        if (session.bestOf() < 1) {
            session.setBestOf(1);
        }
        if (session.selectedMap() == null) {
            session.setSelectedMap(ArenaTerrain.ANY.name());
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
        String locale = messageService.resolveLocale(player);
        UUID targetId = session.targetPlayer();
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        if (target != null) {
            skull.setOwningPlayer(target);
            skull.displayName(Component.text(target.getName(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(messageService.render(locale, "duel-gui.ping",
                    MessageService.tags("n", String.valueOf(target.getPing())))
                    .decoration(TextDecoration.ITALIC, false));
            try {
                RankedKitStats stats = statsService.kitStats(targetId,
                                session.selectedKit() == null ? "nodebuff" : session.selectedKit())
                        .orElse(RankedKitStats.starting(targetId, "nodebuff"));
                lore.add(messageService.render(locale, "duel-gui.record",
                                MessageService.tags("wins", String.valueOf(stats.wins()),
                                        "losses", String.valueOf(stats.losses())))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(messageService.render(locale, "duel-gui.kd",
                                MessageService.tags("kd", String.format("%.2f", statsService.kd(stats)),
                                        "wr", statsService.winRateLabel(stats)))
                        .decoration(TextDecoration.ITALIC, false));
            } catch (Exception ignored) {
                // ignore
            }
            skull.lore(lore);
        }
        skull.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "head");
        head.setItemMeta(skull);
        inventory.setItem(GuiSlots.slot(0, 4), head);
        inventory.setItem(GuiSlots.slot(2, 3), GuiDecorator.button(Material.DIAMOND_SWORD,
                messageService.render(locale, "duel-gui.kit-select",
                        MessageService.tags("kit", session.selectedKit() == null ? "nodebuff" : session.selectedKit())), "kit"));
        inventory.setItem(GuiSlots.slot(2, 5), GuiDecorator.button(Material.GRASS_BLOCK,
                messageService.render(locale, "duel-gui.map-select",
                        MessageService.tags("map", session.selectedMap() == null ? "ANY" : session.selectedMap())), "map"));
        inventory.setItem(GuiSlots.slot(3, 4), GuiDecorator.button(
                session.ranked() ? Material.PURPLE_DYE : Material.BLUE_DYE,
                messageService.render(locale, session.ranked() ? "duel-gui.mode-ranked" : "duel-gui.mode-unranked"), "mode"));
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
                player.closeInventory();
                mapSelectGui.openFor(player, session);
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
        ArenaTerrain terrain = ArenaTerrain.ANY;
        try {
            terrain = ArenaTerrain.valueOf(session.selectedMap() == null ? "ANY" : session.selectedMap());
        } catch (Exception ignored) {
            // keep ANY
        }
        if (duelRequestService.create(player.getUniqueId(), targetId, kit, session.ranked(), terrain, session.bestOf()).isEmpty()) {
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
                        .clickEvent(ClickEvent.runCommand("/duel cancel"))));
        target.sendMessage(messageService.render(targetLocale, "duel.request-received",
                        MessageService.tags("mode", messageService.modeWord(target, ranked),
                                "kit", kit, "sender", player.getName()))
                .append(Component.newline())
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/accept " + player.getName())))
                .append(Component.space())
                .append(Component.text("[DENY]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/deny " + player.getName()))));
        render(player, session, inventory);
    }
}
