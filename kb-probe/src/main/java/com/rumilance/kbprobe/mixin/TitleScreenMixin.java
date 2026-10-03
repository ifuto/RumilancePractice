package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbeScreens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * タイトル画面の右上に KB Probe ボタンを追加する。
 * K キーバインドだけだと気づきにくいので、目に見えるボタンも置く。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void kbprobe$addKbButton(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;
        // 右上: 幅 96, 高さ 20, マージン 8
        self.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("KB Probe"),
                        button -> MinecraftClient.getInstance()
                                .setScreen(new KbProbeScreens.HomeScreen(self))
                ).dimensions(self.width - 104, 8, 96, 20).build()
        );
    }
}