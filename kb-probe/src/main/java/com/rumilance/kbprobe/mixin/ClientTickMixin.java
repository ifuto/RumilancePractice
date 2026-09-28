package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * クライアント tick 駆動: 保留ヒットのタイムアウト判定
 * （ダメージ不成立 / KB無効領域の判定）とワールド移動時の状態リセット。
 */
@Mixin(MinecraftClient.class)
public abstract class ClientTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void kbprobe$tick(CallbackInfo ci) {
        KbProbe.onClientTick();
    }
}
