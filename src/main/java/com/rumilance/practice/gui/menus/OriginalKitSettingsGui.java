package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.OriginalKitSettings;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The big per-slot original-kit settings screen. Every switch maps to a hook the match runtime
 * actually enforces, so nothing here is cosmetic window dressing. Left/right click steps numeric
 * values and flips switches; hovering each tile explains exactly how the rule behaves in a fight.
 *
 * <p>Layout (6 rows of toggles, grouped by column):</p>
 * <ul>
 * <li>column 1 — movement/defence: fall damage, pearl, totem</li>
 *   <li>column 2 — recovery: natural regen, auto food, shield break</li>
 *   <li>column 3 — building: block place, block break, bed explosion</li>
 *   <li>column 4 — numbers/looks: max health, timeout, adventure mode, body size</li>
 * </ul>
 */
public final class OriginalKitSettingsGui extends AbstractGui {

    private final OriginalKitService service;

    public OriginalKitSettingsGui(GuiSessionRegistry registry, SoundService sounds,
                                  OriginalKitService service) {
        super(registry, sounds, GuiType.ORIGINAL_KIT_SETTINGS, 6, false);
        this.service = service;
    }

    public void open(Player player, int kitSlot) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("slot", kitSlot);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    private int slotOf(GuiSession session) {
        Integer slot = session.get("slot", Integer.class);
        return slot == null ? 22 : slot;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.COMPARATOR;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.original-settings-title", com.rumilance.practice.locale.MessageService.tags(
                "slot", String.valueOf(slotOf(session) + 1))).color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        int kitSlot = slotOf(session);
        OriginalKitSettings settings = draftOf(player, session, kitSlot);

        // Movement / defence
        inventory.setItem(GuiSlots.slot(1, 2), toggle(player, settings.fallDamage(),
                "fallDamage", "toggle:fallDamage"));
        inventory.setItem(GuiSlots.slot(2, 2), toggle(player, settings.pearl(),
                "pearl", "toggle:pearl"));
        inventory.setItem(GuiSlots.slot(3, 2), toggle(player, settings.totem(),
                "totem", "toggle:totem"));

        // Recovery
        inventory.setItem(GuiSlots.slot(1, 4), toggle(player, settings.naturalRegen(),
                "naturalRegen", "toggle:naturalRegen"));
        inventory.setItem(GuiSlots.slot(2, 4), toggle(player, settings.autoFood(),
                "autoFood", "toggle:autoFood"));
        inventory.setItem(GuiSlots.slot(3, 4), toggle(player, settings.swordShieldBreak(),
                "swordShieldBreak", "toggle:swordShieldBreak"));

        // Building / environment
        inventory.setItem(GuiSlots.slot(1, 6), toggle(player, settings.blockPlace(),
                "blockPlace", "toggle:blockPlace"));
        inventory.setItem(GuiSlots.slot(2, 6), toggle(player, settings.blockBreak(),
                "blockBreak", "toggle:blockBreak"));
        inventory.setItem(GuiSlots.slot(3, 6), toggle(player, settings.bedExplosion(),
                "bedExplosion", "toggle:bedExplosion"));

        // Numbers / mode / looks
        inventory.setItem(GuiSlots.slot(1, 8), healthToggle(player, settings));
        inventory.setItem(GuiSlots.slot(2, 8), timeoutToggle(player, settings));
        inventory.setItem(GuiSlots.slot(3, 8), toggle(player, settings.forceAdventure(),
                "forceAdventure", "toggle:forceAdventure"));
        inventory.setItem(GuiSlots.slot(4, 8), bodyScaleToggle(player, settings));

        // Reset to vanilla defaults
        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.of(Material.WATER_BUCKET)
                        .name(t(player, "gui.original-settings-reset").color(UiTheme.WARNING)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.original-settings-reset-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action("reset")
                        .build());
        // Save + back
        inventory.setItem(GuiSlots.slot(5, 5),
                ItemBuilder.of(UiTheme.CONFIRM)
                        .name(t(player, "gui.save").color(UiTheme.SUCCESS)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action("save")
                        .build());
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    private ItemStack toggle(Player player, boolean on, String settingKey, String action) {
        // The on/off colour is the affordance, matching KitItemRulesGui's toggle style.
        return ItemBuilder.of(on ? Material.LIME_DYE : Material.RED_DYE)
                .name(pair(player, settingKey,
                        line(player, on ? "gui.original-settings-on" : "gui.original-settings-off")))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "gui.original-settings-" + settingKey + "-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.original-settings-toggle-hint")))
                .glintIf(on)
                .action(action)
                .build();
    }

    private Component pair(Player player, String settingKey, String state) {
        return Component.text(line(player, "gui.original-settings-" + settingKey) + ": ", UiTheme.MUTED)
                .append(Component.text(state, UiTheme.VALUE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack healthToggle(Player player, OriginalKitSettings settings) {
        return ItemBuilder.of(Material.APPLE)
                .name(Component.text(line(player, "gui.original-settings-maxHealth") + ": "
                        + (int) settings.maxHealth() + " HP", UiTheme.VALUE)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "gui.original-settings-maxHealth-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.original-settings-numeric-hint")))
                .action("health:+")
                .build();
    }

    private ItemStack timeoutToggle(Player player, OriginalKitSettings settings) {
        return ItemBuilder.of(Material.CLOCK)
                .name(Component.text(line(player, "gui.original-settings-timeout") + ": "
                        + settings.timeoutSeconds() + "s", UiTheme.VALUE)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "gui.original-settings-timeout-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.original-settings-numeric-hint")))
                .action("timeout:+")
                .build();
    }

    private ItemStack bodyScaleToggle(Player player, OriginalKitSettings settings) {
        String scaleLabel = String.format(java.util.Locale.ROOT, "%.2f", settings.bodyScale());
        return ItemBuilder.of(Material.SLIME_BALL)
                .name(Component.text(line(player, "gui.original-settings-bodyScale") + ": "
                        + scaleLabel + "x", UiTheme.VALUE)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, "gui.original-settings-bodyScale-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.original-settings-numeric-hint")))
                .action("scale:+")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        int kitSlot = slotOf(session);
        switch (action) {
            case "back", "close" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "reset" -> {
                session.put("draft", OriginalKitSettings.defaults());
                sounds.play(player, "select");
                render(player, session, inventory);
            }
            case "save" -> {
                // Single write: every change above only touched the in-memory draft, so this
                // is the ONLY persist point — no per-click DB spamming.
                service.saveSettings(player, kitSlot, draftOf(player, session, kitSlot));
                sounds.play(player, "select");
                player.closeInventory();
            }
            default -> {
                if (action.startsWith("toggle:")) {
                    String key = action.substring("toggle:".length());
                    OriginalKitSettings current = draftOf(player, session, kitSlot);
                    session.put("draft", current.with(key, !currentBoolean(current, key)));
                    sounds.play(player, "select");
                    render(player, session, inventory);
                }
                // Numeric tiles are handled by the ClickType-aware overload below.
            }
        }
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType clickType) {
        // Numeric tiles use click direction: left = increase, right = decrease, shift = +10/-10.
        if (action != null && action.startsWith("health:")) {
            int kitSlot = slotOf(session);
            OriginalKitSettings current = draftOf(player, session, kitSlot);
            double delta = delta(clickType, 2.0d);
            session.put("draft", current.withMaxHealth(current.maxHealth() + delta));
            sounds.play(player, "select");
            render(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("timeout:")) {
            int kitSlot = slotOf(session);
            OriginalKitSettings current = draftOf(player, session, kitSlot);
            int delta = (int) delta(clickType, 15.0d);
            session.put("draft", current.withTimeoutSeconds(current.timeoutSeconds() + delta));
            sounds.play(player, "select");
            render(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("scale:")) {
            int kitSlot = slotOf(session);
            OriginalKitSettings current = draftOf(player, session, kitSlot);
            double delta = delta(clickType, 0.1d);
            session.put("draft", current.withBodyScale(current.bodyScale() + delta));
            sounds.play(player, "select");
            render(player, session, inventory);
            return;
        }
        handleClick(player, session, inventory, slot, action);
    }

    /**
     * The in-memory settings draft for this session. On first open it starts from the slot's
     * persisted settings, and every edit only replaces this draft — the database is written
     * once, on SAVE. Escaping or closing discards the draft (nothing was persisted).
     */
    private OriginalKitSettings draftOf(Player player, GuiSession session, int kitSlot) {
        OriginalKitSettings draft = session.get("draft", OriginalKitSettings.class);
        if (draft == null) {
            draft = service.settingsOf(player.getUniqueId(), kitSlot);
            session.put("draft", draft);
        }
        return draft;
    }

    private static boolean currentBoolean(OriginalKitSettings settings, String key) {
        return switch (key) {
            case "fallDamage" -> settings.fallDamage();
            case "totem" -> settings.totem();
            case "pearl" -> settings.pearl();
            case "naturalRegen" -> settings.naturalRegen();
            case "autoFood" -> settings.autoFood();
            case "swordShieldBreak" -> settings.swordShieldBreak();
            case "blockPlace" -> settings.blockPlace();
            case "blockBreak" -> settings.blockBreak();
            case "bedExplosion" -> settings.bedExplosion();
            case "forceAdventure" -> settings.forceAdventure();
            default -> false;
        };
    }

    private static double delta(ClickType clickType, double step) {
        if (clickType == ClickType.SHIFT_LEFT) {
            return step * 5.0d;
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            return -step * 5.0d;
        }
        if (clickType == ClickType.RIGHT) {
            return -step;
        }
        return step;
    }
}
