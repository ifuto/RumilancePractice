package com.rumilance.practice.replay;

import com.rumilance.practice.match.MatchActionRecorder.Frame;
import org.bukkit.entity.ArmorStand;

import java.util.List;
import java.util.UUID;

/** Mutable playback state for one operator viewing one report's replay. */
public final class ReplaySession {

    private static final double[] SPEEDS = {0.25, 0.5, 1.0, 2.0, 4.0};

    final UUID operator;
    final UUID reportId;
    final String world;
    final String reporterName;
    final String targetName;
    final List<Frame> reporterFrames;
    final List<Frame> targetFrames;

    ArmorStand reporterAvatar;
    ArmorStand targetAvatar;
    int taskId = -1;

    final double startTick;
    final double endTick;
    double playheadTick;
    boolean paused;
    int speedIndex = 2; // 1.0x

    ReplaySession(UUID operator, UUID reportId, String world, String reporterName, String targetName,
                  List<Frame> reporterFrames, List<Frame> targetFrames) {
        this.operator = operator;
        this.reportId = reportId;
        this.world = world;
        this.reporterName = reporterName;
        this.targetName = targetName;
        this.reporterFrames = reporterFrames;
        this.targetFrames = targetFrames;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (List<Frame> list : List.of(reporterFrames, targetFrames)) {
            if (!list.isEmpty()) {
                min = Math.min(min, list.get(0).tick());
                max = Math.max(max, list.get(list.size() - 1).tick());
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
