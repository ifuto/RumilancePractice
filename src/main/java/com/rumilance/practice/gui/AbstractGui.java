package com.rumilance.practice.gui;

import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all practice GUIs.
 */
public abstract class AbstractGui {

    protected final GuiSessionRegistry registry;
    protected final SoundService sounds;
    protected final GuiType type;
    protected final int rows;
    protected final boolean rankedBorder;

    protected AbstractGui(GuiSessionRegistry registry, SoundService sounds, GuiType type, int rows, boolean rankedBorder) {
        this.registry = Objects.requireNonNull(registry);
        this.sounds = Objects.requireNonNull(sounds);
        this.type = type;
        this.rows = rows;
        this.rankedBorder = rankedBorder;
    }

    public GuiType type() {
        return type;
    }

    public final void open(Player player) {
        GuiSession session = registry.open(player.getUniqueId(), type, rows);
        configureSession(session, player);
        PracticeGuiHolder holder = new PracticeGuiHolder(session.sessionId(), type, rows);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title(player, session));
        holder.bind(inventory);
        if (rankedBorder || type == GuiType.RANKED_QUEUE || type == GuiType.UNRANKED_QUEUE
                || type == GuiType.KIT_SELECT || type == GuiType.MAP_SELECT || type == GuiType.DUEL_REQUEST) {
            GuiDecorator.decorateBorder(inventory, session.ranked() || type == GuiType.RANKED_QUEUE);
        }
        render(player, session, inventory);
        player.openInventory(inventory);
        sounds.play(player, "gui-open");
    }

    protected void configureSession(GuiSession session, Player player) {
        // subclasses override
    }

    protected abstract Component title(Player player, GuiSession session);

    protected abstract void render(Player player, GuiSession session, Inventory inventory);

    public final Component titlePublic(Player player, GuiSession session) {
        return title(player, session);
    }

    public final void renderPublic(Player player, GuiSession session, Inventory inventory) {
        render(player, session, inventory);
    }

    public abstract void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action);

    public boolean matches(UUID sessionId, GuiType type) {
        return this.type == type;
    }
}
