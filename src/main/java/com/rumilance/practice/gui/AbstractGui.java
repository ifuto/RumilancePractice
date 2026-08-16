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

    /** @return the number of inventory rows this menu uses. */
    public int rows() {
        return rows;
    }

    public final void open(Player player) {
        GuiSession session = registry.open(player.getUniqueId(), type, rows);
        configureSession(session, player);
        PracticeGuiHolder holder = new PracticeGuiHolder(session.sessionId(), type, rows);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title(player, session));
        holder.bind(inventory);
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

    /**
     * Simple click handler (no {@link org.bukkit.event.inventory.ClickType}). Menus can override
     * either this or the extended overload below; the default is a no-op so a menu that only
     * cares about click types doesn't have to implement both.
     */
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        // default: no-op — most menus override this, ClickType-aware menus override the overload.
    }

    /**
     * Extended click handler that also receives the Bukkit {@link org.bukkit.event.inventory.ClickType}.
     * The default delegates to {@link #handleClick}; menus that need to distinguish left from
     * right clicks (e.g. queue list with a right-click preview) can override this.
     */
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        handleClick(player, session, inventory, slot, action);
    }

    /**
     * Re-renders this menu in place (clears, then fills) without issuing a new open packet,
     * so toggling a setting or turning a page feels instant instead of re-opening the
     * inventory. Safe to call from {@link #handleClick} on the main thread.
     */
    protected void refresh(Player player, GuiSession session, Inventory inventory) {
        inventory.clear();
        render(player, session, inventory);
    }

    public boolean matches(UUID sessionId, GuiType type) {
        return this.type == type;
    }
}
