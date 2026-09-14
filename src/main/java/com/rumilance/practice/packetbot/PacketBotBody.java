package com.rumilance.practice.packetbot;

import com.rumilance.practice.practice.BotBody;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.UUID;

/** {@link BotBody} over a {@link PacketBot} — the same AI drives a real player entity. */
public final class PacketBotBody implements BotBody {

    private final PacketBot bot;

    public PacketBotBody(PacketBot bot) {
        this.bot = bot;
    }

    public PacketBot bot() {
        return bot;
    }

    private Player view() {
        return (Player) bot.getBukkitEntity();
    }

    @Override public World getWorld() { return view().getWorld(); }
    @Override public Location getLocation() { return view().getLocation(); }
    @Override public Location getEyeLocation() { return view().getEyeLocation(); }

    @Override public Vector getVelocity() {
        Vec3 delta = bot.getDeltaMovement();
        return new Vector(delta.x, delta.y, delta.z);
    }

    @Override public void setVelocity(Vector velocity) {
        // No client overrides server-side player physics, so delta movement drives the body
        // exactly like knockback does — the AI's velocity nudges work unchanged.
        bot.setDeltaMovement(new Vec3(velocity.getX(), velocity.getY(), velocity.getZ()));
        view().setSprinting(false);
    }

    @Override public double getHealth() { return view().getHealth(); }
    @Override public void setHealth(double health) { view().setHealth(health); }

    @Override public double getMaxHealth() {
        AttributeInstance attr = view().getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0d : attr.getValue();
    }

    @Override public void setMaxHealth(double max) {
        AttributeInstance attr = view().getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(max);
        }
        view().setHealth(Math.min(view().getHealth(), max));
    }

    @Override public boolean isValid() { return view() != null && view().isValid(); }
    @Override public boolean isDead() { return view().isDead(); }
    @Override public boolean isOnGround() { return view().isOnGround(); }
    @Override public double getFallDistance() { return view().getFallDistance(); }
    @Override public int getFireTicks() { return view().getFireTicks(); }
    @Override public void setFireTicks(int ticks) { view().setFireTicks(ticks); }

    @Override public boolean hasLineOfSight(Entity other) {
        return other instanceof LivingEntity living && view().hasLineOfSight(living);
    }

    @Override public void teleport(Location location) { view().teleport(location); }

    @Override public void remove() {
        PacketBotFactory.despawn(this);
    }

    @Override public void swingMainHand() { bot.swing(InteractionHand.MAIN_HAND); }
    @Override public void setRotation(float yaw, float pitch) { view().setRotation(yaw, pitch); }
    @Override public EntityEquipment getEquipment() { return view().getEquipment(); }
    @Override public LivingEntity living() { return view(); }
    @Override public Entity entity() { return view(); }
    @Override public UUID uuid() { return bot.getUUID(); }
    @Override public boolean owns(Entity entity) {
        return entity != null && entity.getUniqueId().equals(bot.getUUID());
    }
    @Override public String displayName() { return bot.profileName(); }
    @Override public boolean isPacket() { return true; }
}
