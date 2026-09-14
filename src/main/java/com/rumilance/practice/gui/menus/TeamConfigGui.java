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
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamConfig;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-team battle settings for multi-team party battles (owner only) — ITEM 40b rebuild
 * following the {@code gui.json} "Team List" mockup: button-based team adding instead of the
 * old click-to-cycle book. One column per active team (columns 1..n):
 * <ul>
 *   <li>row 1 — team color (wool; click cycles the color, swapping rosters)</li>
 *   <li>row 2 — team max health (L +2 / R -2 / shift reset to 20)</li>
 *   <li>row 3 — team body size (L +0.25 / R -0.25 / shift reset to 1.0)</li>
 *   <li>row 4 — permanent potion effects (L cycles presets / R clears)</li>
 *   <li>row 5 — own-kit override (L next kit / R previous / shift off)</li>
 * </ul>
 * Right after the last team column sits the lime <b>Add Team</b> button; columns beyond the
 * rank limit show barrier locks explaining which rank unlocks them. Row 0 carries the
 * matching <b>Remove Team</b> button, the live team-count chip and reset-all.
 */
public final class TeamConfigGui extends AbstractGui {

    /** Team columns live at cols 1..7 (col 0/8 belong to the frame). */
    private static final int LAST_TEAM_COL = 7;
    /** Team slots 4-5 need VIP; slots 6-7 need VIP+ (mirrors {@code TeamService.maxTeamsFor}). */
    private static final int VIP_PLUS_THRESHOLD = 5;

    /**
     * Curated permanent-effect bundles the owner can cycle through. Values are 0-based
     * amplifiers (like vanilla /effect): 0 = level I, 1 = level II.
     */
    private static final List<Map<PotionEffectType, Integer>> EFFECT_PRESETS = List.of(
            Map.of(),
            Map.of(PotionEffectType.SPEED, 0),
            Map.of(PotionEffectType.SPEED, 1),
            Map.of(PotionEffectType.JUMP_BOOST, 0),
            Map.of(PotionEffectType.STRENGTH, 0),
            Map.of(PotionEffectType.RESISTANCE, 0),
            Map.of(PotionEffectType.SPEED, 0, PotionEffectType.JUMP_BOOST, 0),
            Map.of(PotionEffectType.SPEED, 0, PotionEffectType.STRENGTH, 0));

    private final TeamService teamService;
    private final KitService kitService;
    private TeamHubGui teamHubGui;
    private TeamSettingsGui teamSettingsGui;

    public TeamConfigGui(GuiSessionRegistry registry, SoundService sounds,
                         TeamService teamService, KitService kitService) {
        super(registry, sounds, GuiType.TEAM_CONFIG, 6, true);
        this.teamService = teamService;
        this.kitService = kitService;
    }

    public void setTeamHubGui(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    public void setTeamSettingsGui(TeamSettingsGui teamSettingsGui) {
        this.teamSettingsGui = teamSettingsGui;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.WHITE;
    }

    @Override
    protected Material titleIcon() {
        return Material.WHITE_BANNER;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.team-config-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(player.getUniqueId())) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.party-owner-only"))
                            .lore(UiTheme.line(line(player, "party.owner-only-lore")))
                            .action("decorate")
                            .build());
            backToHub(inventory, player);
            return;
        }

        List<TeamColor> colors = team.activeColors();
        int teamCount = colors.size();
        int maxTeams = teamService.maxTeamsFor(player.getUniqueId());

        // --- row 0: remove-team button, live count chip, reset ---
        boolean canRemove = teamCount > 2;
        inventory.setItem(GuiSlots.slot(0, 1),
                ItemBuilder.of(canRemove ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                        .name(t(player, "gui.team-remove-team")
                                .color(canRemove ? UiTheme.DANGER : UiTheme.MUTED))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, canRemove
                                        ? "gui.team-remove-team-hint" : "gui.team-remove-team-min")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action(canRemove ? "team:remove" : "decorate").build());
        inventory.setItem(GuiSlots.slot(0, 2),
                ItemBuilder.of(Material.BOOK)
                        .name(Component.text(line(player, "gui.team-count-label")
                                .replace("<n>", String.valueOf(teamCount)), UiTheme.HEADER)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue(line(player, "gui.team-count-max"), String.valueOf(maxTeams)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.team-count-info-hint")))
                        .action("decorate").build());
        inventory.setItem(GuiSlots.slot(0, 7),
                ItemBuilder.of(Material.WATER_BUCKET)
                        .name(t(player, "gui.team-config-reset").color(UiTheme.WARNING))
                        .lore(UiTheme.hint(line(player, "gui.team-config-reset-hint")))
                        .action("reset_all").build());

        // --- row 1: team headers, then the Add Team button, then locked/free slots ---
        for (int slotNum = teamCount + 1; slotNum <= LAST_TEAM_COL; slotNum++) {
            if (slotNum == teamCount + 1 && teamCount < maxTeams) {
                inventory.setItem(GuiSlots.slot(1, slotNum),
                        ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                                .name(t(player, "gui.team-add-team").color(UiTheme.SUCCESS))
                                .lore(UiTheme.divider(),
                                        UiTheme.line(line(player, "gui.team-add-team-hint")),
                                        UiTheme.blank(),
                                        UiTheme.hint(line(player, "gui.toggle-hint")))
                                .action("team:add").build());
            } else if (slotNum > maxTeams) {
                inventory.setItem(GuiSlots.slot(1, slotNum),
                        ItemBuilder.of(Material.BARRIER)
                                .name(t(player, slotNum <= VIP_PLUS_THRESHOLD
                                        ? "gui.team-lock-vip" : "gui.team-lock-vipplus")
                                        .color(UiTheme.MUTED))
                                .action("decorate").build());
            } else {
                inventory.setItem(GuiSlots.slot(1, slotNum), fillerPane(player));
            }
        }

        // --- one column per active team (columns 1..n) ---
        for (int i = 0; i < colors.size(); i++) {
            TeamColor color = colors.get(i);
            TeamConfig config = team.configOf(color);
            int col = i + 1;
            int memberCount = team.side(color).size();

            inventory.setItem(GuiSlots.slot(1, col),
                    ItemBuilder.of(color.wool())
                            .name(Component.text(line(player, "gui.team-column-title")
                                    .replace("<team>", color.label()), color.textColor())
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue(line(player, "gui.team-members-label"),
                                            String.valueOf(memberCount)),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.team-color-hint")))
                            .action("color:" + color.name()).build());

            inventory.setItem(GuiSlots.slot(2, col),
                    ItemBuilder.of(Material.GOLDEN_APPLE)
                            .name(Component.text(line(player, "gui.team-hp-label")
                                    .replace("<hp>", String.valueOf((int) config.maxHealth())), UiTheme.VALUE)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(UiTheme.hint(line(player, "gui.team-hp-hint")))
                            .action("hp:" + color.name()).build());

            inventory.setItem(GuiSlots.slot(3, col),
                    ItemBuilder.of(Material.SLIME_BALL)
                            .name(Component.text(line(player, "gui.team-size-label")
                                    .replace("<size>", String.format(Locale.ROOT, "%.2f", config.scale())),
                                    UiTheme.VALUE)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(UiTheme.hint(line(player, "gui.team-size-hint")))
                            .action("scale:" + color.name()).build());

            inventory.setItem(GuiSlots.slot(4, col),
                    ItemBuilder.of(config.effects().isEmpty() ? Material.GLASS_BOTTLE : Material.POTION)
                            .name(Component.text(line(player, "gui.team-fx-label"), UiTheme.VALUE)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(loreWithEffects(player, config.effects(),
                                    line(player, "gui.team-fx-hint")))
                            .action("fx:" + color.name()).build());

            String kitId = config.customKitId();
            boolean hasKit = kitId != null && !kitId.isBlank();
            String kitLabel = hasKit
                    ? kitService.get(kitId).map(KitDefinition::displayName).orElse(kitId)
                    : line(player, "gui.team-kit-default");
            inventory.setItem(GuiSlots.slot(5, col),
                    ItemBuilder.of(hasKit ? Material.CHEST : Material.ENDER_CHEST)
                            .name(Component.text(line(player, "gui.team-kit-label")
                                    .replace("<kit>", kitLabel),
                                    hasKit ? UiTheme.SUCCESS : UiTheme.MUTED)
                                    .decoration(TextDecoration.ITALIC, false))
                            .lore(UiTheme.hint(line(player, "gui.team-kit-hint")))
                            .glintIf(hasKit)
                            .action("kit:" + color.name()).build());
        }

        // --- config rows for not-yet-created teams stay as quiet filler ---
        for (int col = teamCount + 1; col <= LAST_TEAM_COL; col++) {
            for (int row = 2; row <= 5; row++) {
                inventory.setItem(GuiSlots.slot(row, col), fillerPane(player));
            }
        }

        backToHub(inventory, player);
    }

    /** Quiet "no team here" tile (gui.json mockup: "Not Avalible Team"). */
    private ItemStack fillerPane(Player player) {
        return ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name(Component.text(line(player, "gui.team-slot-unavailable"), UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false))
                .action("decorate").build();
    }

    private void backToHub(Inventory inventory, Player player) {
        inventory.setItem(GuiSlots.slot(5, 8),
                ItemBuilder.of(UiTheme.BACK)
                        .name(t(player, "menu.back").color(UiTheme.WARNING))
                        .action("back_to_hub").build());
    }

    private Component[] loreWithEffects(Player player, Map<PotionEffectType, Integer> effects, String hint) {
        if (effects.isEmpty()) {
            return new Component[]{
                    UiTheme.line(line(player, "gui.team-fx-none")),
                    UiTheme.blank(),
                    UiTheme.hint(hint)};
        }
        java.util.List<Component> lines = new java.util.ArrayList<>();
        // Stored values are 0-based amplifiers; show the human level (I, II, ...).
        effects.forEach((type, amplifier) -> lines.add(UiTheme.labelValue(
                prettyEffectName(type), roman(amplifier + 1))));
        lines.add(UiTheme.blank());
        lines.add(UiTheme.hint(hint));
        return lines.toArray(new Component[0]);
    }

    private static String prettyEffectName(PotionEffectType type) {
        String name = type.getKey().getKey().replace('_', ' ');
        StringBuilder out = new StringBuilder(name.length());
        boolean upper = true;
        for (char c : name.toCharArray()) {
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return out.toString();
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(level);
        };
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType clickType) {
        if ("back_to_hub".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if (teamSettingsGui != null) {
                teamSettingsGui.open(player);
            } else if (teamHubGui != null) {
                teamHubGui.open(player);
            }
            return;
        }
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(player.getUniqueId())) {
            return;
        }

        if ("team:add".equals(action)) {
            TeamService.Result r = teamService.setTeamCount(player, team.teamCount() + 1);
            sounds.play(player, r == TeamService.Result.OK ? "gui-click" : "error");
            refresh(player, session, inventory);
            return;
        }
        if ("team:remove".equals(action)) {
            TeamService.Result r = teamService.setTeamCount(player, team.teamCount() - 1);
            sounds.play(player, r == TeamService.Result.OK ? "gui-click" : "error");
            refresh(player, session, inventory);
            return;
        }
        if ("reset_all".equals(action)) {
            for (TeamColor color : team.activeColors()) {
                team.setConfig(color, null);
            }
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }

        if (action.startsWith("color:")) {
            TeamColor color = parseColor(action);
            if (color != null) {
                TeamService.Result r = teamService.cycleTeamColor(player, color);
                sounds.play(player, r == TeamService.Result.OK ? "gui-click" : "error");
                refresh(player, session, inventory);
            }
            return;
        }

        TeamColor color = parseColor(action);
        if (color == null || !team.activeColors().contains(color)) {
            return;
        }
        TeamConfig config = team.configOf(color);
        boolean changed = false;

        if (action.startsWith("hp:")) {
            double hp = config.maxHealth();
            if (isShift(clickType)) {
                hp = TeamConfig.DEFAULT_MAX_HEALTH;
            } else if (clickType == ClickType.RIGHT) {
                hp = Math.max(TeamConfig.MIN_MAX_HEALTH, hp - 2);
            } else {
                hp = Math.min(TeamConfig.MAX_MAX_HEALTH, hp + 2);
            }
            team.setConfig(color, config.withMaxHealth(hp));
            changed = true;
        } else if (action.startsWith("scale:")) {
            double scale = config.scale();
            if (isShift(clickType)) {
                scale = TeamConfig.DEFAULT_SCALE;
            } else if (clickType == ClickType.RIGHT) {
                scale = Math.max(TeamConfig.MIN_SCALE, scale - 0.25d);
            } else {
                scale = Math.min(TeamConfig.MAX_SCALE, scale + 0.25d);
            }
            team.setConfig(color, config.withScale(scale));
            changed = true;
        } else if (action.startsWith("fx:")) {
            if (clickType == ClickType.RIGHT && !isShift(clickType)) {
                team.setConfig(color, config.withEffects(Map.of()));
            } else {
                team.setConfig(color, config.withEffects(nextPreset(config.effects())));
            }
            changed = true;
        } else if (action.startsWith("kit:")) {
            if (isShift(clickType)) {
                team.setConfig(color, config.withCustomKitId(null));
            } else {
                int direction = clickType == ClickType.RIGHT ? -1 : 1;
                team.setConfig(color, config.withCustomKitId(nextKit(config.customKitId(), direction)));
            }
            changed = true;
        }

        if (changed) {
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
        }
    }

    private static boolean isShift(ClickType clickType) {
        return clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT;
    }

    private static TeamColor parseColor(String action) {
        int colon = action.indexOf(':');
        if (colon < 0 || colon == action.length() - 1) {
            return null;
        }
        try {
            return TeamColor.valueOf(action.substring(colon + 1).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<PotionEffectType, Integer> nextPreset(Map<PotionEffectType, Integer> current) {
        int index = EFFECT_PRESETS.indexOf(current);
        return EFFECT_PRESETS.get((index + 1) % EFFECT_PRESETS.size());
    }

    /** Cycles to the next (or previous) enabled kit; null current starts with the first. */
    private String nextKit(String currentKitId, int direction) {
        List<KitDefinition> kits = com.rumilance.practice.gui.KitSections.ordered(kitService);
        if (kits.isEmpty()) {
            return null;
        }
        if (currentKitId == null) {
            return kits.get(0).name();
        }
        int index = -1;
        for (int i = 0; i < kits.size(); i++) {
            if (kits.get(i).name().equals(currentKitId)) {
                index = i;
                break;
            }
        }
        return kits.get(Math.floorMod(index + direction, kits.size())).name();
    }
}
