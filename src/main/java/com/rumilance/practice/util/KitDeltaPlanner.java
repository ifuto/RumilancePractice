package com.rumilance.practice.util;

import java.util.Objects;

/**
 * Pure slot-plan planner for {@link KitLayoutDelta}: given identity keys of the displayed
 * kit layout versus the official default layout, decides per slot whether the default item
 * stays, was emptied (E), moved in from another default slot (M), or is foreign (F — the
 * caller replaces the marker with the item's full serialization).
 *
 * <p>The rule that prevents the "identical duplicate default stack" theft: when a slot
 * keeps a stack identical to its own default, THAT default is spoken for — a later move
 * target must not steal it as its move source. Stealing it used to make the decode drop
 * stacks whenever two identical default stacks existed and one was moved (the "preset kit
 * layout refuses to save" class of bug).</p>
 */
public final class KitDeltaPlanner {

    private KitDeltaPlanner() {
    }

    /**
     * @param layoutKeys  per-slot identity keys of the edited layout ({@code null} = empty); length ≤ keys.length
     * @param defaultKeys per-slot identity keys of the official default; index = slot, SIZE slots expected
     * @return op per slot: {@code "M<j>><i>"} move, {@code "E<i>"} emptied, {@code "F<i>"} foreign
     *         (payload appended by the caller), or {@code null} = slot keeps its default
     */
    public static String[] plan(String[] layoutKeys, String[] defaultKeys) {
        int size = defaultKeys.length;
        boolean[] sourceUsed = new boolean[size];
        String[] ops = new String[size];
        // Sweep A — keeps first: every slot still showing its own default CLAIMS that default
        // before any move scan runs. Scanning moves first with several identical default
        // stacks lets a move steal a kept slot's source; the decode then reads the source as
        // emptied and the stack vanishes from the saved layout (the reported preset-kit
        // "save loses items" bug).
        for (int i = 0; i < size; i++) {
            String target = i < layoutKeys.length ? layoutKeys[i] : null;
            if (target != null && Objects.equals(defaultKeys[i], target)) {
                sourceUsed[i] = true;
            }
        }
        // Sweep B: moves and foreign items. A moved default consumes its source slot, so that
        // slot must not also be reported as emptied.
        for (int i = 0; i < size; i++) {
            String target = i < layoutKeys.length ? layoutKeys[i] : null;
            if (target == null) {
                continue;
            }
            if (Objects.equals(defaultKeys[i], target)) {
                continue;
            }
            int movedFrom = -1;
            for (int j = 0; j < size; j++) {
                if (j == i || sourceUsed[j] || defaultKeys[j] == null) {
                    continue;
                }
                if (Objects.equals(defaultKeys[j], target)) {
                    movedFrom = j;
                    break;
                }
            }
            if (movedFrom >= 0) {
                sourceUsed[movedFrom] = true;
                ops[i] = "M" + movedFrom + ">" + i;
            } else {
                ops[i] = "F" + i;
            }
        }
        // Sweep C: defaults the player dropped (unless their slot fed a move or was kept).
        for (int i = 0; i < size; i++) {
            String target = i < layoutKeys.length ? layoutKeys[i] : null;
            if (target == null && defaultKeys[i] != null && !sourceUsed[i]) {
                ops[i] = "E" + i;
            }
        }
        return ops;
    }
}
