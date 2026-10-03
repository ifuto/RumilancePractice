package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbeScreens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * タイトル画面の右上に KB Probe ボタンを追加する。
 * K キーバインドだけだと気づきにくいので、目に見えるボタンも置く。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Invoker("addDrawableChild")
    protected abstract <T extends Element & Drawable> T invokeAddDrawableChild(T drawable);

    @Inject(method = "init", at = @At("TAIL"))
    private void kbprobe$addKbButton(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;
        int w = ((net.minecraft.client.gui.screen.Screen) (Object) this).width;
        ButtonWidget btn = ButtonWidget.builder(
                Text.literal("KB Probe"),
                button -> MinecraftClient.getInstance()
                        .setScreen(new KbProbeScreens.HomeScreen(self))
        ).dimensions(w - 104, 8, 96, 20).build();
        invokeAddDrawableChild(btn);
    }
}