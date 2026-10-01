package com.rumilance.practice.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;

/**
 * Shulker-box exception for true ADVENTURE mode, built on item NBT — in 1.20.5+ the data
 * components {@code can_break} / {@code can_place_on} (older clients: the legacy CanDestroy /
 * CanPlaceOn NBT tags). In ADVENTURE the client refuses to emit place/dig packets at all unless
 * the HELD item carries a matching predicate, so this is the only mechanism that works there:
 *
 * <ul>
 *   <li>every directly-holdable kit item gets {@code can_break(shader 17種のシュルカーだけ)} —
 *       bare hands cannot carry components, so the shulker must be broken with some item held;</li>
 *   <li>a shulker-box stack itself additionally gets {@code can_place_on(empty predicate)} —
 *       an all-empty block predicate matches ANY clicked block, which is how "placeable
 *       anywhere" is expressed now that wildcard tags no longer exist for this field.</li>
 * </ul>
 *
 * Breakable-vs-placeable ownership rules (only what the player placed in THIS arena) are still
 * enforced by {@link com.rumilance.practice.util.KitBlockRules} in the match/FFA listeners —
 * the components only convince the VANILLA pipeline to deliver BlockPlace/BlockBreak events.
 * The "Can break/Can place on" tooltip lines are suppressed via {@code tooltip_display}
 * (1.21.2 removed {@code show_in_tooltip} from the predicates themselves).
 */
public final class AdventureShulkerCompat {

    private static volatile ItemAdventurePredicate breakShulkerBoxes;
    private static volatile ItemAdventurePredicate placeOnAnyBlock;
    private static volatile boolean supported = true;

    private AdventureShulkerCompat() {
    }

    private static boolean ensurePredicates() {
        if (!supported) {
            return false;
        }
        if (breakShulkerBoxes != null && placeOnAnyBlock != null) {
            return true;
        }
        try {
            List<BlockType> shulkers = new ArrayList<>();
            for (BlockType type : Registry.BLOCK_TYPE) {
                if (type.getKey().getKey().endsWith("shulker_box")) {
                    shulkers.add(type);
                }
            }
            RegistryKeySet<BlockType> set = RegistrySet.keySetFromValues(RegistryKey.BLOCK, shulkers);
            breakShulkerBoxes = ItemAdventurePredicate.itemAdventurePredicate(
                    List.of(BlockPredicate.predicate().blocks(set).build()));
            placeOnAnyBlock = ItemAdventurePredicate.itemAdventurePredicate(
                    List.of(BlockPredicate.predicate().build()));
            return true;
        } catch (Throwable unavailable) {
            // Old Paper forks without the data-component API: keep plain adventure behaviour.
            supported = false;
            return false;
        }
    }

    /** Tags every directly-holdable item in {@code player}'s inventory (36 slots + offhand). */
    public static void tag(Player player) {
        if (player == null || !ensurePredicates()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> targets = new ArrayList<>(inventory.getContents().length + 1);
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null) {
                targets.add(stack);
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null) {
            targets.add(offhand);
        }
        for (ItemStack stack : targets) {
            tagOne(stack);
        }
    }

    private static void tagOne(ItemStack stack) {
        if (stack.getType().isAir()) {
            return;
        }
        if (!stack.hasData(DataComponentTypes.CAN_BREAK)) {
            stack.setData(DataComponentTypes.CAN_BREAK, breakShulkerBoxes);
        }
        if (com.rumilance.practice.util.KitBlockRules.isShulkerBox(stack.getType())
                && !stack.hasData(DataComponentTypes.CAN_PLACE_ON)) {
            stack.setData(DataComponentTypes.CAN_PLACE_ON, placeOnAnyBlock);
        }
        TooltipDisplay current = stack.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        TooltipDisplay.Builder builder = TooltipDisplay.tooltipDisplay();
        Set<DataComponentType> hidden = new HashSet<>();
        if (current != null) {
            hidden.addAll(current.hiddenComponents());
            if (current.hideTooltip()) {
                builder.hideTooltip(true);
            }
        }
        hidden.add(DataComponentTypes.CAN_BREAK);
        hidden.add(DataComponentTypes.CAN_PLACE_ON);
        builder.hiddenComponents(hidden);
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, builder.build());
    }
}
