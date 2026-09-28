package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbe;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * クライアントがエンティティを殴った瞬間を捕捉する（クロスヘア照準攻撃）。
 * ここで条件を記録し、後続のダメージ成立＆速度パケットと突き合わせる。
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class AttackHookMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void kbprobe$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        KbProbe.onAttack(player, target);
    }
}
