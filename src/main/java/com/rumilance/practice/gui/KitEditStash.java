package com.rumilance.practice.gui;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the in-progress kit layout outside the GUI session. Opening an overlay
 * (smithing table / anvil) replaces the {@link GuiSession}, which would otherwise
 * drop the 41-slot layout and wipe every item except the one being edited.
 */
public final class KitEditStash {

    public record Snapshot(String kitId, String preset, ItemStack[] layout, int slot) {
    }

    private final Map<UUID, Snapshot> byPlayer = new ConcurrentHashMap<>();

    public void put(UUID playerId, String kitId, String preset, ItemStack[] layout, int slot) {
        ItemStack[] copy = copyLayout(layout);
        byPlayer.put(playerId, new Snapshot(kitId, preset == null ? "" : preset, copy, slot));
    }

    public void putLayout(UUID playerId, String kitId, String preset, ItemStack[] layout) {
        Snapshot previous = byPlayer.get(playerId);
        int slot = previous == null ? -1 : previous.slot();
        put(playerId, kitId, preset, layout, slot);
    }

    public Snapshot get(UUID playerId) {
        return byPlayer.get(playerId);
    }

    public ItemStack[] layoutCopy(UUID playerId) {
        Snapshot snapshot = byPlayer.get(playerId);
        if (snapshot == null) {
            return null;
        }
        return copyLayout(snapshot.layout());
    }

    public void clear(UUID playerId) {
        byPlayer.remove(playerId);
    }

    private static ItemStack[] copyLayout(ItemStack[] layout) {
        if (layout == null) {
            return new ItemStack[41];
        }
        ItemStack[] copy = new ItemStack[Math.max(41, layout.length)];
        for (int i = 0; i < layout.length; i++) {
            ItemStack stack = layout[i];
            copy[i] = stack == null ? null : stack.clone();
        }
        return copy;
    }
}
