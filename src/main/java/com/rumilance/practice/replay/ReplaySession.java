package com.rumilance.practice.replay;

import com.rumilance.practice.match.MatchActionRecorder.Frame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mutable playback state for one operator viewing one recorded match's replay. */
public final class ReplaySession {

    private static final double[] SPEEDS = {0.25, 0.5, 1.0, 2.0, 4.0};

    final UUID operator;
    final UUID matchId;
    final String world;

    /** Avatar state per recorded participant (player id -> frames/name/NPC handle). */
    static final class Avatar {
        final UUID playerId;
        final String name;
        final List<Frame> frames;
        ReplayNpcService.Avatar npc;

        Avatar(UUID playerId, String name, List<Frame> frames) {
            this.playerId = playerId;
            this.name = name;
            this.frames = frames;
        }
    }

    final List<Avatar> avatars = new ArrayList<>();
    final Map<UUID, Avatar> byPlayer = new HashMap<>();

    int taskId = -1;

    final double startTick;
    final double endTick;
    double playheadTick;
    boolean paused;
    int speedIndex = 2; // 1.0x

    ReplaySession(UUID operator, UUID matchId, String world, List<Avatar> avatars) {
        this.operator = operator;
        this.matchId = matchId;
        this.world = world;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (Avatar a : avatars) {
            this.avatars.add(a);
            this.byPlayer.put(a.playerId, a);
            if (!a.frames.isEmpty()) {
                min = Math.min(min, a.frames.get(0).tick());
                max = Math.max(max, a.frames.get(a.frames.size() - 1).tick());
            }
        }
        this.startTick = min == Double.MAX_VALUE ? 0 : min;
        this.endTick = max == Double.MIN_VALUE ? 0 : max;
        this.playheadTick = this.startTick;
    }

    double speed() {
        return SPEEDS[speedIndex];
    }

    void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
    }

    void seek(double deltaTicks) {
        playheadTick = Math.max(startTick, Math.min(endTick, playheadTick + deltaTicks));
    }

    void restart() {
        playheadTick = startTick;
        paused = false;
    }

    boolean finished() {
        return playheadTick >= endTick;
    }

    double progress() {
        double span = endTick - startTick;
        return span <= 0 ? 1.0 : (playheadTick - startTick) / span;
    }
}
