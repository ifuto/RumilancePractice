package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import com.rumilance.kbprobe.KbProbeMath;
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
        // 重要: 速度パケットの3成分は int (vel*8000)。そのまま渡すと ×8000 の値が
        // 外れ値ガード(>2.5)に全て捌かれ、modが一切計測不能になる (修正: 0.3.1)。
        KbProbe.onVelocityPacket(packet.getId(),
                KbProbeMath.unscaleVelocity(packet.getVelocityX()),
                KbProbeMath.unscaleVelocity(packet.getVelocityY()),
                KbProbeMath.unscaleVelocity(packet.getVelocityZ()));
    }
}
