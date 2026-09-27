package com.rumilance.practice.shieldweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Token + LAN allowlist: the only doors into the Shield Web admin. */
class ShieldWebAuthTest {

    @TempDir
    Path dir;

    @Test
    void tokenIsGeneratedLoadedAndVerifiedConstantTime() throws Exception {
        Path file = dir.resolve("token.txt");
        ShieldWebAuth auth = new ShieldWebAuth(file);
        String token = auth.token();
        assertTrue(token.matches("[0-9a-f]{64}"), token);

        // A second instance over the same file must see the same token — /urank web and the
        // running server never disagree about the credential.
        ShieldWebAuth again = new ShieldWebAuth(file);
        assertEquals(token, again.token());

        assertTrue(auth.tokenMatches(token));
        assertTrue(auth.tokenMatches("  " + token + "  "));
        assertTrue(auth.tokenMatches("Bearer " + token));
        assertFalse(auth.tokenMatches(null));
        assertFalse(auth.tokenMatches(""));
        assertFalse(auth.tokenMatches("deadbeef"));
        assertFalse(auth.tokenMatches(token.substring(1)));
    }

    @Test
    void privateSourcesPassAndPublicOnesDoNot() throws Exception {
        assertTrue(ShieldWebAuth.isAllowedSource(InetAddress.getByName("127.0.0.1"), false));
        assertTrue(ShieldWebAuth.isAllowedSource(InetAddress.getByName("192.168.1.10"), false));
        assertTrue(ShieldWebAuth.isAllowedSource(InetAddress.getByName("10.0.0.5"), false));
        assertTrue(ShieldWebAuth.isAllowedSource(InetAddress.getByName("172.16.3.4"), false));
        // Tailscale CGNAT 100.64.0.0/10 — the range a funnel-enabled node administers from.
        assertTrue(ShieldWebAuth.isAllowedSource(InetAddress.getByName("100.64.0.7"), false));
        assertTrue(ShieldWebAuth.isAllowedSource(InetAddress.getByName("100.127.255.255"), false));
        assertTrue(ShieldWebAuth.isAllowedSource(
                InetAddress.getByName("fd7a:115c:a1e0::1"), false), "ULA");

        assertFalse(ShieldWebAuth.isAllowedSource(InetAddress.getByName("8.8.8.8"), false));
        assertFalse(ShieldWebAuth.isAllowedSource(InetAddress.getByName("100.63.255.255"), false),
                "just OUTSIDE the CGNAT block");
        assertFalse(ShieldWebAuth.isAllowedSource(InetAddress.getByName("100.128.0.1"), false),
                "just OUTSIDE the CGNAT block");
        assertFalse(ShieldWebAuth.isAllowedSource(null, false));

        // The documented escape hatch.
        assertTrue(ShieldWebAuth.isAllowedSource(InetAddress.getByName("8.8.8.8"), true));
    }
}
