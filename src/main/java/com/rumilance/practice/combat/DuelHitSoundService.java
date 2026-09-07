package com.rumilance.practice.combat;

/**
 * 1v1 duel hit-feedback polish. When the attacker swings inside the victim's invulnerability
 * ticks ({@code noDamageTicks > 0}) vanilla plays the weak "nodamage" thud — the hit clearly
 * connected (the kill-confirm damage lands as soon as the ticks expire), but the sound feels
 * like a whiffed tap. This swaps that one sound for the regular strong-hit swish for players
 * in an active 1v1 fight, purely cosmetically: no damage, knockback or attack-timing
 * mechanics change.
 *
 * <p>The packet interception needs ProtocolLib; servers without the soft-depend simply keep
 * the vanilla sound (all actual ProtocolLib classes live in {@link DuelHitSoundPackets},
 * which is only class-loaded through this guarded entry point).</p>
 */
public final class DuelHitSoundService {

    private DuelHitSoundService() {
    }

    /** Registers the sound swap when ProtocolLib is present; a silent no-op otherwise. */
    public static void register(org.bukkit.plugin.Plugin plugin,
                                com.rumilance.practice.match.MatchRegistry matchRegistry) {
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            return;
        }
        try {
            DuelHitSoundPackets.register(plugin, matchRegistry);
        } catch (Throwable t) {
            plugin.getLogger().warning("Duel i-frame hit-sound swap disabled: " + t);
        }
    }
}
