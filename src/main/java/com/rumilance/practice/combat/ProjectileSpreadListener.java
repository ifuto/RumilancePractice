package com.rumilance.practice.combat;

import com.rumilance.practice.config.ConfigService;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

/**
 * Removes (or scales) the random spread applied to ender pearls and wind charges when thrown.
 *
 * <p>Vanilla adds a small random offset to the launch direction ({@code Projectile.spread = 1.0F}
 * for both), which makes throws land slightly off the crosshair and makes techniques that need a
 * precise landing — such as "pearl catching" a teammate — feel unreliable. After the launch event
 * we straighten ONLY the thrown part of the velocity (launch velocity minus the thrower's own
 * motion, which stays intact — important while gliding with an elytra), aiming it at the thrower's
 * look direction and blending it with the actual launch direction by the configured factor:
 * <ul>
 *   <li>{@code 0}  = perfectly straight at the crosshair (best for pearl-catching),</li>
 *   <li>{@code 50} = exactly vanilla random spread,</li>
 *   <li>values in between scale the vanilla offset linearly.</li>
 * </ul>
 * Configured under {@code combat.projectile-spread.*} (0-50). Only affects ender pearls and wind
 * charges thrown by players; all other projectiles are left untouched.</p>
 */
public final class ProjectileSpreadListener implements Listener {

    private final ConfigService configService;

    public ProjectileSpreadListener(ConfigService configService) {
        this.configService = configService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof EnderPearl) && !(projectile instanceof WindCharge)) {
            return;
        }
        if (!(projectile.getShooter() instanceof org.bukkit.entity.Player)) {
            return;
        }
        double value = configService.config().getDouble("combat.projectile-spread.pearl", 0.0D);
        if (projectile instanceof WindCharge) {
            value = configService.config().getDouble("combat.projectile-spread.wind-charge", 0.0D);
        }
        // Scale: 0 = perfectly straight, 50 = vanilla spread. Clamp to [0,50]; negatives -> 0.
        value = Math.max(0.0D, Math.min(50.0D, value));
        // fraction of vanilla spread retained.
        double spread = value / 50.0D;
        // spread >= 1 (value >= 50) means keep vanilla behaviour entirely.
        if (spread >= 0.999D) {
            return;
        }

        org.bukkit.entity.Player shooter = (org.bukkit.entity.Player) projectile.getShooter();
        Vector velocity = projectile.getVelocity();
        // Vanilla adds the thrower's own motion to the launch velocity (that is why pearls /
        // wind charges thrown mid-elytra fly far ahead). Straighten ONLY the thrown part:
        // removing the spread from the full vector would keep the huge inherited speed but
        // snap the direction to the look vector, producing wildly wrong trajectories while
        // gliding (the "weird behaviour" when throwing during elytra flight).
        Vector throwerMotion = shooter.getVelocity();
        Vector thrown = velocity.clone().subtract(throwerMotion);
        double speed = thrown.length();
        if (speed <= 0.0001D) {
            return;
        }
        Vector look = shooter.getEyeLocation().getDirection().normalize();
        // Perfect direction (spread 0) vs the actual (spread 1). Interpolate between them so the
        // configured value scales the random offset rather than snapping to nothing.
        Vector currentDir = thrown.normalize();
        Vector blended = look.clone().multiply(1.0D - spread).add(currentDir.multiply(spread)).normalize();
        projectile.setVelocity(blended.multiply(speed).add(throwerMotion));
    }
}
