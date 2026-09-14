package com.rumilance.practice.practice;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.UUID;

/** {@link BotBody} over today's Paper Mannequin entity — the proven default path. */
public final class MannequinBody implements BotBody {

    private final Mannequin mannequin;

    public MannequinBody(Mannequin mannequin) {
        this.mannequin = mannequin;
    }

    public Mannequin mannequin() {
        return mannequin;
    }

    @Override public World getWorld() { return mannequin.getWorld(); }
    @Override public Location getLocation() { return mannequin.getLocation(); }
    @Override public Location getEyeLocation() { return mannequin.getEyeLocation(); }
    @Override public Vector getVelocity() { return mannequin.getVelocity(); }
    @Override public void setVelocity(Vector velocity) { mannequin.setVelocity(velocity); }
    @Override public double getHealth() { return mannequin.getHealth(); }
    @Override public void setHealth(double health) { mannequin.setHealth(health); }

    @Override public double getMaxHealth() {
        AttributeInstance attr = mannequin.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0d : attr.getValue();
    }

    @Override public void setMaxHealth(double max) {
        AttributeInstance attr = mannequin.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(max);
        }
        mannequin.setHealth(Math.min(mannequin.getHealth(), max));
    }

    @Override public boolean isValid() { return mannequin != null && mannequin.isValid(); }
    @Override public boolean isDead() { return mannequin.isDead(); }
    @Override public boolean isOnGround() { return mannequin.isOnGround(); }
    @Override public double getFallDistance() { return mannequin.getFallDistance(); }
    @Override public int getFireTicks() { return mannequin.getFireTicks(); }
    @Override public void setFireTicks(int ticks) { mannequin.setFireTicks(ticks); }
    @Override public boolean hasLineOfSight(Entity other) {
        return other instanceof LivingEntity living && mannequin.hasLineOfSight(living);
    }
    @Override public void teleport(Location location) { mannequin.teleport(location); }
    @Override public void remove() { mannequin.remove(); }
    @Override public void swingMainHand() { mannequin.swingMainHand(); }
    @Override public void setRotation(float yaw, float pitch) { mannequin.setRotation(yaw, pitch); }
    @Override public EntityEquipment getEquipment() { return mannequin.getEquipment(); }
    @Override public LivingEntity living() { return mannequin; }
    @Override public Entity entity() { return mannequin; }
    @Override public UUID uuid() { return mannequin.getUniqueId(); }
    @Override public boolean owns(Entity entity) {
        return entity != null && entity.getUniqueId().equals(mannequin.getUniqueId());
    }
    @Override public String displayName() { return mannequin.getName(); }
    @Override public boolean isPacket() { return false; }
}
