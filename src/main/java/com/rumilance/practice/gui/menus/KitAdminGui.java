package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin kit management GUI ({@code /kit} with no args). Lists every kit; clicking one opens a
 * toggle panel for its settings (enable / adventure / ranked / arena-terrain / regen / food /
 * block-place / block-break / pearl / totem / shield-break). Changes are saved immediately.
 *
 * <p>All labels are localised via {@link MessageService} under the {@code admin-gui.*} keys
 * (Japanese for {@code ja_jp}, English fallback otherwise).</p>
 */
public final class KitAdminGui extends AbstractGui {

    private static final ArenaTerrain[] TERRAIN_CYCLE = ArenaTerrain.values();
    /**
     * The admin GUI is always shown in Japanese, regardless of the admin's client locale
     * (this server is Japanese-operated; admin tooling is intentionally ja-only).
     */
    private static final String FORCED_LOCALE = "ja_jp";

    private final KitService kitService;
    private final MessageService messageService;

    public KitAdminGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService, MessageService messageService) {
        super(registry, sounds, GuiType.KIT_ADMIN, 6, false);
        this.kitService = kitService;
        this.messageService = messageService;
    }

    /** Localised raw label string from {@code admin-gui.<key>}. */
    private String t(String locale, String key) {
        return messageService.localeService().rawMessage(locale, "admin-gui." + key);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.put("view", "list");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return messageService.render(FORCED_LOCALE, "admin-gui.title")
                .decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        inventory.clear();
        String locale = FORCED_LOCALE;
        String view = session.get("view", String.class);
        if (view == null) {
            view = "list";
        }
        if ("config".equals(view) && session.selectedKit() != null
                && kitService.get(session.selectedKit()).isPresent()) {
            renderConfig(inventory, session, locale);
        } else {
            renderList(inventory, locale);
        }
    }

    private void renderList(Inventory inventory, String locale) {
        int slot = 10;
        for (KitDefinition kit : kitService.all()) {
            if (slot == 17) {
                slot = 19;
            } else if (slot == 26) {
                slot = 28;
            } else if (slot == 35) {
                slot = 37;
            }
            if (slot >= 44) {
                break;
            }
            inventory.setItem(slot++, kitIcon(kit, locale));
        }
    }

    private ItemStack kitIcon(KitDefinition kit, String locale) {
        Material material = Material.matchMaterial(kit.icon());
        if (material == null || material.isAir()) {
            material = Material.DIAMOND_SWORD;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(kit.name(), kit.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(stateLine(t(locale, "enabled"), kit.enabled(), locale));
        lore.add(stateLine(t(locale, "adventure"), kit.forceAdventure(), locale));
        lore.add(stateLine(t(locale, "ranked"), kit.ranked(), locale));
        lore.add(Component.text(t(locale, "terrain") + ": " + kit.arenaTerrain().name(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "select:" + kit.name());
        stack.setItemMeta(meta);
        return stack;
    }

    private void renderConfig(Inventory inventory, GuiSession session, String locale) {
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            renderList(inventory, locale);
            return;
        }
        inventory.setItem(GuiSlots.slot(0, 4), header(kit, locale));
        inventory.setItem(GuiSlots.slot(1, 1), toggle(t(locale, "enabled"), kit.enabled(), "toggle:enabled",
                kit.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, locale));
        inventory.setItem(GuiSlots.slot(1, 3), toggle(t(locale, "adventure"), kit.forceAdventure(), "toggle:adventure",
                kit.forceAdventure() ? Material.LIME_DYE : Material.GRAY_DYE, locale));
        inventory.setItem(GuiSlots.slot(1, 5), toggle(t(locale, "ranked"), kit.ranked(), "toggle:ranked",
                kit.ranked() ? Material.LIME_DYE : Material.GRAY_DYE, locale));
        inventory.setItem(GuiSlots.slot(1, 7), GuiDecorator.button(Material.GRASS_BLOCK,
                Component.text(t(locale, "terrain") + ": " + kit.arenaTerrain().name(), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false), "cycle:terrain"));

        inventory.setItem(GuiSlots.slot(2, 1), toggle(t(locale, "health-regen"), kit.naturalHealthRegen(), "toggle:autoregen",
                Material.GOLDEN_APPLE, locale));
        inventory.setItem(GuiSlots.slot(2, 3), toggle(t(locale, "auto-food"), kit.autoFood(), "toggle:autofood",
                Material.COOKED_BEEF, locale));
        inventory.setItem(GuiSlots.slot(2, 5), toggle(t(locale, "block-place"), kit.blockPlace(), "toggle:blockplace",
                Material.BRICKS, locale));
        inventory.setItem(GuiSlots.slot(2, 7), toggle(t(locale, "block-break"), kit.blockBreak(), "toggle:blockbreak",
                Material.IRON_PICKAXE, locale));

        inventory.setItem(GuiSlots.slot(3, 1), toggle(t(locale, "ender-pearl"), kit.pearl(), "toggle:pearl",
                Material.ENDER_PEARL, locale));
        inventory.setItem(GuiSlots.slot(3, 3), toggle(t(locale, "totem"), kit.totem(), "toggle:totem",
                Material.TOTEM_OF_UNDYING, locale));
        inventory.setItem(GuiSlots.slot(3, 5), toggle(t(locale, "shield-break"), kit.swordShieldBreak(), "toggle:swordshieldbreak",
                Material.SHIELD, locale));
        inventory.setItem(GuiSlots.slot(3, 7), GuiDecorator.button(Material.CLOCK,
                Component.text(t(locale, "timeout") + ": " + kit.timeoutSeconds() + "s", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false), "noop"));

        inventory.setItem(GuiSlots.slot(5, 4), GuiDecorator.button(Material.ARROW,
                Component.text(t(locale, "back"), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false), "back"));
    }

    private ItemStack header(KitDefinition kit, String locale) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(kit.name(), NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(t(locale, "click-hint"), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack toggle(String label, boolean state, String action, Material material, String locale) {
        return GuiDecorator.button(material,
                Component.text(label + ": ", NamedTextColor.GRAY)
                        .append(Component.text(state ? t(locale, "on") : t(locale, "off"),
                                state ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .decoration(TextDecoration.ITALIC, false),
                action);
    }

    private Component stateLine(String label, boolean state, String locale) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(state ? t(locale, "on") : t(locale, "off"),
                        state ? NamedTextColor.GREEN : NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if (action.equals("back")) {
            session.put("view", "list");
            session.setSelectedKit(null);
            sounds.play(player, "gui-click");
            render(player, session, inventory);
            return;
        }
        if (action.startsWith("select:")) {
            String kitName = action.substring("select:".length());
            session.setSelectedKit(kitName);
            session.put("view", "config");
            sounds.play(player, "kit-select");
            render(player, session, inventory);
            return;
        }
        if (action.equals("noop")) {
            return;
        }
        KitDefinition current = session.selectedKit() == null ? null
                : kitService.get(session.selectedKit()).orElse(null);
        if (current == null) {
            return;
        }
        KitDefinition updated = applyConfigChange(current, action);
        if (updated != null) {
            kitService.save(updated);
            sounds.play(player, updated.equals(current) ? "gui-click" : "select");
            render(player, session, inventory);
        }
    }

    private KitDefinition applyConfigChange(KitDefinition kit, String action) {
        KitDefinition.Builder b = kit.toBuilder();
        return switch (action) {
            case "toggle:enabled" -> b.enabled(!kit.enabled()).build();
            case "toggle:adventure" -> b.forceAdventure(!kit.forceAdventure()).build();
            case "toggle:ranked" -> b.ranked(!kit.ranked()).build();
            case "toggle:autoregen" -> b.naturalHealthRegen(!kit.naturalHealthRegen()).build();
            case "toggle:autofood" -> b.autoFood(!kit.autoFood()).build();
            case "toggle:blockplace" -> b.blockPlace(!kit.blockPlace()).build();
            case "toggle:blockbreak" -> b.blockBreak(!kit.blockBreak()).build();
            case "toggle:pearl" -> b.pearl(!kit.pearl()).build();
            case "toggle:totem" -> b.totem(!kit.totem()).build();
            case "toggle:swordshieldbreak" -> b.swordShieldBreak(!kit.swordShieldBreak()).build();
            case "cycle:terrain" -> b.arenaTerrain(nextTerrain(kit.arenaTerrain())).build();
            default -> null;
        };
    }

    private ArenaTerrain nextTerrain(ArenaTerrain current) {
        for (int i = 0; i < TERRAIN_CYCLE.length; i++) {
            if (TERRAIN_CYCLE[i] == current) {
                return TERRAIN_CYCLE[(i + 1) % TERRAIN_CYCLE.length];
            }
        }
        return ArenaTerrain.ANY;
    }
}
