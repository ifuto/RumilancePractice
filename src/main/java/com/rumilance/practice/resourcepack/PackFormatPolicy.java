package com.rumilance.practice.resourcepack;

/**
 * Whether a client is new enough to read the pack we ship.
 *
 * <p>ViaVersion/ViaBackwards translate the <em>protocol</em> — blocks, items, entities — but
 * they never translate a resource pack. A pack built for the newest client is handed to older
 * clients byte for byte, and they answer with "made for a newer version / broken or
 * incompatible". Even when forced, assets written against the new asset layout (item model
 * definitions in particular) simply do not exist for the older client, so the custom-font
 * glyphs the UI relies on stay missing.</p>
 *
 * <p>So the honest options are: ship a per-version pack variant, or do not offer the pack to
 * clients that cannot read it. This class implements the second one; it is pure so the rule is
 * unit-testable and independent of how the protocol number was obtained.</p>
 *
 * <p>Protocol numbers are deliberately <b>not</b> hardcoded here — Minecraft renumbers them
 * every release and a stale table silently locks out players. The threshold lives in
 * {@code resource-pack.min-client-protocol} in config.yml, where an operator sets it to the
 * protocol of the version the pack was built for (0 disables the check entirely).</p>
 */
public final class PackFormatPolicy {

    /** Returned when the client's protocol could not be read. Treated as "do not block". */
    public static final int UNKNOWN = 0;

    private PackFormatPolicy() {
    }

    /**
     * @param clientProtocol protocol version reported by the client (or {@link #UNKNOWN})
     * @param minProtocol    minimum accepted protocol; {@code <= 0} disables the check
     * @return true when the pack may be sent to this client
     */
    public static boolean maySend(int clientProtocol, int minProtocol) {
        if (minProtocol <= 0) {
            return true;
        }
        if (clientProtocol == UNKNOWN) {
            // No data is not evidence of an old client — sending is the current behaviour.
            return true;
        }
        return clientProtocol >= minProtocol;
    }

    /** True when we know the client is too old for this pack. */
    public static boolean tooOld(int clientProtocol, int minProtocol) {
        return !maySend(clientProtocol, minProtocol);
    }
}
