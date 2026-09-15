package com.rumilance.practice.packetbot;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * A carpet-style fake player on Paper: a real {@link ServerPlayer} (real inventory, skin,
 * hitbox, sounds, vanilla damage/totem/death pipeline) wired to a {@link FakePlayerConnection}.
 * Never saved to playerdata, hunger constantly topped up, and death is redirected to the
 * session's callback instead of vanilla's drop/respawn flow.
 */
public class PacketBot extends ServerPlayer {

    private final MinecraftServer owningServer;
    private final String profileName;
    private Runnable deathCallback = () -> { };

    public PacketBot(MinecraftServer server, ServerLevel level, GameProfile profile,
                     ClientInformation information) {
        super(server, level, profile, information);
        this.owningServer = server;
        this.profileName = profile.name();
    }

    public String profileName() {
        return profileName;
    }

    public MinecraftServer owningServer() {
        return owningServer;
    }

    public void deathCallback(Runnable callback) {
        this.deathCallback = callback;
    }

    @Override
    public void tick() {
        super.tick();
        // No client sends hunger packets; keep the bot fed so vanilla sprint/food rules stay real.
        this.getFoodData().eat(1, 0.2f);
    }

    @Override
    public void die(DamageSource source) {
        Runnable callback = deathCallback;
        this.remove(Entity.RemovalReason.KILLED);
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
