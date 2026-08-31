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
 * we rebuild the velocity vector from the thrower's look direction multiplied by the configured
 * spread factor:
 * <ul>
 *   <li>{@code 0.0} = perfectly on the crosshair (best for pearl-catching),</li>
 *   <li>{@code 1.0} = vanilla random spread,</li>
 *   <li>values in between = a fraction of vanilla spread.</li>
 * </ul>
 * Configured under {@code combat.projectile-spread.*}. Only affects ender pearls and wind charges
 * thrown by players; all other projectiles are left untouched.</p>
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
        double spread = configService.config().getDouble("combat.projectile-spread.pearl", 0.0D);
        if (projectile instanceof WindCharge) {
            spread = configService.config().getDouble("combat.projectile-spread.wind-charge", 0.0D);
        }
        // Clamp: 0 = no spread (pixel-accurate), 1 = vanilla. Negative treated as 0.
        spread = Math.max(0.0D, Math.min(1.0D, spread));
        // spread >= 1 means keep vanilla behaviour entirely.
        if (spread >= 0.999D) {
            return;
        }

        org.bukkit.entity.Player shooter = (org.bukkit.entity.Player) projectile.getShooter();
        Vector velocity = projectile.getVelocity();
        double speed = velocity.length();
        if (speed <= 0.0001D) {
            return;
        }
        Vector look = shooter.getEyeLocation().getDirection().normalize();
        // Perfect direction (spread 0) vs the actual (spread 1). Interpolate between them so the
        // configured value scales the random offset rather than snapping to nothing.
        Vector currentDir = velocity.clone().normalize();
        Vector blended = look.clone().multiply(1.0D - spread).add(currentDir.clone().multiply(spread)).normalize();
        projectile.setVelocity(blended.multiply(speed));
    }
}
