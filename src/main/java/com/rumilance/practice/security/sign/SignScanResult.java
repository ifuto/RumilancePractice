package com.rumilance.practice.security.sign;

/**
 * Outcome of scanning one sign's component tree. Severity increases from harmless plain text up
 * to a high-confidence malicious structure that warrants an automatic ban.
 */
public record SignScanResult(Severity severity, String reason) {

    public enum Severity {
        /** Nothing suspicious - allow. */
        NONE,
        /** Suspicious structure - cancel the edit and log it, but do not ban (avoid false bans). */
        SUSPICIOUS,
        /** High-confidence exploit structure - cancel, log, and auto-ban. */
        MALICIOUS
    }

    public static final SignScanResult CLEAN = new SignScanResult(Severity.NONE, "");

    public boolean isBlocked() {
        return severity != Severity.NONE;
    }

    public boolean isMalicious() {
        return severity == Severity.MALICIOUS;
    }

    /** @return the more severe of the two results (reason follows the winner). */
    public SignScanResult max(SignScanResult other) {
        if (other == null) {
            return this;
        }
        return other.severity.ordinal() > this.severity.ordinal() ? other : this;
    }
}
