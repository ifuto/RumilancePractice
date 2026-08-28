package com.rumilance.practice.combat;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Restores vanilla item-swap timing on Paper 1.21.4+.
 *
 * <p>Paper's {@code unsupported-settings.update-equipment-on-player-actions} defaults to
 * {@code true}, which applies the <em>new</em> item's attributes before the attack packet is
 * processed. That is what made hotbar / F-key swaps feel 50-50 compared with vanilla and with
 * most competitive practice servers. Setting it {@code false} brings back vanilla swap (MC-28289
 * style attribute carry) without NMS packets.</p>
 */
public final class PaperCombatTuning {

    private PaperCombatTuning() {
    }

    /**
     * @return true when the live Paper global config was flipped this session
     */
    public static boolean applyVanillaItemSwap(Logger logger) {
        try {
            Class<?> global = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object instance = global.getMethod("get").invoke(null);
            if (instance == null) {
                logPersistHint(logger, "Paper GlobalConfiguration.get() returned null");
                return false;
            }
            if (setBoolean(instance, "unsupportedSettings", "updateEquipmentOnPlayerActions", false)
                    || setBoolean(instance, "misc", "updateEquipmentOnPlayerActions", false)) {
                logger.info("Vanilla item swap restored (Paper update-equipment-on-player-actions=false). "
                        + "Persist in config/paper-global.yml under unsupported-settings.");
                return true;
            }
            logPersistHint(logger, "Paper equipment-update field was not found");
        } catch (ReflectiveOperationException e) {
            logger.log(Level.INFO, "Could not toggle Paper equipment-update at runtime. "
                    + "Set unsupported-settings.update-equipment-on-player-actions: false in paper-global.yml", e);
        }
        return false;
    }

    private static void logPersistHint(Logger logger, String detail) {
        logger.info(detail + ". Set unsupported-settings.update-equipment-on-player-actions: false "
                + "in config/paper-global.yml and restart.");
    }

    private static boolean setBoolean(Object root, String groupField, String valueField, boolean value) {
        try {
            Object group = fieldValue(root, groupField);
            if (group == null) {
                return false;
            }
            Field field = findField(group.getClass(), valueField);
            if (field == null || (field.getType() != boolean.class && field.getType() != Boolean.class)) {
                return false;
            }
            field.setAccessible(true);
            field.set(group, value);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static Object fieldValue(Object target, String name) throws IllegalAccessException {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        return field.get(target);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }
}
