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
        // The per-instance copy of quantum:tick now lives under qbot_N:quantum/tick (the source
        // namespace is part of the instance path — see QuantumFunctionRegistry.install).
        return this.function("tick");
    }

    public String initFunction() {
        return this.namespace + ":instance_init";
    }

    /**
     * Id of an instance copy of a {@code quantum:}-namespace function. Instance copies are keyed
     * by source namespace + path ({@code qbot_N:quantum/<path>}), so both the tick driver and the
     * option/difficulty/toggle drivers must target exactly that form.
     */
    public String function(String path) {
        return this.namespace + ":quantum/" + path;
    }

    public String botHolder(String name) {
        return this.holderPrefix + name;
    }
}
