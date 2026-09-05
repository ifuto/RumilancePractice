package com.rumilance.practice.util;

import com.rumilance.practice.kit.KitLoadout;
import com.rumilance.practice.kit.KitLayoutContents;
import com.rumilance.practice.model.KitDefinition;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Space-saving storage for player ekit layouts: instead of re-serializing every item's NBT,
 * the layout is stored as a small delta against the kit's official default layout:
 * <ul>
 *   <li>{@code M<j>><i>} — the default item from slot j now sits in slot i (source empties)</li>
 *   <li>{@code F<i>:<base64>} — a non-default item fully serialized at slot i</li>
 *   <li>{@code E<i>} — a default item was dropped; slot i is intentionally empty</li>
 * </ul>
 * Slots without an op keep their default item. Values without the {@link #PREFIX} are legacy
 * full-array base64 dumps and decode as before. Preset-based kits use the exact same scheme —
 * their defaults are the kit defaults too, so nothing special is required.
 */
public final class KitLayoutDelta {

    public static final String PREFIX = "delta1:";

    private KitLayoutDelta() {
    }

    /** Encodes {@code layout} against the kit defaults; falls back to full base64 when unsafe. */
    public static String encode(ItemStack[] layout, KitDefinition kit) {
        if (layout == null) {
            return ItemSerializer.toBase64(new ItemStack[KitLoadout.SIZE]);
        }
        if (kit == null) {
            return ItemSerializer.toBase64(layout);
        }
        ItemStack[] defaults = baseline(kit);
        boolean[] sourceUsed = new boolean[KitLoadout.SIZE];
        String[] slotOps = new String[KitLoadout.SIZE];
        // Pass 1: moves and foreign items. A moved default item consumes its source slot, so
        // that slot must not also be reported as emptied.
        for (int i = 0; i < KitLoadout.SIZE; i++) {
            ItemStack target = i < layout.length ? layout[i] : null;
            if (target == null || stackEquals(defaults[i], target)) {
                continue;
            }
            int movedFrom = -1;
            for (int j = 0; j < KitLoadout.SIZE; j++) {
                if (j == i || sourceUsed[j] || defaults[j] == null) {
                    continue;
                }
                if (stackEquals(defaults[j], target)) {
                    movedFrom = j;
                    break;
                }
            }
            if (movedFrom >= 0) {
                sourceUsed[movedFrom] = true;
                slotOps[i] = "M" + movedFrom + ">" + i;
            } else {
                slotOps[i] = "F" + i + ":" + ItemSerializer.singleToBase64(target);
            }
        }
        // Pass 2: default items the player dropped (unless their slot fed a move).
        for (int i = 0; i < KitLoadout.SIZE; i++) {
            ItemStack target = i < layout.length ? layout[i] : null;
            if (target == null && defaults[i] != null && !sourceUsed[i]) {
                slotOps[i] = "E" + i;
            }
        }
        StringBuilder out = new StringBuilder(PREFIX);
        boolean first = true;
        for (String op : slotOps) {
            if (op == null) {
                continue;
            }
            if (!first) {
                out.append(';');
            }
            out.append(op);
            first = false;
        }
        return out.toString();
    }

    /** Decodes either the delta format or a legacy full-array base64 dump. */
    public static ItemStack[] decode(String stored, KitDefinition kit) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return ItemSerializer.fromBase64(stored);
        }
        ItemStack[] out = baseline(kit);
        if (kit == null) {
            return out;
        }
        String body = stored.substring(PREFIX.length());
        if (body.isEmpty()) {
            return out;
        }
        // Parse first so a move never reads a slot that an earlier op already overwrote.
        List<int[]> moves = new ArrayList<>();
        List<Object[]> fulls = new ArrayList<>();
        Set<Integer> empties = new HashSet<>();
        Set<Integer> assigned = new HashSet<>();
        for (String op : body.split(";")) {
            if (op.isEmpty()) {
                continue;
            }
            char kind = op.charAt(0);
            try {
                if (kind == 'M') {
                    int sep = op.indexOf('>');
                    int from = Integer.parseInt(op.substring(1, sep));
                    int to = Integer.parseInt(op.substring(sep + 1));
                    if (inRange(from) && inRange(to)) {
                        moves.add(new int[]{from, to});
                        assigned.add(to);
                    }
                } else if (kind == 'F') {
                    int sep = op.indexOf(':');
                    int slot = Integer.parseInt(op.substring(1, sep));
                    if (inRange(slot)) {
                        fulls.add(new Object[]{slot, op.substring(sep + 1)});
                        assigned.add(slot);
                    }
                } else if (kind == 'E') {
                    int slot = Integer.parseInt(op.substring(1));
                    if (inRange(slot)) {
                        empties.add(slot);
                        assigned.add(slot);
                    }
                }
            } catch (RuntimeException ignored) {
                // Malformed op: skip it rather than losing the whole layout.
            }
        }
        // Pristine baseline for move sources: moves must read the ORIGINAL default item even
        // when an E op (or another move) also touches that slot.
        ItemStack[] defaults = out.clone();
        for (int slot : empties) {
            out[slot] = null;
        }
        for (int[] move : moves) {
            out[move[1]] = defaults[move[0]] == null ? null : defaults[move[0]].clone();
        }
        for (Object[] full : fulls) {
            int slot = (int) full[0];
            try {
                out[slot] = ItemSerializer.singleFromBase64((String) full[1]);
            } catch (RuntimeException e) {
                out[slot] = null;
            }
        }
        // Moved-from slots become empty unless another op assigned them a new occupant.
        for (int[] move : moves) {
            if (!assigned.contains(move[0])) {
                out[move[0]] = null;
            }
        }
        return out;
    }

    private static boolean inRange(int slot) {
        return slot >= 0 && slot < KitLoadout.SIZE;
    }

    /** The default layout both sides of the delta agree on (placeholders stripped). */
    private static ItemStack[] baseline(KitDefinition kit) {
        ItemStack[] defaults = KitLoadout.fromOfficial(kit);
        KitLayoutContents.stripPlaceholders(defaults);
        return defaults;
    }

    /** ItemStack#equals semantics (type + amount + meta). */
    private static boolean stackEquals(ItemStack a, ItemStack b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
