package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 対象エンティティの除去（卸載・切断・リスポーン置き換え）を捕捉する。
 * 除去された相手には以後どの速度パケットも届かないので、KB 無効領域の偽陽性を防ぐため
 * 保留ヒットを静かに破棄する。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class EntitiesDestroyMixin {

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"))
    private void kbprobe$onEntitiesDestroy(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        for (int entityId : packet.getEntityIds()) {
            KbProbe.onEntityRemoved(entityId);
        }
    }
}
