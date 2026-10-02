package com.rumilance.kbprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry point: registers the home-screen keybind (default K, rebindable in
 * 設定 → 操作) and screens the player opens from there. Denken: 画面生成はキー入力
 * スレッドではなく END_CLIENT_TICK で行う（vanilla 画面遷移の安全箇所）。
 */
public final class KbProbeClientInit implements ClientModInitializer {

    private static KeyBinding homeKey;

    @Override
    public void onInitializeClient() {
        // 1.21.11: category は String → KeyBinding.Category.create(Identifier) に変更
        homeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kbprobe.home",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of("kbprobe", "kbprobe"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (homeKey.wasPressed()) {
                if (client.player != null) {
                    MinecraftClient.getInstance()
                            .setScreen(new KbProbeScreens.HomeScreen(null));
                }
            }
        });
    }
}
