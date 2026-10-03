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
 * クライアント側が間で重力/摩擦をシミュレーションするので、ローカルのエンティティ実体ではなく
 * このパケット値こそが「サーバーが決めた正しい速度」。前回値との差分が実際に掛かった衝撃。
 *
 * 1.21.11: getVelocityX/Y/Z() は廃止。getVelocity() が Vec3d を返す
 * (内部で int/8000.0 して blocks/tick 単位)。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityCaptureMixin {

    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"))
    private void kbprobe$onVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        Vec3d vel = packet.getVelocity();
        // デバッグ: パケット生値とエンティティ現在速度を比較
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.world != null) {
            net.minecraft.entity.Entity entity = mc.world.getEntityById(packet.getEntityId());
            if (entity != null) {
                Vec3d entVel = entity.getVelocity();
                if (mc.player != null) {
                    mc.player.sendMessage(net.minecraft.text.Text.literal(
                            String.format("§8[KBProbe] pkt id=%d vel=(%.4f,%.4f,%.4f) ent=(%.4f,%.4f,%.4f) Δ=(%.4f,%.4f,%.4f)",
                                    packet.getEntityId(),
                                    vel.x, vel.y, vel.z,
                                    entVel.x, entVel.y, entVel.z,
                                    vel.x - entVel.x, vel.y - entVel.y, vel.z - entVel.z)),
                            true);
                }
            }
        }
        KbProbe.onVelocityPacket(packet.getEntityId(), vel.x, vel.y, vel.z);
    }
}