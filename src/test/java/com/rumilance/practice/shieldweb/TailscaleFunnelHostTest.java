package com.rumilance.practice.shieldweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Reading this node's public hostname out of {@code tailscale status --json}.
 *
 * <p>The hostname is what the pack URL is built from, so getting it wrong silently points every
 * client at a dead address — the server resolves no SHA-1, sends no pack, and the rank badges
 * render as empty squares. This pins the parsing down without needing Tailscale installed.</p>
 *
 * <p>Only {@link TailscaleFunnelService#selfDnsName(String)} is under test: everything else in
 * that class shells out to the {@code tailscale} binary.</p>
 */
final class TailscaleFunnelHostTest {

    private static String status(String selfDnsName) {
        return """
                {
                  "Version": "1.72.0",
                  "Self": {
                    "ID": "nAAAAAAAAAA",
                    "HostName": "server-pc",
                    "DNSName": "%s",
                    "OS": "linux"
                  },
                  "Peer": {
                    "nBBBBBBBBBB": {
                      "HostName": "laptop",
                      "DNSName": "laptop.tailnet.ts.net."
                    }
                  }
                }
                """.formatted(selfDnsName);
    }

    @Test
    void readsTheSelfHostnameAndStripsTheTrailingDot() {
        assertEquals("server-pc.tailnet.ts.net",
                TailscaleFunnelService.selfDnsName(status("server-pc.tailnet.ts.net.")));
    }

    @Test
    void worksWhenThereIsNoTrailingDot() {
        assertEquals("server-pc.tailnet.ts.net",
                TailscaleFunnelService.selfDnsName(status("server-pc.tailnet.ts.net")));
    }

    @Test
    void takesSelfAndNotAPeer() {
        // The peer block comes after Self and must never win, even though it matches too.
        String json = status("server-pc.tailnet.ts.net.");
        assertEquals("server-pc.tailnet.ts.net", TailscaleFunnelService.selfDnsName(json));
    }

    @Test
    void returnsNullWhenThereIsNoSelfBlock() {
        assertNull(TailscaleFunnelService.selfDnsName("{\"Peer\":{}}"));
        assertNull(TailscaleFunnelService.selfDnsName(""));
        assertNull(TailscaleFunnelService.selfDnsName("not json at all"));
    }

    @Test
    void returnsNullWhenSelfHasNoDnsName() {
        assertNull(TailscaleFunnelService.selfDnsName("{\"Self\":{\"HostName\":\"server-pc\"}}"));
    }

    @Test
    void returnsNullForAnEmptyDnsName() {
        assertNull(TailscaleFunnelService.selfDnsName(status("")));
        assertNull(TailscaleFunnelService.selfDnsName(status(".")));
    }

    @Test
    void handlesNullInput() {
        assertNull(TailscaleFunnelService.selfDnsName(null));
    }
}
