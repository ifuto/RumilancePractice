package com.rumilance.practice.practice;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * The combat bot behind a session, hidden behind a seam: the very same AI drives either
 * a Paper {@link org.bukkit.entity.Mannequin} ({@link MannequinBody}, today's default) or a
 * carpet-style packet {@link net.minecraft.server.level.ServerPlayer fake player}
 * ({@code PacketBotBody}, opt-in) — the packet bot is a real player entity with a real
 * inventory, skin, hitbox and vanilla damage pipeline.
 */
public interface BotBody {

    World getWorld();

    Location getLocation();

    Location getEyeLocation();

    Vector getVelocity();

    void setVelocity(Vector velocity);

    double getHealth();

    void setHealth(double health);

    double getMaxHealth();

    void setMaxHealth(double max);

    boolean isValid();

    boolean isDead();

    boolean isOnGround();

    double getFallDistance();

    int getFireTicks();

    void setFireTicks(int ticks);

    boolean hasLineOfSight(Entity other);

    void teleport(Location location);

    void remove();

    void swingMainHand();

    void setRotation(float yaw, float pitch);

    EntityEquipment getEquipment();

    /** The Bukkit living-entity view (kit binding, drop-chance guards, particle origins). */
    LivingEntity living();

    /** The Bukkit entity view for generic entity operations. */
    Entity entity();

    UUID uuid();

    /** True when {@code entity} IS this bot (damage/projectile attribution). */
    boolean owns(Entity entity);

    String displayName();

    /** True when the body is a packet fake player (vanilla pipeline already applied the hit). */
    boolean isPacket();

    default AttributeInstance attribute(org.bukkit.attribute.Attribute attribute) {
        LivingEntity living = living();
        return living == null ? null : living.getAttribute(attribute);
    }
}
