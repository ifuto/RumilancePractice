package com.rumilance.practice.gui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player active GUI session registry. Prevents stale GUI actions from applying.
 * Propagates {@code fromGameMenu}/{@code fromBattleMenu} flags so Esc/Close can return
 * players to the correct parent menu.
 */
public final class GuiSessionRegistry {

    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiSession open(UUID playerId, GuiType type, int rows) {
        GuiSession previous = sessions.get(playerId);
        GuiSession session = new GuiSession(UUID.randomUUID(), playerId, type, rows);
        if (type == GuiType.BATTLE_MENU) {
            if (previous != null && (previous.type() == GuiType.GAME_MENU || previous.fromGameMenu())) {
                session.setFromGameMenu(true);
            }
        } else if (type != GuiType.GAME_MENU && previous != null) {
            if (previous.type() == GuiType.BATTLE_MENU || previous.type() == GuiType.BOT_MENU
                    || previous.fromBattleMenu()) {
                session.setFromBattleMenu(true);
            }
            if (previous.fromGameMenu() || previous.type() == GuiType.GAME_MENU
                    || previous.type() == GuiType.BATTLE_MENU && previous.fromGameMenu()) {
                session.setFromGameMenu(true);
            }
            // Children of a battle menu opened from the game menu keep both flags.
            if (previous.fromGameMenu()) {
                session.setFromGameMenu(true);
            }
        }
        sessions.put(playerId, session);
        return session;
    }

    public Optional<GuiSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public boolean isCurrent(UUID playerId, UUID sessionId) {
        GuiSession session = sessions.get(playerId);
        return session != null && session.sessionId().equals(sessionId);
    }

    public void close(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clear() {
        sessions.clear();
    }
}
