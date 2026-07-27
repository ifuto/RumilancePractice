package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.PracticeGuiHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Opens an {@link AbstractGui} using an already-prepared {@link GuiSession}.
 */
public final class PracticeGuiOpen {

    private PracticeGuiOpen() {
    }

    public static void open(AbstractGui gui, Player player, GuiSession session) {
        PracticeGuiHolder holder = new PracticeGuiHolder(session.sessionId(), session.type(), session.rows());
        Inventory inventory = Bukkit.createInventory(holder, session.rows() * 9, guiTitle(gui, player, session));
        holder.bind(inventory);
        if (session.type() == GuiType.RANKED_QUEUE || session.type() == GuiType.UNRANKED_QUEUE
                || session.type() == GuiType.KIT_SELECT || session.type() == GuiType.MAP_SELECT
                || session.type() == GuiType.DUEL_REQUEST || session.type() == GuiType.SETTINGS
                || session.type() == GuiType.STATS_KIT || session.type() == GuiType.PLAYERS
                || session.type() == GuiType.SPECTATE_LIST || session.type() == GuiType.FFA_LIST
                || session.type() == GuiType.ARROW_EFFECT || session.type() == GuiType.EDIT_KIT
                || session.type() == GuiType.ORIGINAL_KIT) {
            GuiDecorator.decorateBorder(inventory, session.ranked() || session.type() == GuiType.RANKED_QUEUE
                    || session.type() == GuiType.SETTINGS || session.type() == GuiType.STATS_KIT);
        }
        gui.renderPublic(player, session, inventory);
        player.openInventory(inventory);
    }

    private static Component guiTitle(AbstractGui gui, Player player, GuiSession session) {
        return gui.titlePublic(player, session);
    }
}
