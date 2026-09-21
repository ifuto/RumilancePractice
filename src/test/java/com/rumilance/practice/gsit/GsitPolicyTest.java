package com.rumilance.practice.gsit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GSit bridge must leave every player with EXACTLY one GSit ability in the hub — click a
 * stair/slab to sit — and with none anywhere else. Everything GSit a group grants has to be
 * vetoed by an explicit deny, because deleting the player's own nodes changes nothing.
 */
class GsitPolicyTest {

    private static final String GRANT = "GSit.SitClick";

    private static final GsitPolicy.Owned wildcardDeny = GsitPolicy.Owned.deny(GsitPolicy.WILDCARD);
    private static final GsitPolicy.Owned grantTrue = GsitPolicy.Owned.grant(GRANT);
    private static final GsitPolicy.Owned grantFalse = GsitPolicy.Owned.deny(GRANT);

    // ------------------------------------------------------------------ node classification

    @Test
    void recognisesEveryGsitNodeShape() {
        assertTrue(GsitPolicy.isGsitNode("gsit.*"));
        assertTrue(GsitPolicy.isGsitNode("GSit.*"));
        assertTrue(GsitPolicy.isGsitNode("gsit"));
        assertTrue(GsitPolicy.isGsitNode("GSit.SitClick"));
        assertTrue(GsitPolicy.isGsitNode("gsit.sit"));
        assertTrue(GsitPolicy.isGsitNode("gsit.crawl"));
        assertTrue(GsitPolicy.isGsitNode("-gsit.sit"));
        assertTrue(GsitPolicy.isGsitNode("--gsit.sit"));
        assertTrue(GsitPolicy.isGsitNode("-gsit"));
        assertTrue(GsitPolicy.isGsitNode(" -GSit.Crawl "));
        assertTrue(GsitPolicy.isGsitNode(" gsit.belt "));
        assertFalse(GsitPolicy.isGsitNode(null));
        assertFalse(GsitPolicy.isGsitNode(""));
        assertFalse(GsitPolicy.isGsitNode("-"));
        assertFalse(GsitPolicy.isGsitNode("gsitx"));
        assertFalse(GsitPolicy.isGsitNode("rumilance.user"));
        // Groups are LuckPerms inheritance nodes, not GSit nodes: never strip a group.
        assertFalse(GsitPolicy.isGsitNode("group.gsit"));
        assertFalse(GsitPolicy.isGsitNode("inheritance.gsit"));
    }

    // ------------------------------------------------------------------ hub state

    @Test
    void aFreshPlayerGetsTheWildcardDenyAndTheGrant() {
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(), GRANT, GsitPolicy.Mode.LOBBY);
        assertEquals(List.of(wildcardDeny, grantTrue), plan.add());
        assertTrue(plan.remove().isEmpty());
        assertFalse(plan.isNoop());
    }

    @Test
    void groupInheritedPermissionsAreVetoedEvenWhenThePlayerOwnsNothing() {
        // The whole point of the rewrite: a group grants gsit.*, the player's own data is
        // empty, and the old implementation called that a no-op - so sitting stayed open.
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(), GRANT, GsitPolicy.Mode.LOBBY);
        assertTrue(plan.add().contains(wildcardDeny));
        assertFalse(plan.isNoop());
    }

    @Test
    void noopWhenAlreadyInTheWantedHubState() {
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(wildcardDeny, grantTrue), GRANT,
                GsitPolicy.Mode.LOBBY);
        assertTrue(plan.add().isEmpty());
        assertTrue(plan.remove().isEmpty());
        assertTrue(plan.isNoop());
    }

    @Test
    void aGrantedWildcardIsRemovedAndDenied() {
        GsitPolicy.Plan plan = GsitPolicy.plan(
                List.of(GsitPolicy.Owned.grant(GsitPolicy.WILDCARD)), GRANT, GsitPolicy.Mode.LOBBY);
        assertEquals(List.of(GsitPolicy.Owned.grant(GsitPolicy.WILDCARD)), plan.remove());
        assertEquals(List.of(wildcardDeny, grantTrue), plan.add());
    }

    @Test
    void leftoverGsitNodesAreRemovedAndUnrelatedOnesAreNot() {
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(
                GsitPolicy.Owned.grant("gsit.sit"),
                GsitPolicy.Owned.grant("GSit.Crawl"),
                GsitPolicy.Owned.grant("gsit.belt"),
                GsitPolicy.Owned.grant("rumilance.admin"),
                GsitPolicy.Owned.grant("group.builder"),
                GsitPolicy.Owned.grant("essentials.sit")), GRANT, GsitPolicy.Mode.LOBBY);
        assertEquals(List.of(
                GsitPolicy.Owned.grant("gsit.sit"),
                GsitPolicy.Owned.grant("GSit.Crawl"),
                GsitPolicy.Owned.grant("gsit.belt")), plan.remove());
        assertEquals(List.of(wildcardDeny, grantTrue), plan.add());
    }

    @Test
    void aNegatedGrantIsReplacedByTheRealOne() {
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(wildcardDeny, grantFalse), GRANT,
                GsitPolicy.Mode.LOBBY);
        assertEquals(List.of(grantFalse), plan.remove());
        assertEquals(List.of(grantTrue), plan.add());
    }

    // ------------------------------------------------------------------ match state

    @Test
    void outsideTheHubTheGrantIsDeniedExplicitly() {
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(wildcardDeny, grantTrue), GRANT,
                GsitPolicy.Mode.MATCH);
        assertEquals(List.of(grantTrue), plan.remove());
        assertEquals(List.of(grantFalse), plan.add());
        assertFalse(plan.isNoop());
    }

    @Test
    void matchStateIsStableOnceApplied() {
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(wildcardDeny, grantFalse), GRANT,
                GsitPolicy.Mode.MATCH);
        assertTrue(plan.isNoop());
    }

    @Test
    void returningToTheHubRestoresTheGrant() {
        GsitPolicy.Plan plan = GsitPolicy.plan(List.of(wildcardDeny, grantFalse), GRANT,
                GsitPolicy.Mode.LOBBY);
        assertEquals(List.of(grantFalse), plan.remove());
        assertEquals(List.of(grantTrue), plan.add());
    }

    // ------------------------------------------------------------------ convergence

    @Test
    void applyingThePlanTwiceConvergesToANoop() {
        // Simulates the reconciler: write the plan into the node list, plan again, and the
        // second pass must be a no-op for both modes - otherwise the bridge would rewrite
        // LuckPerms once a second forever.
        for (GsitPolicy.Mode mode : GsitPolicy.Mode.values()) {
            List<GsitPolicy.Owned> owned = new ArrayList<>(List.of(
                    GsitPolicy.Owned.grant("gsit.*"),
                    GsitPolicy.Owned.grant("gsit.crawl"),
                    GsitPolicy.Owned.grant("rumilance.admin")));
            for (int pass = 0; pass < 3; pass++) {
                GsitPolicy.Plan plan = GsitPolicy.plan(owned, GRANT, mode);
                if (pass > 0) {
                    assertTrue(plan.isNoop(), "pass " + pass + " of " + mode + " still writes: " + plan);
                }
                owned.removeAll(plan.remove());
                owned.addAll(plan.add());
            }
            assertTrue(owned.contains(wildcardDeny));
            assertEquals(mode == GsitPolicy.Mode.LOBBY, owned.contains(grantTrue));
            assertEquals(mode == GsitPolicy.Mode.MATCH, owned.contains(grantFalse));
            assertTrue(owned.contains(GsitPolicy.Owned.grant("rumilance.admin")));
            assertFalse(owned.contains(GsitPolicy.Owned.grant("gsit.crawl")));
        }
    }
}
