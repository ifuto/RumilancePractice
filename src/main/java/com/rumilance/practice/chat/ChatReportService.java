package com.rumilance.practice.chat;

import com.rumilance.practice.database.repository.ChatReportRepository;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat reports filed from the chat hover/click, plus the lookup a reviewer needs.
 *
 * <p>Filing stores only a reference (see {@link ChatReportRepository}); the reported text and
 * the surrounding lines are resolved from {@link ChatLogService} when the report is opened, so
 * what a reviewer sees is the live buffer rather than a snapshot. The buffer is finite, so an
 * old report may come back with {@code context} shorter than requested or empty — that is the
 * honest answer to "what was said around here" once the line has scrolled out.</p>
 */
public final class ChatReportService {

    /** Status values stored in the {@code status} column. */
    public static final String OPEN = "OPEN";
    public static final String HANDLED = "HANDLED";
    public static final String DISMISSED = "DISMISSED";

    /**
     * A report joined with whatever the chat buffer still knows about its line.
     * {@code line} is empty once the buffer has scrolled past it.
     */
    public record Report(long chatLineId, UUID reporterId, UUID reportedId, String reportedName,
                         long reportedTs, String status,
                         Optional<ChatLogService.ChatLine> line,
                         List<ChatLogService.ChatLine> context) {

        /** Wall-clock time the reported line was sent, or when it was filed if it is gone. */
        public Instant timestamp() {
            return line.map(l -> Instant.ofEpochMilli(l.timestampMillis()))
                    .orElseGet(() -> Instant.ofEpochMilli(reportedTs));
        }

        /** The reported text, or {@code null} when the buffer no longer holds the line. */
        public String text() {
            return line.map(ChatLogService.ChatLine::message).orElse(null);
        }
    }

    private final Plugin plugin;
    private final ChatReportRepository repository;
    private final ChatLogService log;
    /** chatLineId + reporter -> status, so a duplicate click does not double-file. */
    private final Map<String, String> known = new ConcurrentHashMap<>();

    public ChatReportService(Plugin plugin, ChatReportRepository repository, ChatLogService log) {
        this.plugin = plugin;
        this.repository = repository;
        this.log = log;
    }

    /** Lines of context pulled around a reported line. */
    public int contextBefore = 5;
    public int contextAfter = 5;

    /**
     * Files a report. Returns {@code false} when this reporter already flagged this exact line,
     * so spam-clicking one message cannot bury the queue.
     */
    public boolean report(long chatLineId, UUID reporterId, UUID reportedId, String reportedName) {
        String key = chatLineId + ":" + reporterId;
        if (known.putIfAbsent(key, OPEN) != null) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        async(() -> {
            try {
                repository.insert(chatLineId, reporterId, reportedId, reportedName, now);
            } catch (SQLException e) {
                plugin.getLogger().warning("[reports] insert failed: " + e.getMessage());
            }
        });
        return true;
    }

    /** Every stored report, newest first, with context resolved from the chat buffer. */
    public List<Report> recent(int limit) {
        if (repository == null) {
            return List.of();
        }
        try {
            List<Report> out = new java.util.ArrayList<>();
            for (ChatReportRepository.Row row : repository.findRecent(limit)) {
                out.add(join(row));
            }
            return List.copyOf(out);
        } catch (SQLException e) {
            plugin.getLogger().warning("[reports] load failed: " + e.getMessage());
            return List.of();
        }
    }

    public List<Report> open(int limit) {
        if (repository == null) {
            return List.of();
        }
        try {
            List<Report> out = new java.util.ArrayList<>();
            for (ChatReportRepository.Row row : repository.findOpen(limit)) {
                out.add(join(row));
            }
            return List.copyOf(out);
        } catch (SQLException e) {
            plugin.getLogger().warning("[reports] load failed: " + e.getMessage());
            return List.of();
        }
    }

    public void setStatus(long chatLineId, UUID reporterId, String status) {
        known.put(chatLineId + ":" + reporterId, status);
        if (repository == null) {
            return;
        }
        async(() -> {
            try {
                repository.setStatus(chatLineId, reporterId, status);
            } catch (SQLException e) {
                plugin.getLogger().warning("[reports] status failed: " + e.getMessage());
            }
        });
    }

    private Report join(ChatReportRepository.Row row) {
        Optional<ChatLogService.ChatLine> line = log.find(row.chatLineId());
        List<ChatLogService.ChatLine> context =
                line.isPresent() ? log.context(row.chatLineId(), contextBefore, contextAfter)
                        : List.of();
        return new Report(row.chatLineId(), row.reporterId(), row.reportedId(), row.reportedName(),
                row.reportedTs(), row.status(), line, context);
    }

    private void async(Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }
}
