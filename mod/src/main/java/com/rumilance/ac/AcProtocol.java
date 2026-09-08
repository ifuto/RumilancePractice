package com.rumilance.ac;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Wire protocol on channel {@code rumilance:ac} between the server plugin and this mod.
 * Payloads are raw UTF-8 text (no length prefix) in both directions:
 *
 * <pre>
 *   S2C (server &rarr; mod):   HELLO|&lt;nonce&gt;
 *   C2S (mod &rarr; server):   VERIFY|2|&lt;modVersion&gt;|&lt;nonce&gt;|&lt;findingsCsvOr-&gt;|&lt;totalMods&gt;|&lt;ownJarSha256Or-&gt;
 * </pre>
 *
 * Design notes (informed by how BAC/CheatBreaker/Grim do and don't do it):
 * <ul>
 *   <li>The nonce is one-time, server-minted per challenge (join + rolling heartbeats).
 *       Echoing it proves a real round-trip through the mod at that moment — an unmodded
 *       client cannot answer, and an old answer cannot be replayed.</li>
 *   <li>Protocol 2 adds the SHA-256 of this mod's own jar. The server can pin accepted
 *       builds: forgery then requires shipping the byte-identical official jar, not just
 *       answering HELLO. (Documented limit: injected cheats can still RUN ALONGSIDE the
 *       official jar — that is the same ceiling every same-userland scanner has, from BAC
 *       to CheatBreaker. We raise the bar; we do not claim to remove it.)</li>
 *   <li>No obscurity: this protocol is public. Security rests on nonces + hash pinning +
 *       server-side judgment, never on secrecy of the format.</li>
 * </ul>
 */
public final class AcProtocol {

    public static final int PROTOCOL = 2;
    public static final Identifier CHANNEL = Identifier.of("rumilance", "ac");

    private AcProtocol() {
    }

    /** Custom payload wrapper so Fabric's typed networking carries our raw bytes. */
    public record AcPayload(byte[] data) implements CustomPayload {

        public static final Id<AcPayload> ID = new Id<>(CHANNEL);

        public static final PacketCodec<PacketByteBuf, AcPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBytes(value.data()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new AcPayload(bytes);
                });

        public static AcPayload of(String text) {
            return new AcPayload(text.getBytes(StandardCharsets.UTF_8));
        }

        public String text() {
            return new String(data, StandardCharsets.UTF_8);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
