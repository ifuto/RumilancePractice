package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.practice.BotDifficulty;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * ITEM 41 bot difficulty studio: the Quantum map's seven-rung ladder (NPC … SURVIVAL MASTER) as
 * one-click presets plus a fully detailed parameter board — every stat of the fighter is tunable,
 * which flips the preset to CUSTOM. Choices persist per player.
 */
public final class BotDifficultyGui extends AbstractGui {

    /** The ladder, in map order (rung 0 .. rung 6). */
    private static final BotDifficulty.Preset[] LADDER = {
            BotDifficulty.Preset.NPC,
            BotDifficulty.Preset.EASY,
            BotDifficulty.Preset.INTERMEDIATE,
            BotDifficulty.Preset.HARD,
            BotDifficulty.Preset.CRAZY,
            BotDifficulty.Preset.MASTER,
            BotDifficulty.Preset.SURVIVAL_MASTER
    };

    private static final Material[] LADDER_ICONS = {
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.TOTEM_OF_UNDYING
    };

    private final PracticeService practiceService;

    public BotDifficultyGui(GuiSessionRegistry registry, SoundService sounds,
                            PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_DIFFICULTY, 6, true);
        this.practiceService = practiceService;
    }

    public void openFor(Player player, com.rumilance.practice.practice.PracticeSession session) {
        GuiSession gui = registry.open(player.getUniqueId(), type(), rows);
        gui.put("practice_id", session.practiceId());
        com.rumilance.practice.gui.menus.PracticeGuiOpen.open(this, player, gui);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.NETHER_STAR;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.bot-difficulty-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        var pracOpt = practiceService.session(player.getUniqueId());
        if (pracOpt.isEmpty()) {
            return;
        }
        BotDifficulty d = pracOpt.get().difficulty();

        // Row 1 — the seven-rung ladder (map order), row 2 slot 8 — CUSTOM.
        for (int i = 0; i < LADDER.length; i++) {
            BotDifficulty.Preset preset = LADDER[i];
            boolean active = d.preset() == preset;
            String key = preset.name().toLowerCase(Locale.ROOT);
            inventory.setItem(GuiSlots.slot(1, i + 1),
                    ItemBuilder.of(LADDER_ICONS[i])
                            .name(t(player, "gui.difficulty-" + key)
                                    .color(active ? UiTheme.SUCCESS : UiTheme.SECONDARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.difficulty-lore-" + key)),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.toggle-hint")))
                            .glintIf(active)
                            .action("preset:" + preset.name()).build());
        }
        inventory.setItem(GuiSlots.slot(2, 8),
                ItemBuilder.of(Material.NETHER_STAR)
                        .name(t(player, "gui.difficulty-custom")
                                .color(d.preset() == BotDifficulty.Preset.CUSTOM
                                        ? UiTheme.SUCCESS : UiTheme.MUTED))
                        .lore(UiTheme.line(line(player, "gui.difficulty-custom-lore")))
                        .glintIf(d.preset() == BotDifficulty.Preset.CUSTOM)
                        .action("decorate").build());

        // Rows 3-5 — the detail board (L +, R -, shift reset).
        inventory.setItem(GuiSlots.slot(3, 1), paramTile(player, Material.GOLDEN_APPLE,
                "gui.param-hp", String.valueOf((int) d.botMaxHp()), "param:hp"));
        inventory.setItem(GuiSlots.slot(3, 3), paramTile(player, Material.IRON_SWORD,
                "gui.param-damage", String.valueOf(d.attackDamage()), "param:damage"));
        inventory.setItem(GuiSlots.slot(3, 5), paramTile(player, Material.CLOCK,
                "gui.param-atkspeed", d.attackIntervalMs() + "ms", "param:atkspeed"));
        inventory.setItem(GuiSlots.slot(3, 7), paramTile(player, Material.STICK,
                "gui.param-reach", String.format(Locale.ROOT, "%.1f", d.reachBlocks()) + "b",
                "param:reach"));
        inventory.setItem(GuiSlots.slot(4, 1), paramTile(player, Material.FEATHER,
                "gui.param-speed", String.format(Locale.ROOT, "%.2f", d.moveSpeed()), "param:speed"));
        inventory.setItem(GuiSlots.slot(4, 3), paramTile(player, Material.GHAST_TEAR,
                "gui.param-regen", String.valueOf(d.regenPerSecond()), "param:regen"));
        inventory.setItem(GuiSlots.slot(4, 5), paramTile(player, Material.TNT,
                "gui.param-combo", d.comboCooldownMs() + "ms", "param:combo"));
        inventory.setItem(GuiSlots.slot(4, 7), paramTile(player, Material.BOW,
                "gui.param-aim", String.format(Locale.ROOT, "%.0f°", d.aimSpreadDegrees()),
                "param:aim"));
        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.of(d.shieldStun() ? UiTheme.TOGGLE_ON : UiTheme.TOGGLE_OFF)
                        .name(t(player, "gui.param-stun")
                                .color(d.shieldStun() ? UiTheme.SUCCESS : UiTheme.MUTED))
                        .lore(UiTheme.line(line(player, "gui.param-stun-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action("param:stun").build());
        inventory.setItem(GuiSlots.slot(5, 3), paramTile(player, Material.CHAINMAIL_CHESTPLATE,
                "gui.param-block", (int) Math.round(d.shieldReduction() * 100) + "%", "param:block"));
        inventory.setItem(GuiSlots.slot(5, 5), paramTile(player, Material.TOTEM_OF_UNDYING,
                "gui.param-goal", String.valueOf(d.totemGoal()), "param:goal"));

        paintNav(player, session, inventory);
    }

    private ItemStack paramTile(Player player, Material icon, String nameKey, String value,
                                String action) {
        return ItemBuilder.of(icon)
                .name(t(player, nameKey).color(UiTheme.PRIMARY))
                .lore(UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.param-value"), value),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.param-hint")))
                .action(action).build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        if (action == null) {
            return;
        }
        var pracOpt = practiceService.session(player.getUniqueId());
        if (pracOpt.isEmpty()) {
            return;
        }
        var prac = pracOpt.get();
        BotDifficulty d = prac.difficulty();
        boolean changed = false;

        if (action.startsWith("preset:")) {
            d.applyPreset(BotDifficulty.parsePreset(action.substring(7)));
            changed = true;
        } else if (action.startsWith("param:")) {
            boolean left = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
            boolean shift = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
            String p = action.substring(6);
            switch (p) {
                case "hp" -> d.setBotMaxHp(shift ? 50 : d.botMaxHp() + (left ? 10 : -10));
                case "damage" -> d.setAttackDamage(shift ? 4 : d.attackDamage() + (left ? 1 : -1));
                case "atkspeed" -> d.setAttackIntervalMs(shift ? 750
                        : d.attackIntervalMs() + (left ? -50 : 50));
                case "speed" -> d.setMoveSpeed(shift ? 0.22 : d.moveSpeed() + (left ? 0.02 : -0.02));
                case "regen" -> d.setRegenPerSecond(shift ? 3
                        : d.regenPerSecond() + (left ? 0.5 : -0.5));
                case "combo" -> d.setComboCooldownMs(shift ? 2400
                        : d.comboCooldownMs() + (left ? -200 : 200));
                case "stun" -> d.setShieldStun(!d.shieldStun());
                case "block" -> d.setShieldReduction(shift ? 0.5
                        : d.shieldReduction() + (left ? 0.05 : -0.05));
                case "goal" -> d.setTotemGoal(shift ? 3 : d.totemGoal() + (left ? 1 : -1));
                case "reach" -> d.setReachBlocks(shift ? 3.0
                        : d.reachBlocks() + (left ? 0.1 : -0.1));
                case "aim" -> d.setAimSpreadDegrees(shift ? 5
                        : d.aimSpreadDegrees() + (left ? -1 : 1));
                default -> { }
            }
            changed = true;
        }

        if (changed) {
            prac.setDifficulty(d);
            practiceService.saveDifficulty(player.getUniqueId(), d);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
        }
    }
}
