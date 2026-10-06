package com.rumilance.practice.combat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.sound.BuiltinSound;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * PacketEvents side of {@link DuelHitSoundService}. Outbound {@code NAMED_SOUND_EFFECT} packets
 * carrying {@code entity.player.attack.nodamage} (the i-frame whiff thud) are rewritten to the
 * regular strong-hit sound when the receiver is fighting a 1v1 duel. Everything else (volume,
 * pitch, position, category) passes through untouched.
 *
 * <p>Only class-loaded after the PacketEvents presence check in the facade.</p>
 */
final class DuelHitSoundPackets implements PacketListener {

    private final com.rumilance.practice.match.MatchRegistry matchRegistry;

    private DuelHitSoundPackets(com.rumilance.practice.match.MatchRegistry matchRegistry) {
        this.matchRegistry = matchRegistry;
    }

    static void register(Plugin plugin, com.rumilance.practice.match.MatchRegistry matchRegistry) {
        PacketEvents.getAPI().getEventManager()
                .registerListener(new DuelHitSoundPackets(matchRegistry), PacketListenerPriority.NORMAL);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.NAMED_SOUND_EFFECT) {
            return;
        }
        Object receiver = event.getPlayer();
        if (!(receiver instanceof Player target)) {
            return;
        }
        WrapperPlayServerSoundEffect sound;
        try {
            sound = new WrapperPlayServerSoundEffect(event);
        } catch (Throwable t) {
            return; // Never break sound playback because of the interceptor.
        }
        if (sound.getSound() != BuiltinSound.ENTITY_PLAYER_ATTACK_NODAMAGE) {
            return;
        }
        MatchSession session = matchRegistry.byPlayer(target.getUniqueId()).orElse(null);
        if (session == null || session.state() != MatchState.ACTIVE
                || session.participants().size() != 2) {
            return;
        }
        sound.setSound(BuiltinSound.ENTITY_PLAYER_ATTACK_STRONG);
    }
}
