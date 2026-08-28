package com.rumilance.practice.report;

import com.rumilance.practice.database.repository.PlayerReportRepository;
import com.rumilance.practice.match.MatchActionRecorder;
import com.rumilance.practice.model.PlayerReport;
import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Player reporting workflow: resolves the last 1v1 opponent, enforces the two-slot limit
 * (max two pending reports against distinct targets), captures compressed movement evidence,
 * and lets staff list/dismiss reports (dismissal deletes the evidence file and frees a slot).
 */
public final class ReportService {

    /** Max distinct pending reports a single player may hold at once. */
    public static final int MAX_ACTIVE_REPORTS = 2;

    private final PlayerReportRepository repository;
    private final ReportEvidenceStore evidenceStore;
    private final MatchActionRecorder recorder;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;

    public ReportService(PlayerReportRepository repository, ReportEvidenceStore evidenceStore,
                         MatchActionRecorder recorder, AsyncExecutor asyncExecutor, Logger logger) {
        this.repository = repository;
        this.evidenceStore = evidenceStore;
        this.recorder = recorder;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    /** @return the most recent 1v1 opponent whose movement trace is still retained, if any. */
    public Optional<MatchActionRecorder.LastMatch> lastOpponent(UUID reporterId) {
        return recorder.lastMatch(reporterId);
    }

    public enum SubmitResult {
        SUBMITTED,
        NO_RECENT_MATCH,
        ALREADY_REPORTED_TARGET,
        SLOTS_FULL,
        ERROR
    }

    /**
     * Submits a report for the reporter's last opponent. Must be called on the main thread
     * (reads live movement frames); the file write + DB insert are dispatched asynchronously.
     */
    public SubmitResult submit(Player reporter, String reason) {
        MatchActionRecorder.LastMatch last = recorder.lastMatch(reporter.getUniqueId()).orElse(null);
        if (last == null || last.opponentId() == null) {
            return SubmitResult.NO_RECENT_MATCH;
        }
        List<PlayerReport> pending;
        try {
            pending = repository.findPendingByReporter(reporter.getUniqueId());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to read pending reports", e);
            return SubmitResult.ERROR;
        }
        for (PlayerReport report : pending) {
            if (report.targetUuid().equals(last.opponentId())) {
                return SubmitResult.ALREADY_REPORTED_TARGET;
            }
        }
        if (pending.size() >= MAX_ACTIVE_REPORTS) {
            return SubmitResult.SLOTS_FULL;
        }

        UUID reportId = UUID.randomUUID();
        UUID matchId = last.matchId();
        UUID targetId = last.opponentId();
        String targetName = nameOf(targetId);
        String reporterName = reporter.getName();
        String world = last.world();
        List<MatchActionRecorder.Frame> reporterFrames = recorder.framesOf(reporter.getUniqueId(), matchId);
        List<MatchActionRecorder.Frame> targetFrames = recorder.framesOf(targetId, matchId);

        ReportEvidence evidence = new ReportEvidence(matchId, world, last.kit(), last.mode(),
                reporter.getUniqueId(), reporterName, targetId, targetName,
                System.currentTimeMillis(), reporterFrames, targetFrames);
        PlayerReport report = new PlayerReport(reportId, reporter.getUniqueId(), reporterName,
                targetId, targetName, matchId, reason, last.kit(), last.mode(),
                PlayerReport.STATUS_PENDING, evidenceStore.pathFor(reportId).toString(), Instant.now());

        asyncExecutor.execute(() -> {
            try {
                evidenceStore.save(reportId, evidence);
                repository.insert(report);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to persist report evidence", e);
                evidenceStore.delete(reportId);
            }
        });
        return SubmitResult.SUBMITTED;
    }

    public List<PlayerReport> listPending() {
        try {
            return repository.findAllPending();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to list reports", e);
            return List.of();
        }
    }

    public Optional<PlayerReport> find(UUID reportId) {
        try {
            return repository.findById(reportId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public ReportEvidence loadEvidence(UUID reportId) {
        try {
            return evidenceStore.load(reportId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load evidence for report " + reportId, e);
            return null;
        }
    }

    /** Dismisses a report: deletes the DB row and its evidence file, freeing the reporter's slot. */
    public void dismiss(UUID reportId) {
        asyncExecutor.execute(() -> {
            try {
                repository.delete(reportId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to delete report row", e);
            }
            evidenceStore.delete(reportId);
        });
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }
}
