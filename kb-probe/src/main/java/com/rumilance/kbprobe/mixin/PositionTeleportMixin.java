package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 対象の「確定テレポート」（EntityPositionS2CPacket = サーバーが座標を絶対値で上書きする
 * パケット）を捕捉する。kill/respawn、TP、スキルワープ等で座標がジャンプすると、
 * 速度差分の基線が成立しないばかりか、KBが実際に「届かない」ので、それを KB 無効領域
 * と誤判定させないために保留ヒットを静かに破棄する。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PositionTeleportMixin {

    @Inject(method = "onEntityPosition", at = @At("HEAD"))
    private void kbprobe$onTeleport(EntityPositionS2CPacket packet, CallbackInfo ci) {
        KbProbe.onEntityTeleported(packet.entityId());
    }
}
