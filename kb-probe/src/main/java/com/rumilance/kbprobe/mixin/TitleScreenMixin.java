package com.rumilance.kbprobe.mixin;

import com.rumilance.kbprobe.KbProbeScreens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * タイトル画面の右上に KB Probe ボタンを追加する。
 * K キーバインドだけだと気づきにくいので、目に見えるボタンも置く。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    // Screen の protected addDrawableChild を Mixin 経由で使えるようにする
    @Shadow
    protected abstract <T extends Element & Drawable> T addDrawableChild(T drawable);

    // コンストラクタは Mixin では不要だが Screen の abstract 要件を満たす
    private TitleScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void kbprobe$addKbButton(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;
        addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("KB Probe"),
                        button -> MinecraftClient.getInstance()
                                .setScreen(new KbProbeScreens.HomeScreen(self))
                ).dimensions(this.width - 104, 8, 96, 20).build()
        );
    }
}