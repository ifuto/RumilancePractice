package com.rumilance.practice.combat;

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Resolves the participating player behind a damage source (melee, projectile, primed TNT, ignited creeper).
 */
public final class CombatAttacker {

    private CombatAttacker() {
    }

    public static UUID playerId(Entity damager) {
        if (damager instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player.getUniqueId();
            }
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Creeper creeper && creeper.getIgniter() instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }
}
