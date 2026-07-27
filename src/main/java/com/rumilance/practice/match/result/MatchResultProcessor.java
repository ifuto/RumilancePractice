package com.rumilance.practice.match.result;

import com.rumilance.practice.session.MatchSession;

import java.util.UUID;

/**
 * Processes a finished match. Ranked and unranked implementations must stay separated.
 */
public interface MatchResultProcessor {

    void process(MatchSession session, UUID winnerId, boolean draw) throws Exception;
}
