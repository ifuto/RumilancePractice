package com.rumilance.practice.combat;

/**
 * Pure policy for what a practice room may silence. {@code PracticeListener.onDamage} used to
 * cancel every non-whitelisted damage cause inside a room — including SUFFOCATION and DROWNING.
 * A player buried in their own obsidian (crystal / anchor rooms) or drowning next to bot lava
 * therefore became an unkillable wall: the finishing tick could never land as suffocation, and
 * nothing else outside the whitelist could touch them either.
 *
 * <p>The invariant is intentionally simple so the "buried but invincible" state is
 * <strong>theoretically impossible</strong>:</p>
 * <ol>
 *   <li>Suffocation and drowning always tick (cause names stay raw strings to keep the kernel
 *       Bukkit-free).</li>
 *   <li>A lethal frame that no totem absorbed always resolves to a real death — never a silent
 *       cancel. The vanilla death event is then routed by the practice death flow
 *       (bot match loss / drill respawn).</li>
 * </ol>
 * Everything else (fall / starvation / stray non-combat damage in drills) keeps the classic
 * room-protection cancel for non-lethal amounts.
 */
public final class PracticeRoomDamagePolicy {

    private PracticeRoomDamagePolicy() {
    }

    /** Cause names that must never be silenced inside a practice room. */
    public static boolean isEnvironmentalDot(String causeName) {
        return "SUFFOCATION".equals(causeName) || "DROWNING".equals(causeName);
    }

    /**
     * @param causeName      {@code EntityDamageEvent.DamageCause#name()} of the incoming frame
     * @param remainingAfter HP (+ absorption) left if this frame landed
     *                       ({@link PracticeDeath#remainingAfter}); {@code <= 0} means lethal
     * @return {@code true} when the room-protection layer may cancel this damage frame
     */
    public static boolean mayCancelRoomDamage(String causeName, double remainingAfter) {
        if (isEnvironmentalDot(causeName)) {
            return false;
        }
        // A non-totem lethal must always reach the death pipeline — cancelling it is what
        // produced the "0 HP but unhittable" zombie.
        if (remainingAfter <= 0.0d) {
            return false;
        }
        return true;
    }
}
