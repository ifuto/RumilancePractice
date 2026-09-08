package com.rumilance.ac;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Known-cheat-client detector, engineered for ZERO false positives.
 *
 * <h3>Design rules (this is the "do not misdetect Sodium / Shield Stats" part)</h3>
 * <ul>
 *   <li><b>Blacklist-only.</b> A mod is never suspicious for being unknown. Performance and
 *       utility mods (Sodium, Lithium, Iris, FerriteCore, Krypton, EntityCulling,
 *       ImmediatelyFast, ShieldStats, voice chat, minimaps, OptiFine-shims, replay …)
 *       are simply invisible to this scanner — they are not in the signature table, end of
 *       story. There is <em>no</em> heuristic ("unknown mod = cheat"), no substring match,
 *       no package-prefix guess.</li>
 *   <li><b>Exact mod-id equality, case-folded.</b> An id like {@code meteor-client} only
 *       matches that exact id — never {@code meteorology-utils} or {@code real-wurst}
 *       style near-misses.</li>
 *   <li><b>Two independent signals form a verdict.</b> Signal A = exact mod id in
 *       FabricLoader's registry; signal B = presence of the client's entry class on the
 *       classpath (catches renamed jars). One signal alone ⇒ {@link Severity#SUSPECT}
 *       (reported, never acted on), both ⇒ {@link Severity#CONFIRMED}. A legit mod that
 *       coincidentally shares an id cannot also load the cheat client's own class.</li>
 *   <li>The signature table contains only publicly documented, self-identifying cheat
 *       clients distributed as Fabric mods. Injection-only clients provide neither signal
 *       and are honestly <em>not</em> detected — see class doc of the plugin side.</li>
 * </ul>
 */
public final class CheatModScanner {

    public enum Severity {
        SUSPECT, CONFIRMED
    }

    public record Finding(String displayName, Severity severity, boolean idSignal, boolean classSignal) {
        /** Stable short token sent to the server, e.g. {@code "meteor-client(CONFIRMED)"}. */
        public String token() {
            return displayName + "(" + severity + ")";
        }
    }

    private record Signature(String id, String entryClass, String display) {
    }

    /** Publicly documented cheat clients. Entries must NEVER include general-purpose mods. */
    private static final Signature[] SIGNATURES = {
            new Signature("meteor-client",
                    "meteordevelopment.meteorclient.MeteorClient", "meteor-client"),
            new Signature("wurst",
                    "net.wurstclient.WurstClient", "wurst"),
    };

    /** Scans the current mod registry + classpath. Returns findings (usually empty). */
    public static List<Finding> scan() {
        List<Finding> findings = new ArrayList<>();
        for (Signature signature : SIGNATURES) {
            boolean idHit = FabricLoader.getInstance()
                    .getModContainer(signature.id())
                    .map(ModContainer::getMetadata)
                    .isPresent();
            boolean classHit = false;
            try {
                Class.forName(signature.entryClass(), false,
                        CheatModScanner.class.getClassLoader());
                classHit = true;
            } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                // entry class absent — the expected outcome on clean clients
            }
            if (idHit && classHit) {
                findings.add(new Finding(signature.display(), Severity.CONFIRMED, true, true));
            } else if (idHit || classHit) {
                findings.add(new Finding(signature.display(), Severity.SUSPECT, idHit, classHit));
            }
        }
        return findings;
    }

    /** Total loaded mod count — sanity counter sent alongside findings (no names). */
    public static int totalModCount() {
        return FabricLoader.getInstance().getAllMods().size();
    }
}
