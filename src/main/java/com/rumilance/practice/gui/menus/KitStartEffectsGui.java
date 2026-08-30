package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitStartEffect;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.SplashPotionDurations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Admin GUI to configure kit start-of-fight potion effects (applied in {@code beginFight}).
 */
public final class KitStartEffectsGui extends AbstractGui {

    /** Selectable PvP-oriented buffs: effect key + max 0-based amplifier. */
    private static final List<Selectable> SELECTABLES = List.of(
            new Selectable("speed", "Speed", Material.SUGAR, 1),
            new Selectable("strength", "Strength", Material.BLAZE_POWDER, 1),
            new Selectable("regeneration", "Regeneration", Material.GHAST_TEAR, 1),
            new Selectable("fire_resistance", "Fire Resistance", Material.MAGMA_CREAM, 0),
            new Selectable("jump_boost", "Jump Boost", Material.RABBIT_FOOT, 1),
            new Selectable("resistance", "Resistance", Material.IRON_INGOT, 2),
            new Selectable("absorption", "Absorption", Material.GOLDEN_APPLE, 2),
            new Selectable("haste", "Haste", Material.GOLDEN_PICKAXE, 2),
            new Selectable("night_vision", "Night Vision", Material.GOLDEN_CARROT, 0),
            new Selectable("water_breathing", "Water Breathing", Material.PUFFERFISH, 0),
            new Selectable("invisibility", "Invisibility", Material.GLASS_BOTTLE, 0),
            new Selectable("slow_falling", "Slow Falling", Material.PHANTOM_MEMBRANE, 0)
    );

    private final KitService kitService;
    private BiConsumer<Player, String> returnTo = (p, kit) -> { };

    public KitStartEffectsGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_START_EFFECTS, 6, false);
        this.kitService = kitService;
    }

    public void setReturnTo(BiConsumer<Player, String> returnTo) {
        this.returnTo = returnTo == null ? (p, kit) -> { } : returnTo;
    }

    public void open(Player player, String kitId) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit() == null ? "" : session.selectedKit();
        return t(player, "gui.start-effects-title",
                com.rumilance.practice.locale.MessageService.tags("kit", kit)).color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        KitDefinition kit = kitOf(session);
        if (kit == null) {
            inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                    t(player, "menu.back"), "back"));
            return;
        }

        // Palette of addable effects (row 0–2).
        int slot = 0;
        for (Selectable sel : SELECTABLES) {
            if (slot >= 27) {
                break;
            }
            inventory.setItem(slot++, paletteIcon(player, sel, kit));
        }

        // Current configured list (row 3–4) with remove.
        List<KitStartEffect> current = kit.startEffects();
        int curSlot = 27;
        for (int i = 0; i < current.size() && curSlot < 45; i++) {
            KitStartEffect effect = current.get(i);
            inventory.setItem(curSlot++, currentIcon(player, effect, i));
        }
        if (current.isEmpty()) {
            inventory.setItem(31, GuiDecorator.decorative(Material.GRAY_STAINED_GLASS_PANE, line(player, "gui.unset")));
        }

        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                t(player, "menu.back"), "back"));
    }

    private ItemStack paletteIcon(Player player, Selectable sel, KitDefinition kit) {
        int existingAmp = findAmplifier(kit, sel.key());
        ItemStack stack = potionIcon(sel.key(), sel.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(t(player, "gui.effect-" + sel.key()).color(UiTheme.WARNING));
        List<Component> lore = new ArrayList<>();
        if (existingAmp >= 0) {
            lore.add(t(player, "gui.start-effects-set",
                    com.rumilance.practice.locale.MessageService.tags("level", String.valueOf(existingAmp + 1)))
                    .color(UiTheme.SUCCESS));
            lore.add(t(player, "gui.start-effects-toggle").color(UiTheme.MUTED));
        } else {
            lore.add(t(player, "gui.start-effects-add").color(UiTheme.MUTED));
        }
        lore.add(t(player, "gui.start-effects-max",
                com.rumilance.practice.locale.MessageService.tags("level", String.valueOf(sel.maxAmplifier() + 1)))
                .color(UiTheme.MUTED));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                "toggle:" + sel.key());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack currentIcon(Player player, KitStartEffect effect, int index) {
        Selectable sel = selectable(effect.potionEffectKey());
        Material mat = sel == null ? Material.POTION : sel.material();
        String label = sel == null ? effect.potionEffectKey() : line(player, "gui.effect-" + sel.key());
        ItemStack stack = potionIcon(effect.potionEffectKey(), mat);
        ItemMeta meta = stack.getItemMeta();
        int ticks = SplashPotionDurations.ticks(effect.potionEffectKey(), effect.amplifier());
        meta.displayName(Component.text(label + " Lv" + (effect.amplifier() + 1), UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                t(player, "gui.start-effects-duration",
                        com.rumilance.practice.locale.MessageService.tags("secs", String.valueOf(ticks / 20)))
                        .color(UiTheme.MUTED),
                t(player, "gui.start-effects-remove").color(UiTheme.DANGER)
        ));
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                "remove:" + index);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack potionIcon(String effectKey, Material fallback) {
        ItemStack stack = new ItemStack(Material.POTION);
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            try {
                PotionType base = switch (effectKey) {
                    case "speed" -> PotionType.SWIFTNESS;
                    case "strength" -> PotionType.STRENGTH;
                    case "regeneration" -> PotionType.REGENERATION;
                    case "fire_resistance" -> PotionType.FIRE_RESISTANCE;
                    case "jump_boost" -> PotionType.LEAPING;
                    case "night_vision" -> PotionType.NIGHT_VISION;
                    case "water_breathing" -> PotionType.WATER_BREATHING;
                    case "invisibility" -> PotionType.INVISIBILITY;
                    case "slow_falling" -> PotionType.SLOW_FALLING;
                    default -> PotionType.WATER;
                };
                meta.setBasePotionType(base);
                stack.setItemMeta(meta);
                return stack;
            } catch (Exception ignored) {
                // fall through to material icon
            }
        }
        return new ItemStack(fallback == null ? Material.POTION : fallback);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("back".equals(action)) {
            String kitId = session.selectedKit();
            sounds.play(player, "gui-back");
            returnTo.accept(player, kitId);
            return;
        }
        KitDefinition kit = kitOf(session);
        if (kit == null) {
            return;
        }
        if (action.startsWith("remove:")) {
            int index = Integer.parseInt(action.substring("remove:".length()));
            List<KitStartEffect> next = new ArrayList<>(kit.startEffects());
            if (index >= 0 && index < next.size()) {
                next.remove(index);
                kitService.save(kit.toBuilder().startEffects(next).build());
                sounds.play(player, "select");
                render(player, session, inventory);
            }
            return;
        }
        if (action.startsWith("toggle:")) {
            String key = action.substring("toggle:".length()).toLowerCase(Locale.ROOT);
            Selectable sel = selectable(key);
            if (sel == null) {
                return;
            }
            List<KitStartEffect> next = new ArrayList<>(kit.startEffects());
            int idx = indexOf(next, key);
            if (idx < 0) {
                next.add(new KitStartEffect(key, 0));
            } else {
                KitStartEffect cur = next.get(idx);
                int amp = cur.amplifier() + 1;
                if (amp > sel.maxAmplifier()) {
                    next.remove(idx);
                } else {
                    next.set(idx, new KitStartEffect(key, amp));
                }
            }
            kitService.save(kit.toBuilder().startEffects(next).build());
            sounds.play(player, "select");
            render(player, session, inventory);
        }
    }

    private KitDefinition kitOf(GuiSession session) {
        return session.selectedKit() == null ? null : kitService.get(session.selectedKit()).orElse(null);
    }

    private static int findAmplifier(KitDefinition kit, String key) {
        for (KitStartEffect e : kit.startEffects()) {
            if (e.potionEffectKey().equalsIgnoreCase(key)) {
                return e.amplifier();
            }
        }
        return -1;
    }

    private static int indexOf(List<KitStartEffect> list, String key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).potionEffectKey().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    private static Selectable selectable(String key) {
        for (Selectable sel : SELECTABLES) {
            if (sel.key().equalsIgnoreCase(key)) {
                return sel;
            }
        }
        return null;
    }

    /** Resolves a registry effect (unused in GUI but kept for callers / validation). */
    public static PotionEffectType resolveType(String key) {
        String normalized = SplashPotionDurations.normalizeKey(key);
        if (normalized.isEmpty()) {
            return null;
        }
        return Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(normalized));
    }

    private record Selectable(String key, String display, Material material, int maxAmplifier) {
    }
}
