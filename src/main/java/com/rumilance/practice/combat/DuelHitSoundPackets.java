package com.rumilance.practice.combat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * ProtocolLib side of {@link DuelHitSoundService}. Outbound NAMED_SOUND_EFFECT packets
 * carrying {@code entity.player.attack.nodamage} (the i-frame whiff thud) are rewritten to
 * the regular strong-hit sound when the receiver is fighting a 1v1 duel. Everything else
 * (volume, pitch, position, category) passes through untouched.
 *
 * <p>Only class-loaded after the ProtocolLib presence check in the facade.</p>
 */
final class DuelHitSoundPackets {

    private DuelHitSoundPackets() {
    }

    static void register(org.bukkit.plugin.Plugin plugin,
                         com.rumilance.practice.match.MatchRegistry matchRegistry) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.NORMAL, PacketType.Play.Server.NAMED_SOUND_EFFECT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player receiver = event.getPlayer();
                if (receiver == null || event.isPlayerTemporary()) {
                    return;
                }
                Sound sound;
                try {
                    sound = event.getPacket().getSoundEffects().read(0);
                } catch (Throwable t) {
                    return; // Never break sound playback because of the interceptor.
                }
                if (sound != Sound.ENTITY_PLAYER_ATTACK_NODAMAGE) {
                    return;
                }
                MatchSession session = matchRegistry.byPlayer(receiver.getUniqueId()).orElse(null);
                if (session == null || session.state() != MatchState.ACTIVE
                        || session.participants().size() != 2) {
                    return;
                }
                event.getPacket().getSoundEffects().write(0, Sound.ENTITY_PLAYER_ATTACK_STRONG);
            }
        });
    }
}
