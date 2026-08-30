package com.rumilance.practice.combat;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Plugin-side workarounds for Paper PvP behaviour regressions that affect our fights. All
 * handlers only act on active combatants (duel/team match or FFA) so lobby/kit-edit movement
 * is never touched.
 *
 * <ul>
 *   <li><b>Paper #10742 / SPIGOT-7732 / MC-268147 / #13838</b> — a shield hit arms i-frames so
 *       the follow-up swing (melee, or a trident jab after a charge) is swallowed for a few
 *       ticks; and the vanilla post-stun hit deals damage but no knockback. We clear the
 *       victim's i-frames across the stun window and restore the owed knockback.</li>
 *   <li><b>Paper #13426</b> — damage survived through a raised shield should still knock the
 *       blocker back; we re-apply vanilla melee knockback when {@code finalDamage &gt; 0}.</li>
 *   <li><b>Paper #13680 / MC-29519</b> — bow draw force was not being normalised into arrow
 *       damage/crit on some Paper builds; we re-derive the vanilla arrow damage + crit flag
 *       from the shoot draw force so partial/full pulls deal consistent damage.</li>
 * </ul>
 *
 * <p>#12927 (pearl momentum) is fixed upstream in 1.21.10 teleport reworks and #11552
 * (quickswap cooldown read) is mitigated by the ping-rewind in {@link CombatSyncListener};
 * they need no extra handler here.</p>
 */
public final class PaperCombatCompatListener implements Listener {

    /** Ticks in which a hit after a weapon draw/swap is treated as not fully charged (anti attribute-swap). */
    private static final int SWAP_GRACE_TICKS = 5;

    private final Plugin plugin;
    private final Predicate<UUID> combatantTest;
    /** Last tick a combatant drew/changed the item in a hand (hotbar scroll or F swap). */
    private final Map<UUID, Integer> lastWeaponDraw = new ConcurrentHashMap<>();

    public PaperCombatCompatListener(Plugin plugin, Predicate<UUID> combatantTest) {
        this.plugin = plugin;
        this.combatantTest = combatantTest;
    }

    private boolean combatant(Player player) {
        return player != null && combatantTest.test(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        if (combatant(event.getPlayer()) && ShieldBreakStunFix.holdsShieldBreaker(event.getPlayer())) {
            lastWeaponDraw.put(event.getPlayer().getUniqueId(), org.bukkit.Bukkit.getCurrentTick());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (combatant(event.getPlayer()) && ShieldBreakStunFix.holdsShieldBreaker(event.getPlayer())) {
            lastWeaponDraw.put(event.getPlayer().getUniqueId(), org.bukkit.Bukkit.getCurrentTick());
        }
    }

    /**
     * Paper #11552 / #13436 / #13588: quick-swapping from a non-cooldown item to a weapon makes
     * {@code getAttackCooldown()} read 1.0 for a few ticks (attribute-swap), letting a freshly
     * drawn axe/mace/spear land a free full-charge/crit hit. Treat such a hit as a glancing,
     * non-charged swing: cap the damage low and strip the crit bonus.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttributeSwapHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!combatant(attacker) || !combatant(victim)) {
            return;
        }
        if (!ShieldBreakStunFix.holdsShieldBreaker(attacker)) {
            return;
        }
        Integer drewAt = lastWeaponDraw.get(attacker.getUniqueId());
        boolean justDrew = drewAt != null
                && (org.bukkit.Bukkit.getCurrentTick() - drewAt) <= SWAP_GRACE_TICKS;
        boolean uncharged = attacker.getAttackCooldown() < 0.9f;
        if (!justDrew && !uncharged) {
            return;
        }
        // Glancing hit: a not-yet-charged melee swing in vanilla deals ~20% and cannot crit.
        event.setDamage(event.getDamage() * 0.2d);
        stripCritical(event);
    }

    /** Clears the critical flag if the running Paper exposes the setter (1.21+ does). */
    private static void stripCritical(EntityDamageByEntityEvent event) {
        try {
            event.getClass().getMethod("setCritical", boolean.class).invoke(event, false);
        } catch (ReflectiveOperationException ignored) {
            // Older API without the setter: the damage cap alone still prevents the one-shot.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShieldedHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayerAttacker(event);
        if (attacker == null || !combatant(victim) || !combatant(attacker)) {
            return;
        }
        boolean wasBlocking = victim.isBlocking();
        double net = event.getFinalDamage();
        // A shield hit from an axe/sword/mace/trident arms i-frames and stuns the shield;
        // clear the victim's i-frames so the immediate follow-up swing/jab connects.
        if (wasBlocking && ShieldBreakStunFix.holdsShieldBreaker(attacker)) {
            ShieldBreakStunFix.allowFollowUpHit(plugin, victim);
            return;
        }
        // Post-stun follow-up (the hit just after the break): vanilla deals damage but applies
        // no knockback (MC-268147). Restore the owed knockback.
        if (net > 0.0d && ShieldBreakStunFix.recentShieldBreak(victim)) {
            applyVanillaMeleeKnockback(victim, attacker);
            return;
        }
        // Paper #13426: damage survived through a block should still knock the blocker.
        if (net > 0.0d && wasBlocking) {
            applyVanillaMeleeKnockback(victim, attacker);
        }
    }

    /**
     * Re-derives vanilla arrow damage/crit from the bow draw force so partial pulls deal less
     * and full pulls fire a critical (1.5-1.75x) arrow, independent of any Paper normalisation
     * gap (#13680). Only normalises when the projectile is a player-fired arrow in a fight.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void normalizeArrowDamage(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (!combatant(shooter)) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        float force = Math.max(0.0f, Math.min(1.0f, event.getForce()));
        // Vanilla: base arrow damage 2.0 scaled by draw force, fully-drawn shots are critical.
        double base = 2.0d * (2.0d * force + force * force);
        boolean full = force >= 0.9f;
        arrow.setCritical(full);
        // Preserve Power-enchant bonus already on the arrow; only correct the base scaling when
        // the arrow is weaker than the draw force implies (partial pull underdamage bug).
        double current = arrow.getDamage();
        double minExpected = base;
        if (current < minExpected) {
            arrow.setDamage(base);
        }
    }

    private Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void applyVanillaMeleeKnockback(Player victim, Player attacker) {
        double dx = victim.getLocation().getX() - attacker.getLocation().getX();
        double dz = victim.getLocation().getZ() - attacker.getLocation().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) {
            float yaw = attacker.getLocation().getYaw();
            dx = -Math.sin(Math.toRadians(yaw));
            dz = Math.cos(Math.toRadians(yaw));
            horizontal = Math.sqrt(dx * dx + dz * dz);
        }
        double strength = 0.4d;
        double nx = dx / horizontal;
        double nz = dz / horizontal;
        Vector vel = victim.getVelocity();
        double newX = vel.getX() / 2.0d - nx * strength;
        double newZ = vel.getZ() / 2.0d - nz * strength;
        double newY = Math.min(0.4d, vel.getY() / 2.0d + 0.5d);
        victim.setVelocity(new Vector(newX, newY, newZ));
    }
}
