package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.EnchantmentRules;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enchant picker for weapons/armor added to an original kit.
 * Flow: enchant list -> level list -> applied; clicking an applied book removes it.
 * Vanilla rules enforced via {@link EnchantmentRules}.
 */
public final class EnchantGui extends AbstractGui {

    private final OriginalKitService service;
    private final OriginalKitEditGui editGui;

    public EnchantGui(GuiSessionRegistry registry, SoundService sounds,
                      OriginalKitService service, OriginalKitEditGui editGui) {
        super(registry, sounds, GuiType.ENCHANT, 6, false);
        this.service = service;
        this.editGui = editGui;
    }

    public void open(Player player, ItemStack base) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("base", base.clone());
        session.put("applied", new HashMap<String, Integer>());
        session.put("view", "list");
        session.put("pick", null);
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        if (ctx != null) {
            ctx.suppressRestore = true;
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.enchant-title").color(UiTheme.PRIMARY);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> applied(GuiSession session) {
        Map<String, Integer> map = session.get("applied", Map.class);
        return map == null ? new HashMap<>() : map;
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        ItemStack base = session.get("base", ItemStack.class);
        if (base == null) {
            return;
        }
        Map<String, Integer> applied = applied(session);
        String view = session.get("view", String.class);
        MenuScaffold.chrome(inventory);
        if ("levels".equals(view)) {
            String pick = session.get("pick", String.class);
            Enchantment ench = pick == null ? null : Registry.ENCHANTMENT.get(NamespacedKey.minecraft(pick));
            int max = ench == null ? 1 : ench.getMaxLevel();
            int i = 0;
            for (int level = 1; level <= max && i < 45; level++) {
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta meta = book.getItemMeta();
                meta.displayName(t(player, "gui.enchant-level",
                        com.rumilance.practice.locale.MessageService.tags("level", String.valueOf(level)))
                        .color(UiTheme.WARNING)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text(EnchantmentRules.label(ench), UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false)));
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "level:" + level);
                book.setItemMeta(meta);
                inventory.setItem(i++, book);
            }
        } else {
            List<Enchantment> list = EnchantmentRules.applicable(base);
            for (int i = 0; i < list.size() && i < 45; i++) {
                Enchantment ench = list.get(i);
                Integer appliedLevel = applied.get(ench.getKey().getKey());
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta meta = book.getItemMeta();
                meta.displayName(Component.text(EnchantmentRules.label(ench), UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        appliedLevel == null
                                ? t(player, "gui.enchant-pick").color(UiTheme.MUTED)
                                : t(player, "gui.enchant-applied",
                                        com.rumilance.practice.locale.MessageService.tags(
                                                "level", String.valueOf(appliedLevel))).color(UiTheme.SUCCESS)));
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                        "ench:" + ench.getKey().getKey());
                book.setItemMeta(meta);
                inventory.setItem(i, book);
            }
        }
        inventory.setItem(45, ItemBuilder.action(UiTheme.BACK,
                t(player, "menu.back"), "back"));
        inventory.setItem(49, ItemBuilder.action(UiTheme.CONFIRM,
                t(player, "gui.continue"), "continue"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        ItemStack base = session.get("base", ItemStack.class);
        Map<String, Integer> applied = applied(session);
        switch (action) {
            case "back" -> reopenEdit(player);
            case "continue" -> {
                ItemStack finalItem = buildFinal(base, applied);
                OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
                if (ctx == null) {
                    return;
                }
                boolean placed = OriginalKitService.addToLayout(ctx, finalItem);
                if (!placed) {
                    player.sendMessage(t(player, "gui.kit-full"));
                    return;
                }
                sounds.play(player, "select");
                reopenEdit(player);
            }
            default -> {
                if (action != null && action.startsWith("ench:")) {
                    String key = action.substring(5);
                    if (applied.containsKey(key)) {
                        applied.remove(key);
                        sounds.play(player, "delete");
                    } else {
                        session.put("pick", key);
                        session.put("view", "levels");
                    }
                    render(player, session, inventory);
                } else if (action != null && action.startsWith("level:")) {
                    int level = Integer.parseInt(action.substring(6));
                    String key = session.get("pick", String.class);
                    Enchantment ench = key == null ? null : Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
                    Map<Enchantment, Integer> existing = new HashMap<>();
                    for (Map.Entry<String, Integer> e : applied.entrySet()) {
                        Enchantment other = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey()));
                        if (other != null) {
                            existing.put(other, e.getValue());
                        }
                    }
                    if (EnchantmentRules.canApply(base, ench, level, existing)) {
                        applied.put(key, level);
                        sounds.play(player, "select");
                    } else {
                        sounds.play(player, "error");
                    }
                    session.put("view", "list");
                    render(player, session, inventory);
                }
            }
        }
    }

    private ItemStack buildFinal(ItemStack base, Map<String, Integer> applied) {
        ItemStack out = base.clone();
        ItemMeta meta = out.getItemMeta();
        for (Map.Entry<String, Integer> e : applied.entrySet()) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey()));
            if (ench != null) {
                meta.addEnchant(ench, e.getValue(), true);
            }
        }
        out.setItemMeta(meta);
        return out;
    }

    private void reopenEdit(Player player) {
        OriginalKitService.EditContext ctx = service.context(player.getUniqueId());
        service.markNavigating(player.getUniqueId());
        player.closeInventory();
        if (ctx != null) {
            editGui.open(player, ctx.slot, ctx.layout);
        }
    }
}
