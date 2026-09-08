package com.rumilance.ac.mixin;

import com.rumilance.ac.digest.OutgoingDigest;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taps every outbound packet at the earliest sane point (HEAD of ClientConnection#send) so
 * the digest sees the same stream the NIC sees. Anything injected AFTER this tap diverges
 * from the digest; anything before it (same-JVM pre-hook) is the documented, universal
 * ceiling of every userland client anti-cheat.
 */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionSendMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At("HEAD"))
    private void rumilanceac$tapSend(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        OutgoingDigest.note(packet);
    }
}
