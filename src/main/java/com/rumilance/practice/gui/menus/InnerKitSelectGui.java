package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.InnerKitService;
import com.rumilance.practice.kit.InnerKitService.InnerKit;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * 中キット (inner kit) picker — the list a right-click on a kit opens in Duel Request, Party
 * Fight and Kit Edit.
 *
 * <p>The first entry is always the kit itself, badged {@code [Default]}: it is what Queue hands
 * out, it is not a stored preset, and it cannot be changed, renamed, removed or replaced by
 * another preset. Everything below it is a real preset from
 * {@code kits.<kit>.inner-kits} in kits.yml, created with {@code /kit preset add} and edited in
 * the kit editor.</p>
 *
 * <p>Where the choice goes depends on the screen that opened this one ({@code session "origin"}):
 * a duel carries it into the duel request, a party battle carries it into the team match, and the
 * kit editor opens that preset's loadout for editing. Queue never comes here — clicking a kit in
 * the queue GUI keeps fighting the default, exactly as before.</p>
 */
public final class InnerKitSelectGui extends AbstractGui {

    /** Session key holding which screen opened this picker. */
    public static final String ORIGIN_KEY = "innerkit-origin";
    /** Session key holding the chosen preset id on the caller's session (null/absent = default). */
    public static final String CHOICE_KEY = "innerkit";

    public static final String ORIGIN_DUEL = "duel";
    public static final String ORIGIN_TEAM = "team";
    public static final String ORIGIN_EDIT = "edit";

    private final KitService kitService;
    private final InnerKitService innerKits;
    private KitSelectGui kitSelectGui;
    private DuelRequestGui duelRequestGui;
    private TeamKitSelectGui teamKitSelectGui;
    private EkitSelectGui ekitSelectGui;
    private EditKitGui editKitGui;

    public InnerKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                             KitService kitService, InnerKitService innerKits) {
        super(registry, sounds, GuiType.INNER_KIT_SELECT, 6, false);
        this.kitService = kitService;
        this.innerKits = innerKits;
    }

    public void setKitSelectGui(KitSelectGui kitSelectGui) {
        this.kitSelectGui = kitSelectGui;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    public void setTeamKitSelectGui(TeamKitSelectGui teamKitSelectGui) {
        this.teamKitSelectGui = teamKitSelectGui;
    }

    public void setEkitSelectGui(EkitSelectGui ekitSelectGui) {
        this.ekitSelectGui = ekitSelectGui;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    /** Right-click in the duel kit picker: choose the 中キット this duel fights with. */
    public void openForDuel(Player player, GuiSession parent, String kitId) {
        GuiSession session = begin(player, parent, kitId, ORIGIN_DUEL);
        if (session == null) {
            return;
        }
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        session.setFirstTo(parent.firstTo());
        session.setFromBattleMenu(parent.fromBattleMenu());
        finish(player, session);
    }

    /** Right-click in the party kit picker: choose the 中キット the team battle fights with. */
    public void openForTeam(Player player, GuiSession parent, String kitId) {
        GuiSession session = begin(player, parent, kitId, ORIGIN_TEAM);
        if (session == null) {
            return;
        }
        finish(player, session);
    }

    /** Right-click in the kit editor's picker: choose which 中キット to edit. */
    public void openForEdit(Player player, GuiSession parent, String kitId) {
        GuiSession session = begin(player, parent, kitId, ORIGIN_EDIT);
        if (session == null) {
            return;
        }
        finish(player, session);
    }

    private GuiSession begin(Player player, GuiSession parent, String kitId, String origin) {
        if (kitId == null || kitId.isBlank()) {
            return null;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put(ORIGIN_KEY, origin);
        session.setSelectedKit(kitId);
        session.setPage(0);
        if (parent != null) {
            // Remember where "back" returns to, and which category page the caller was on.
            session.setKitCategory(parent.kitCategory());
            // Show the caller's current choice as selected (a reopen must not look reset).
            session.put(CHOICE_KEY, parent.get(CHOICE_KEY, String.class));
        }
        return session;
    }

    private void finish(Player player, GuiSession session) {
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.CHEST;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        String name = kit == null ? KitNames.pretty(session.selectedKit()) : kit.prettyDisplayName();
        return t(player, "gui.innerkit-title").color(UiTheme.PRIMARY)
                .append(Component.text(" " + name, UiTheme.VALUE));
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        String kitId = session.selectedKit();
        KitDefinition kit = kitId == null ? null : kitService.get(kitId).orElse(null);
        if (kit == null) {
            inventory.setItem(MenuScaffold.gridSlot(13), ItemBuilder.of(Material.BARRIER)
                    .name(t(player, "gui.kit-none").color(UiTheme.DANGER))
                    .action("decorate")
                    .build());
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }
        List<InnerKit> presets = innerKits.list(kitId);
        String current = session.get(CHOICE_KEY, String.class);
        int index = 0;
        // The default first, always: the kit itself, badged and locked.
        inventory.setItem(MenuScaffold.gridSlot(index++),
                defaultTile(player, kit, InnerKitService.isDefault(current)));
        for (InnerKit preset : presets) {
            if (index >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++),
                    presetTile(player, session, kit, preset, preset.id().equals(
                            InnerKitService.normalizeId(current))));
        }
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    /** {@code HQ Style Axe [Default]} — the kit's own loadout, not a stored preset. */
    private ItemStack defaultTile(Player player, KitDefinition kit, boolean selected) {
        return ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                .nameMini(kit.prettyDisplayName() + " " + InnerKitService.DEFAULT_BADGE)
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.innerkit-default-lore")),
                        UiTheme.blank(),
                        selected
                                ? UiTheme.status(line(player, "gui.kit-selected"), UiTheme.SUCCESS)
                                : UiTheme.hint(line(player, "gui.innerkit-click-select"))
                )
                .glint(selected)
                .action("pick:" + InnerKitService.DEFAULT_ID)
                .build();
    }

    private ItemStack presetTile(Player player, GuiSession session, KitDefinition kit, InnerKit preset,
                                 boolean selected) {
        Material icon = Material.matchMaterial(preset.icon() == null ? kit.icon() : preset.icon());
        return ItemBuilder.of(icon == null ? Material.DIAMOND_SWORD : icon)
                .name(Component.text(preset.displayName(),
                        selected ? UiTheme.SUCCESS : UiTheme.VALUE))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, originLoreKey(session))),
                        UiTheme.blank(),
                        UiTheme.labelValue(line(player, "gui.innerkit-id-label"), preset.id()),
                        UiTheme.blank(),
                        selected
                                ? UiTheme.status(line(player, "gui.kit-selected"), UiTheme.SUCCESS)
                                : UiTheme.hint(line(player, "gui.innerkit-click-select"))
                )
                .glint(selected)
                .action("pick:" + preset.id())
                .build();
    }

    /** One hint line per origin, so the list says what the click will do. */
    private String originLoreKey(GuiSession session) {
        String origin = session.get(ORIGIN_KEY, String.class);
        if (ORIGIN_TEAM.equals(origin)) {
            return "gui.innerkit-team-lore";
        }
        if (ORIGIN_EDIT.equals(origin)) {
            return "gui.innerkit-edit-lore";
        }
        return "gui.innerkit-duel-lore";
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            backToOrigin(player, session);
            return;
        }
        if (!action.startsWith("pick:")) {
            return;
        }
        String chosen = action.substring("pick:".length()).toLowerCase(Locale.ROOT);
        String kitId = session.selectedKit();
        String origin = session.get(ORIGIN_KEY, String.class);
        String inner = InnerKitService.DEFAULT_ID.equals(chosen) ? null : chosen;
        sounds.play(player, "select");
        switch (origin == null ? ORIGIN_DUEL : origin) {
            case ORIGIN_TEAM -> {
                player.closeInventory();
                if (teamKitSelectGui != null) {
                    teamKitSelectGui.proceedWithKit(player, kitId, inner);
                }
            }
            case ORIGIN_EDIT -> {
                if (editKitGui != null) {
                    session.setNavigatingAway(true);
                    editKitGui.openKitEditor(player, kitId, null, null, inner);
                } else {
                    player.closeInventory();
                }
            }
            default -> returnToDuel(player, session, kitId, inner);
        }
    }

    /** Duel: reopen the request GUI with the kit AND the chosen 中キット on the fresh session. */
    private void returnToDuel(Player player, GuiSession session, String kitId, String inner) {
        Player target = session.targetPlayer() == null
                ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        boolean ranked = session.ranked();
        String map = session.selectedMap();
        int bestOf = session.bestOf();
        boolean fromBattle = session.fromBattleMenu();
        player.closeInventory();
        // The choice rides into openFor so the very first render already names the preset.
        duelRequestGui.openFor(player, target, ranked, kitId, map, bestOf, inner);
        registry.get(player.getUniqueId()).ifPresent(fresh -> fresh.setFromBattleMenu(fromBattle));
    }

    /** Back returns to the picker that opened this one, on the category page the player was on. */
    private void backToOrigin(Player player, GuiSession session) {
        String origin = session.get(ORIGIN_KEY, String.class);
        String category = session.kitCategory();
        if (ORIGIN_EDIT.equals(origin)) {
            if (ekitSelectGui != null) {
                session.setNavigatingAway(true);
                ekitSelectGui.open(player);
                return;
            }
        } else if (ORIGIN_TEAM.equals(origin)) {
            if (teamKitSelectGui != null) {
                session.setNavigatingAway(true);
                teamKitSelectGui.open(player);
                registry.get(player.getUniqueId()).ifPresent(fresh -> {
                    fresh.setKitCategory(category);
                    fresh.setPage(0);
                });
                return;
            }
        } else if (kitSelectGui != null) {
            session.setNavigatingAway(true);
            kitSelectGui.openFor(player, session);
            registry.get(player.getUniqueId()).ifPresent(fresh -> fresh.setKitCategory(category));
            return;
        }
        player.closeInventory();
    }
}
