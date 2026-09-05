package com.rumilance.practice.replay;

/**
 * Arena placement captured at match end so the replay viewer can re-paste the arena.
 * Disposable arena copies are filled with air on release, so without this the operator
 * would replay into an empty void. Only relocated (pasted) copies need re-pasting;
 * in-place arenas ({@code relocated=false}) still exist in the world afterwards.
 */
public record ReplayArenaSnapshot(
        String templateName,
        String world,
        String schematicPath,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        boolean relocated) {

    public boolean hasSchematic() {
        return schematicPath != null && !schematicPath.isBlank();
    }
}
