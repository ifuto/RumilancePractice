package com.rumilance.ac;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RumilanceAC client entry point.
 *
 * <p>What this mod does: answers the server's per-join nonce on {@code rumilance:ac} with
 * its version plus the result of a blacklist-only cheat-client scan (see
 * {@link CheatModScanner} — Sodium, ShieldStats and every other legitimate mod are never
 * touched). What this mod deliberately does NOT do: scan memory, inspect packets, or alter
 * gameplay. It is an attestation + detection tripwire, and it is only mandatory for players
 * an operator explicitly flags via {@code /anticheat require}.</p>
 */
public final class RumilanceAcMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(AcProtocol.AcPayload.ID, AcProtocol.AcPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AcProtocol.AcPayload.ID, AcProtocol.AcPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> com.rumilance.ac.digest.OutgoingDigest.reset());

        ClientPlayNetworking.registerGlobalReceiver(AcProtocol.AcPayload.ID, (payload, context) -> {
            String text = payload.text();
            if (!text.startsWith("HELLO|")) {
                return; // not ours / unknown message — ignore silently
            }
            String nonce = text.substring("HELLO|".length());
            List<CheatModScanner.Finding> findings = CheatModScanner.scan();
            String flags = findings.isEmpty() ? "-"
                    : findings.stream().map(CheatModScanner.Finding::token)
                        .collect(Collectors.joining(","));
            String version = FabricLoader.getInstance()
                    .getModContainer("rumilance-ac")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("dev");
            long[] snap = com.rumilance.ac.digest.OutgoingDigest.snapshot();
            String reply = "VERIFY|2|" + version + "|" + nonce + "|" + flags + "|"
                    + CheatModScanner.totalModCount() + "|"
                    + JarFingerprint.ownJarSha256().orElse("-") + "|"
                    + snap[0] + "|" + Long.toHexString(snap[1]);
            context.responseSender().sendPacket(AcProtocol.AcPayload.of(reply));
        });
    }
}
