package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * サーバーがブロードキャストする速度を HEAD で捕捉する。
 * パケット値を直接 KbProbe に渡す（entity.getVelocity() は使わない）。
 * 1.21.11: getVelocity() が Vec3d を返す（内部で int/8000.0 → blocks/tick）。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityCaptureMixin {

    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"))
    private void kbprobe$onVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        Vec3d vel = packet.getVelocity();
        KbProbe.onVelocityPacket(packet.getEntityId(), vel.x, vel.y, vel.z);
    }
}