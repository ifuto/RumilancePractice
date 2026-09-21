package com.rumilance.practice.gsit;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player's own LuckPerms data has to look like for GSit to behave the way this server
 * wants — the pure decision behind {@link GsitPermissionService}.
 *
 * <p>The point of the whole bridge is that GSit's permissions normally arrive through a
 * <em>group</em>, so simply deleting the player's own nodes changes nothing (that is what the
 * old implementation did, which is why sitting stayed wide open). The working shape is the
 * opposite:</p>
 *
 * <ul>
 *   <li>the player always carries {@code -gsit.*} — an explicit deny that vetoes every GSit
 *       node inherited from any group, including wildcards;</li>
 *   <li>in the hub the player additionally carries {@code GSit.SitClick = true}. LuckPerms
 *       resolves the most specific node, so that exact grant beats the {@code gsit.*} deny and
 *       clicking a stair or slab sits you down — while {@code /sit}, crawling and the rest stay
 *       denied;</li>
 *   <li>outside the hub (a match, a queue, practice, the AFK room) the grant is denied
 *       explicitly too, so GSit is completely off during a fight.</li>
 * </ul>
 *
 * <p>Group inheritance nodes and unrelated permissions are never touched.</p>
 */
public final class GsitPolicy {

    /** The wildcard node used to veto everything GSit a group might grant. */
    public static final String WILDCARD = "gsit.*";
    /** Root node: {@code gsit} on its own. */
    public static final String ROOT = "gsit";

    private static final String PREFIX = "gsit.";

    /** Where the player is: the hub, or anywhere a fight can happen. */
    public enum Mode {
        /** In the hub — sitting by clicking a stair/slab is allowed. */
        LOBBY,
        /** Everywhere else — GSit is off completely. */
        MATCH
    }

    /**
     * One permission node the player owns in LuckPerms. The key never carries the {@code -}
     * prefix (that is what {@link #value} is for), but {@link #isGsitNode} tolerates it because
     * callers sometimes pass raw strings.
     */
    public record Owned(String key, boolean value) {

        public static Owned grant(String key) {
            return new Owned(key, true);
        }

        public static Owned deny(String key) {
            return new Owned(key, false);
        }
    }

    /** The edit to apply to the player's own data. */
    public record Plan(List<Owned> add, List<Owned> remove) {

        /** True when the player is already exactly in the wanted state — no write needed. */
        public boolean isNoop() {
            return add.isEmpty() && remove.isEmpty();
        }
    }

    private GsitPolicy() {
    }

    /**
     * True for every node this bridge owns: {@code gsit.*}, the bare {@code gsit} root, and any
     * negated variant. {@code group.gsit} / {@code inheritance.gsit} are NOT GSit nodes —
     * removing a group would change far more than sitting.
     */
    public static boolean isGsitNode(String key) {
        if (key == null) {
            return false;
        }
        String trimmed = key.trim();
        int from = 0;
        while (from < trimmed.length() && trimmed.charAt(from) == '-') {
            from++;
        }
        String plain = trimmed.substring(from);
        return plain.equalsIgnoreCase(ROOT)
                || plain.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /**
     * The wanted end state for one player.
     *
     * @param owned the player's own permission nodes (no inheritance, no meta)
     * @param grant the node that allows sitting by clicking ({@code GSit.SitClick})
     * @param mode  hub or fight
     */
    public static Plan plan(List<Owned> owned, String grant, Mode mode) {
        String grantKey = grant == null ? "" : grant.trim();
        List<Owned> add = new ArrayList<>();
        List<Owned> remove = new ArrayList<>();
        boolean wildcardDenied = false;
        boolean grantTrue = false;
        boolean grantFalse = false;

        for (Owned node : owned) {
            if (node == null || !isGsitNode(node.key())) {
                continue; // not ours: groups, meta and every other plugin stay untouched
            }
            String key = node.key().trim();
            String plain = stripNegation(key);
            if (plain.equalsIgnoreCase(WILDCARD)) {
                if (node.value()) {
                    remove.add(node); // a granted wildcard must go: it re-enables everything
                } else {
                    wildcardDenied = true;
                }
                continue;
            }
            if (plain.equalsIgnoreCase(grantKey)) {
                if (node.value()) {
                    grantTrue = true;
                } else {
                    grantFalse = true;
                }
                continue;
            }
            // Any other GSit node on the player is a leftover: drop it either way.
            remove.add(node);
        }

        if (!wildcardDenied) {
            add.add(Owned.deny(WILDCARD));
        }
        if (mode == Mode.LOBBY) {
            if (grantFalse) {
                remove.add(Owned.deny(grantKey));
            }
            if (!grantTrue) {
                add.add(Owned.grant(grantKey));
            }
        } else {
            if (grantTrue) {
                remove.add(Owned.grant(grantKey));
            }
            if (!grantFalse) {
                add.add(Owned.deny(grantKey));
            }
        }
        return new Plan(add, remove);
    }

    private static String stripNegation(String key) {
        int from = 0;
        while (from < key.length() && key.charAt(from) == '-') {
            from++;
        }
        return key.substring(from);
    }
}
