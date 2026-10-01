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
        // yarn 1.21.1 の実記述 (FabricMC/yarn refs/heads/1.21.1 で確認): entity id アクセサは
        // getEntityId() であり getId() は存在しない。getVelocityX/Y/Z は整ラweltasulation で
        // 分解した wire(vel*8000)/8000 の double を返すので、外部の unscale(int) は不要
        // （0.3.1 当時の int アクセサ想定を修正: 2026-10-01 CI で yarn コンパイル確認）。
        KbProbe.onVelocityPacket(packet.getEntityId(),
                packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }
}
