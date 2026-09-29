package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.NameDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Party Fight map picker — lists arenas flagged {@code party} with their icon blocks.
 */
public final class PartyMapSelectGui extends AbstractGui {

    private final TeamService teamService;
    private final ArenaTemplateStore arenaStore;
    private final KitService kitService;
    private TeamHubGui teamHubGui;
    private TeamKitSelectGui teamKitSelectGui;
    /** Kit chosen on the previous screen; copied into the fresh GUI session on open. */
    private volatile String pendingKitId;
    /** 中キット chosen with the kit (null = the kit's own default loadout). */
    private volatile String pendingInnerKitId;

    public PartyMapSelectGui(GuiSessionRegistry registry, SoundService sounds,
                             TeamService teamService, ArenaTemplateStore arenaStore,
                             KitService kitService) {
        super(registry, sounds, GuiType.PARTY_MAP, 6, true);
        this.teamService = teamService;
        this.arenaStore = arenaStore;
        this.kitService = kitService;
    }

    public void setTeamHubGui(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    /** Set so a kit-less open routes back to kit selection. Wired from bootstrap. */
    public void setTeamKitSelectGui(TeamKitSelectGui teamKitSelectGui) {
        this.teamKitSelectGui = teamKitSelectGui;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.CYAN;
    }

    @Override
    protected Material titleIcon() {
        return Material.FILLED_MAP;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "party.map-title").color(UiTheme.PRIMARY);
    }

    /**
     * Opens the map picker for the given kit (called from {@link TeamKitSelectGui}). The
     * kit id is stashed and copied into the fresh GUI session by {@link #configureSession}.
     */
    public void openForKit(Player player, String kitId) {
        openForKit(player, kitId, null);
    }

    /**
     * Party flow with a 中キット: {@code innerKitId} is the preset every member fights with,
     * {@code null} / blank / {@code default} keeps the kit's own loadout.
     */
    public void openForKit(Player player, String kitId, String innerKitId) {
        this.pendingKitId = kitId;
        this.pendingInnerKitId = com.rumilance.practice.kit.InnerKitService.isDefault(innerKitId)
                ? null
                : com.rumilance.practice.kit.InnerKitService.normalizeId(innerKitId);
        open(player);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        if (pendingKitId != null) {
            session.put("kit_id", pendingKitId);
        }
        if (pendingInnerKitId != null) {
            session.put(InnerKitSelectGui.CHOICE_KEY, pendingInnerKitId);
        }
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        String kitId = session.get("kit_id", String.class);
        String current = team == null ? null : team.selectedArena();

        inventory.setItem(MenuScaffold.gridSlot(0), ItemBuilder.of(Material.ENDER_EYE)
                .name(t(player, "party.random").color(UiTheme.PRIMARY))
                .lore(UiTheme.hint(line(player, "gui.party-map-click")))
                .action("map:random")
                .build());

        List<ArenaTemplate> maps = partyPool(kitId);
        int index = 1;
        for (ArenaTemplate t : maps) {
            if (index >= MenuScaffold.gridPageSize() - 1) {
                break;
            }
            Material icon = Material.matchMaterial(t.iconMaterial() == null ? "" : t.iconMaterial());
            if (icon == null || icon.isAir()) {
                icon = Material.GRASS_BLOCK;
            }
            boolean selected = t.name().equals(current);
            inventory.setItem(MenuScaffold.gridSlot(index++), ItemBuilder.of(icon)
                    .name(Component.text(NameDisplay.pretty(t.name()), UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.labelValue(line(player, "gui.arena-id"), t.name()),
                            selected
                                    ? UiTheme.status(line(player, "party.selected"), UiTheme.SUCCESS)
                                    : UiTheme.hint(line(player, "gui.party-map-click"))
                    )
                    .glint(selected)
                    .action("map:" + t.name())
                    .build());
        }
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-back");
            String kitId = session.get("kit_id", String.class);
            if (kitId != null && teamKitSelectGui != null) {
                // Back with selection preserved (map chip から来た場合)。
                String innerKitId = session.get(InnerKitSelectGui.CHOICE_KEY, String.class);
                teamKitSelectGui.openResume(player, kitId, innerKitId);
            } else if (teamSettingsGui != null) {
                teamSettingsGui.open(player);
            } else if (teamHubGui != null) {
                teamHubGui.open(player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (action != null && action.startsWith("map:")) {
            // [Party GUI 刷新 2026-09-29] マップの確定のみする — バトルはキット選択画面の
            // START ヒーローボタンから始める。誤タップで試合が始まる事故は消滅する。
            String map = action.substring("map:".length());
            String kitId = session.get("kit_id", String.class);
            String arena = "random".equalsIgnoreCase(map) ? null : map;
            TeamService.Result r = teamService.setSelectedArena(player, arena);
            if (r != TeamService.Result.OK) {
                sounds.play(player, "error");
                return;
            }
            sounds.play(player, "select");
            String innerKitId = session.get(InnerKitSelectGui.CHOICE_KEY, String.class);
            this.pendingKitId = null;
            this.pendingInnerKitId = null;
            if (kitId != null && teamKitSelectGui != null) {
                // キット選択画面へ選定済みキット付きで帰る(1 tick 遅延で close 競合を防ぐ)
                player.closeInventory();
                final String k = kitId;
                final String in = innerKitId;
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> { if (player.isOnline()) { teamKitSelectGui.openResume(player, k, in); } });
            } else if (teamSettingsGui != null) {
                // 設定画面(マップだけ選びに来た)へ戻る
                player.closeInventory();
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> { if (player.isOnline()) { teamSettingsGui.open(player); } });
            } else {
                player.closeInventory();
            }
        }
    }

    /** 設定画面からマップ選択したときの帰り場所。 */
    private TeamSettingsGui teamSettingsGui;

    public void setTeamSettingsGui(TeamSettingsGui teamSettingsGui) {
        this.teamSettingsGui = teamSettingsGui;
    }

    private List<ArenaTemplate> partyPool(String kitId) {
        List<String> kitPool = kitId == null ? List.of()
                : kitService.get(kitId).map(KitDefinition::partyArenas).orElse(List.of());
        List<ArenaTemplate> allParty = arenaStore.partyArenas();
        if (kitPool.isEmpty()) {
            return allParty;
        }
        return allParty.stream()
                .filter(t -> kitPool.contains(t.name().toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }
}
