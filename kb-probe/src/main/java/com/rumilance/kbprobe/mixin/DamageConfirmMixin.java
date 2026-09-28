package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「ダメージがサーバー側で成立した」ことを確認する。
 * 保護領域でスカった殴り（ダメージパケットが来ない殴り）を計測に使わせないための正面切符。
 * TAIL（バニラの被害アニメーション適用後）で呼ぶので、成立は確定済みと見なせる。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class DamageConfirmMixin {

    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void kbprobe$onDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        KbProbe.onDamageConfirmed(packet.entityId());
    }
}
