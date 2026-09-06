package com.rumilance.practice.gsit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GSit permission bridge must leave every player with EXACTLY {@code GSit.SitClick}: no
 * {@code /sit} command, no crawling, no wildcard — and it must never touch group inheritance.
 */
class GsitPermissionServiceTest {

    private static final String GRANT = "GSit.SitClick";

    @Test
    void recognisesEveryGsitNodeShape() {
        assertTrue(GsitPermissionService.isGsitNode("gsit.*"));
        assertTrue(GsitPermissionService.isGsitNode("GSit.*"));
        assertTrue(GsitPermissionService.isGsitNode("gsit"));
        assertTrue(GsitPermissionService.isGsitNode("GSit.SitClick"));
        assertTrue(GsitPermissionService.isGsitNode("gsit.sit"));
        assertTrue(GsitPermissionService.isGsitNode("gsit.crawl"));
        assertTrue(GsitPermissionService.isGsitNode("-gsit.sit"));
        assertTrue(GsitPermissionService.isGsitNode("--gsit.sit"));
        assertTrue(GsitPermissionService.isGsitNode("-gsit"));
        assertTrue(GsitPermissionService.isGsitNode(" -GSit.Crawl "));
        assertTrue(GsitPermissionService.isGsitNode(" gsit.belt "));
        assertFalse(GsitPermissionService.isGsitNode(null));
        assertFalse(GsitPermissionService.isGsitNode(""));
        assertFalse(GsitPermissionService.isGsitNode("-"));
        assertFalse(GsitPermissionService.isGsitNode("gsitx"));
        assertFalse(GsitPermissionService.isGsitNode("rumilance.user"));
        // Groups are LuckPerms inheritance nodes, not GSit nodes: never strip a group.
        assertFalse(GsitPermissionService.isGsitNode("group.gsit"));
        assertFalse(GsitPermissionService.isGsitNode("inheritance.gsit"));
    }

    @Test
    void stripsTheWildcardAndGrantsOnlySitClick() {
        var plan = GsitPermissionService.plan(
                List.of("gsit.*", "rumilance.user", " essentials.home"), GRANT);
        assertEquals(List.of("gsit.*"), plan.remove());
        assertFalse(plan.granted());
        assertFalse(plan.isNoop());
    }

    @Test
    void stripsEveryIndividualGsitNodeExceptTheGrant() {
        var plan = GsitPermissionService.plan(
                List.of("gsit.sit", "GSit.Crawl", "gsit.belt", "GSit.SitClick", "gsit.clickblock"),
                GRANT);
        assertEquals(List.of("gsit.sit", "GSit.Crawl", "gsit.belt", "gsit.clickblock"), plan.remove());
        assertTrue(plan.granted());
    }

    @Test
    void keepsNegationsOutTooSoNothingCanReGrantTheRest() {
        var plan = GsitPermissionService.plan(List.of("-gsit.sit", "-gsit.*"), GRANT);
        assertEquals(List.of("-gsit.sit", "-gsit.*"), plan.remove());
        assertFalse(plan.granted());
    }

    @Test
    void noopWhenAlreadyInTheWantedState() {
        // A second join must not write to the database again.
        var plan = GsitPermissionService.plan(List.of("GSit.SitClick", "rumilance.user"), GRANT);
        assertTrue(plan.remove().isEmpty());
        assertTrue(plan.granted());
        assertTrue(plan.isNoop());

        var empty = GsitPermissionService.plan(List.of(), GRANT);
        assertFalse(empty.isNoop()); // the grant is still missing -> one write, then noop forever
        assertFalse(empty.granted());
    }

    @Test
    void aNegatedGrantIsReplacedByTheRealOne() {
        // "-GSit.SitClick" vetoes sitting: it has to go, and the positive node has to be added.
        var plan = GsitPermissionService.plan(List.of("-GSit.SitClick", "gsit.*"), GRANT);
        assertEquals(List.of("-GSit.SitClick", "gsit.*"), plan.remove());
        assertFalse(plan.granted());
    }

    @Test
    void leavesUnrelatedPermissionsAlone() {
        var plan = GsitPermissionService.plan(
                List.of("rumilance.admin", "group.builder", "essentials.sit", "sit.command"), GRANT);
        assertTrue(plan.remove().isEmpty());
        assertFalse(plan.granted());
    }
}
