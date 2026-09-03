package com.rumilance.practice.gui;

import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link ItemStack}s used inside menus. Centralises the repetitive
 * display-name / lore / item-flags / PDC boilerplate and always removes italic from names
 * and lore so menus match the plugin's house style. Use {@link #background()} for filler
 * and {@link #action(Material, Component, String)} for clickable buttons.
 */
public final class ItemBuilder {

    private final ItemStack stack;
    private ItemMeta meta;
    private final List<Component> lore = new ArrayList<>();
    private boolean hideAttributes = true;

    private ItemBuilder(Material material, int amount) {
        this.stack = new ItemStack(material, Math.max(1, amount));
        this.meta = stack.getItemMeta();
    }

    private ItemBuilder(ItemStack existing) {
        this.stack = existing.clone();
        this.meta = stack.getItemMeta();
    }

    /** Starts a plain item with no name or lore. */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    /** Wraps an existing stack (e.g. a player's shield) for GUI display. */
    public static ItemBuilder of(ItemStack existing) {
        return new ItemBuilder(existing);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    /** A completely empty background filler (no tooltip, action = decorate). */
    public static ItemStack background() {
        return hiddenFill(UiTheme.BACKGROUND);
    }

    /** Chrome pane with the tooltip fully suppressed. */
    public static ItemStack hiddenFill() {
        return hiddenFill(UiTheme.BACKGROUND);
    }

    public static ItemStack hiddenFill(Material material) {
        ItemStack stack = filler(material);
        com.rumilance.practice.item.ItemTooltips.hideCompletely(stack);
        return stack;
    }

    /** A lighter panel filler. */
    public static ItemStack panel() {
        return filler(UiTheme.PANEL);
    }

    /** An accent highlight filler. */
    public static ItemStack accent() {
        return filler(UiTheme.ACCENT);
    }

    private static ItemStack filler(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "decorate");
        stack.setItemMeta(meta);
        return stack;
    }

    /** A clickable button with a plain (non-MiniMessage) display name and action key. */
    public static ItemStack action(Material material, Component name, String action) {
        return new ItemBuilder(material, 1).name(name).action(action).build();
    }

    /** Sets the display name from a literal component (italic already removed). */
    public ItemBuilder name(Component component) {
        meta.displayName(component.decoration(TextDecoration.ITALIC, false));
        return this;
    }

    /** Sets the display name from a plain string (no MiniMessage parsing). */
    public ItemBuilder name(String text) {
        return name(Component.text(text));
    }

    /** Sets the display name from a MiniMessage string (e.g. {@code "<aqua>NoDebuff</aqua>"}). */
    public ItemBuilder nameMini(String miniMessage) {
        return name(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, amount));
        return this;
    }

    /** Appends one lore line. */
    public ItemBuilder lore(Component line) {
        lore.add(line.decoration(TextDecoration.ITALIC, false));
        return this;
    }

    /** Appends a plain, muted lore line. */
    public ItemBuilder lore(String text) {
        return lore(Component.text(text, UiTheme.MUTED));
    }

    /** Appends several lore lines at once. */
    public ItemBuilder lore(Component... lines) {
        for (Component line : lines) {
            lore(line);
        }
        return this;
    }

    /** Appends several plain lore lines at once. */
    public ItemBuilder lore(String... lines) {
        for (String line : lines) {
            lore(line);
        }
        return this;
    }

    /** Adds a horizontal divider and the given labelled-value line. */
    public ItemBuilder labeled(String label, String value) {
        if (lore.isEmpty()) {
            lore(UiTheme.divider());
        }
        return lore(UiTheme.labelValue(label, value));
    }

    /** Adds the standard "▶ <hint>" call-to-action line. */
    public ItemBuilder hint(String text) {
        return lore(UiTheme.hint(text));
    }

    public ItemBuilder clearLore() {
        lore.clear();
        return this;
    }

    /** Tags the item with a GUI action key (used by GuiListener to route clicks). */
    public ItemBuilder action(String action) {
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, action);
        return this;
    }

    /** Stores an arbitrary string value under a custom PDC key (e.g. kit/arena id). */
    public ItemBuilder tag(org.bukkit.NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    /** Adds the enchantment glint shimmer without a real enchantment (1.20.5+ Paper API). */
    public ItemBuilder glint(boolean glint) {
        meta.setEnchantmentGlintOverride(glint);
        return this;
    }

    /** Allows setting pre-built meta (e.g. a skull with owning player) directly. */
    public ItemBuilder applyMeta(org.bukkit.inventory.meta.ItemMeta prebuilt) {
        if (prebuilt != null) {
            stack.setItemMeta(prebuilt);
            this.meta = prebuilt;
        }
        return this;
    }

    /** Builds the item meta without creating the final stack, useful for skull editing. */
    public org.bukkit.inventory.meta.ItemMeta buildMeta() {
        stack.setItemMeta(meta);
        return meta;
    }

    /** Convenience: adds the glint shimmer when {@code active} is true. */
    public ItemBuilder glintIf(boolean active) {
        return glint(active);
    }

    public ItemBuilder hideAttributes(boolean hide) {
        this.hideAttributes = hide;
        return this;
    }

    /** If the material is a player head, sets its owning player. */
    public ItemBuilder skullOwner(OfflinePlayer player) {
        if (meta instanceof SkullMeta skullMeta && player != null) {
            skullMeta.setOwningPlayer(player);
        }
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) {
            meta.lore(new ArrayList<>(lore));
        }
        if (hideAttributes) {
            meta.addItemFlags(ItemFlag.values());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    // ---- Convenience for icons built from a kit/arena definition --------

    /** Resolves a material name safely, falling back to {@link Material#DIAMOND_SWORD}. */
    public static Material materialOr(String name, Material fallback) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null ? fallback : material;
    }

    /** Builds a simple head for the given player with a name and optional lore. */
    public static ItemStack head(OfflinePlayer owner, Component name, Component... extraLore) {
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD, 1)
                .name(name)
                .skullOwner(owner);
        if (extraLore != null) {
            builder.lore(extraLore);
        }
        return builder.build();
    }
}
