package com.rumilance.practice.originalkit;

/**
 * Optional bridge for client mods. Default disabled — no fictional protocol.
 */
public interface ClientModBridge {

    boolean isEnabled();

    void notifyKitShare(java.util.UUID playerId, String shareCode);

    final class NoOp implements ClientModBridge {
        public static final NoOp INSTANCE = new NoOp();

        private NoOp() {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void notifyKitShare(java.util.UUID playerId, String shareCode) {
            // intentionally empty
        }
    }
}
