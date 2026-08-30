package com.rumilance.practice.cosmetic.kill;

import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.settings.SettingsService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Plays a killer's selected {@link KillEffect} at their victim's death position. Kill effects
 * are fully paid: they require VIP+ (the effect is ignored for non-donors and for any effect
 * whose configuration has been removed). Animation is driven by a single lightweight repeating
 * task so several simultaneous FFA finishes stay cheap.
 */
public final class KillEffectService {

    private record Active(KillEffect effect, Location origin, int ticksLeft) {
    }

    private final Plugin plugin;
    private final SettingsService settingsService;
    private final RankService rankService;
    private final KillEffectRegistry registry;
    private final Deque<Active> active = new ArrayDeque<>();
    private BukkitTask task;

    public KillEffectService(Plugin plugin, SettingsService settingsService, RankService rankService,
                             KillEffectRegistry registry) {
        this.plugin = plugin;
        this.settingsService = settingsService;
        this.rankService = rankService;
        this.registry = registry;
    }

    public KillEffectRegistry registry() {
        return registry;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        active.clear();
    }

    /**
     * Plays the killer's currently selected kill effect (if any) at {@code victimLocation}.
     * No-op for non-donors or "None".
     */
    public void playOnKill(Player killer, Location victimLocation) {
        if (killer == null || victimLocation == null || victimLocation.getWorld() == null) {
            return;
        }
        // Fully paid: kill effects require VIP+.
        if (rankService == null || !rankService.isVipPlusOrAbove(killer)) {
            return;
        }
        String id = settingsService.get(killer).killEffect();
        KillEffect effect = registry.byId(id);
        if (effect == null || effect.isNone()) {
            return;
        }
        Location origin = victimLocation.clone();
        effect.play(origin);
        if (effect.durationTicks() > 1) {
            active.add(new Active(effect, origin, effect.durationTicks()));
        }
    }

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        int size = active.size();
        for (int i = 0; i < size; i++) {
            Active a = active.poll();
            if (a == null) {
                continue;
            }
            int elapsed = a.effect().durationTicks() - a.ticksLeft();
            a.effect().playTick(a.origin(), elapsed);
            int remaining = a.ticksLeft() - 1;
            if (remaining > 0) {
                active.add(new Active(a.effect(), a.origin(), remaining));
            }
        }
    }
}
