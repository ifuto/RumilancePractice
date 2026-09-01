package com.rumilance.practice.combat;

import com.rumilance.practice.PluginIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Resolves which player owns the end crystal / TNT that deals explosion damage, so blast
 * damage is attributed correctly for kill credit and outcome rulings.
 *
 * <p>Vanilla crystal PvP is <strong>self-damaging</strong>: detonating your own end crystal
 * hurts you too (the basis of crystal combos), and the damage event fired to the victim carries
 * the crystal as the damager with <em>no Bukkit attacker</em>. The previous code therefore (a)
 * never credited the crystal owner for an opponent kill, and (b) treated every crystal blast as
 * self-inflicted/environmental, which produced the wrong suicide/&ldquo;draw&rdquo; rulings.
 * This tracker stamps each placed crystal with its owner (and reads the live source of practice
 * TNT) so the damage handlers can attribute the blast.</p>
 *
 * <p>Crystal self-damage is never cancelled here — this only resolves ownership.</p>
 */
public final class ExplosionSourceTracker implements Listener {

    private final NamespacedKey ownerKey;

    public ExplosionSourceTracker(Plugin plugin) {
        this.ownerKey = new NamespacedKey(PluginIdentity.PDC_NAMESPACE, "explosion_owner");
    }

    /** Stamp an end crystal with the player who placed it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        crystal.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
    }

    /**
     * Resolves the player responsible for an explosion damage event, or {@code null} for a plain
     * environmental hit (fall, void, fire...). Covers practice TNT ({@link TNTPrimed#getSource()})
     * and end crystals (owner PDC).
     */
    public UUID resolveExplosionSource(EntityDamageEvent event) {
        if (event == null) {
            return null;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return null;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            // Respawn-anchor / bed block explosion: no entity owner tracked yet.
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source.getUniqueId();
        }
        if (damager instanceof EnderCrystal crystal) {
            return ownerOf(crystal);
        }
        return null;
    }

    private UUID ownerOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source.getUniqueId();
        }
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
