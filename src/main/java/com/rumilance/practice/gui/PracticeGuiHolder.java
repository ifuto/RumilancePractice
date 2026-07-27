package com.rumilance.practice.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * InventoryHolder that identifies GUIs by session id + type, never by title string.
 */
public final class PracticeGuiHolder implements InventoryHolder {

    private final UUID sessionId;
    private final GuiType type;
    private final int rows;
    private Inventory inventory;

    public PracticeGuiHolder(UUID sessionId, GuiType type, int rows) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.type = Objects.requireNonNull(type, "type");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1-6");
        }
        this.rows = rows;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public GuiType type() {
        return type;
    }

    public int rows() {
        return rows;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
