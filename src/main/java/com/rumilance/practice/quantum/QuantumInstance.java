package com.rumilance.practice.quantum;

import java.util.UUID;

/**
 * Runtime identity for one QuantumBOT fight.
 *
 * <p>The datapack was authored for one fake player and uses tags and scoreboard pseudo-players
 * as implicit globals. Each instance gets its own namespaced function copy and selector/score
 * identities so two fights never share that implicit state.</p>
 */
public record QuantumInstance(
        int number,
        UUID botUuid,
        UUID targetUuid,
        String namespace,
        String botTag,
        String targetTag,
        String participantTag,
        String entityTag,
        String holderPrefix
) {
    public String tickFunction() {
        return this.namespace + ":tick";
    }

    public String initFunction() {
        return this.namespace + ":instance_init";
    }

    public String botHolder(String name) {
        return this.holderPrefix + name;
    }
}
