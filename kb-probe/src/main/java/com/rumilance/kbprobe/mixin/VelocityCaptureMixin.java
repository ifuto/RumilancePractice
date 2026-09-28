package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * サーバーがブロードキャストする速度を HEAD で捕捉する。
 * クライアント側が間で重力/摩擦をシミュレーションするので、ローカルのエンティティ実体ではなく
 * このパケット値こそが「サーバーが決めた正しい速度」。前回値との差分が実際に掛かった衝撃。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityCaptureMixin {

    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"))
    private void kbprobe$onVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        KbProbe.onVelocityPacket(packet.getId(),
                packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }
}
