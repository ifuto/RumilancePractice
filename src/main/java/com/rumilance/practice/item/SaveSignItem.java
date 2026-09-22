package com.rumilance.practice.item;

import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The special sign used as the "SAVE" control in the original-kit room. Obtained via
 * {@code /giveitem save-sign} and placed by an admin inside the kit room. Right-clicking the
 * placed sign while editing an original kit triggers the save (with the strict inventory
 * validation). Recognised by a PDC marker, never by display name.
 */
public final class SaveSignItem {

    public static final String FUNCTION = "ekit-save";

    /** The wall-sign / standing-sign materials admin may place as a save point. */
    public static final List<Material> SIGN_MATERIALS = List.of(
            Material.OAK_SIGN, Material.OAK_WALL_SIGN
    );

    private SaveSignItem() {
    }

    public static ItemStack create() {
        return create(1);
    }

    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.OAK_SIGN, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Save Kit Sign", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Place in the original-kit room", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("then right-click it to save your kit.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        meta.getPersistentDataContainer().set(ItemKeys.functionType(), PersistentDataType.STRING, FUNCTION);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSaveButton(ItemStack stack) {
        if (stack == null || !SIGN_MATERIALS.contains(stack.getType()) || !stack.hasItemMeta()) {
            return false;
        }
        String fn = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.functionType(), PersistentDataType.STRING);
        return FUNCTION.equals(fn);
    }
}
