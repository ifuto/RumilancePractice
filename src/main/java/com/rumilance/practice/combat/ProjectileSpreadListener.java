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
 * <p>Vanilla adds a small random offset to the launch direction ({@code divergence = 1.0}
 * for both), which makes throws land slightly off the crosshair and makes techniques that
 * need a precise landing — such as "pearl catching" a teammate — feel unreliable. The
 * correction straightens ONLY the thrown part of the velocity; the thrower's own motion
 * keeps vanilla's exact inheritance rule (full motion while airborne — elytra glide or
 * wind-charge knockback — vertical motion dropped when grounded), so the trajectory is
 * bit-for-bit vanilla apart from the direction noise. See {@link ProjectileSpreadMath} for
 * the invariants. Configured under {@code combat.projectile-spread.*}: {@code 0} = perfectly
 * straight (best for pearl-catching), {@code 50} = exactly vanilla spread, values between
 * scale the offset linearly. Only ender pearls and wind charges thrown by players are
 * affected.</p>
 */
public final class ProjectileSpreadListener implements Listener {

    private final ConfigService configService;

    public ProjectileSpreadListener(ConfigService configService) {
        this.configService = configService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof EnderPearl) && !(projectile instanceof WindCharge)) {
            return;
        }
        if (!(projectile.getShooter() instanceof org.bukkit.entity.Player shooter)) {
            return;
        }
        double value = configService.config().getDouble("combat.projectile-spread.pearl", 0.0D);
        if (projectile instanceof WindCharge) {
            value = configService.config().getDouble("combat.projectile-spread.wind-charge", 0.0D);
        }
        // Scale: 0 = perfectly straight, 50 = vanilla spread. Clamp to [0,50]; negatives -> 0.
        value = Math.max(0.0D, Math.min(50.0D, value));
        double spread = value / 50.0D;
        if (spread >= 0.999D) {
            return; // vanilla spread fully retained
        }
        Vector corrected = ProjectileSpreadMath.straightenThrow(
                projectile.getVelocity(),
                shooter.getVelocity(),
                shooter.isOnGround(),
                shooter.getEyeLocation().getDirection(),
                spread);
        projectile.setVelocity(corrected);
    }
}
