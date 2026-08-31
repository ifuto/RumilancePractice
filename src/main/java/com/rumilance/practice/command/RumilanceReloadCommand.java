package com.rumilance.practice.command;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.bootstrap.ServiceRegistry;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.cosmetic.kill.KillEffectRegistry;
import com.rumilance.practice.arrow.ArrowEffectService;
import com.rumilance.practice.ekit.EkitItems;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.kit.PresetItems;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.locale.LocaleService;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.scoreboard.ScoreboardConfig;
import com.rumilance.practice.scoreboard.ScoreboardService;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reloads every YAML configuration (config, kits, arenas, ffa, lobby, practice, sounds,
 * scoreboard, locales, arrow/kill effects, ekit, presets) and pushes the fresh values into the
 * services that cache them. Admin-only. Designed to be safe to call on a live server: each
 * service reload is isolated so one failing file never stops the rest.
 */
public final class RumilanceReloadCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public RumilanceReloadCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        List<String> done = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        // 1) Reload every YAML file from disk first (config.yml, kits.yml, ... scoreboard.yml).
        safe(done, failed, "config", () -> services.get(ConfigService.class).reload());

        // 2) Locales / messages (lang/*.yml) — MessageService reads through LocaleService.
        safe(done, failed, "locales", () -> services.get(LocaleService.class).reload());

        // 3) Push fresh config into every caching service.
        safe(done, failed, "sounds", () -> services.get(SoundService.class).reload());
        safe(done, failed, "lobby", () -> services.get(LobbyService.class).reload());
        safe(done, failed, "kits", () -> services.get(KitService.class).reload());
        safe(done, failed, "arenas", () -> {
            ArenaTemplateStore store = services.find(ArenaTemplateStore.class).orElse(null);
            if (store != null) {
                store.reload();
                services.find(ArenaService.class)
                        .ifPresent(a -> a.setTemplates(store.templates()));
            }
        });
        safe(done, failed, "ffa", () -> services.get(FfaService.class).reload());
        safe(done, failed, "practice", () -> services.find(PracticeService.class)
                .ifPresent(PracticeService::reload));
        safe(done, failed, "arrow-effects", () -> services.find(ArrowEffectService.class)
                .ifPresent(ArrowEffectService::reload));
        safe(done, failed, "kill-effects", () -> services.find(KillEffectRegistry.class)
                .ifPresent(KillEffectRegistry::reload));
        safe(done, failed, "ekit-items", () -> services.find(EkitItems.class)
                .ifPresent(EkitItems::reload));
        safe(done, failed, "preset-items", () -> services.find(PresetItems.class)
                .ifPresent(PresetItems::reload));
        safe(done, failed, "scoreboard", () -> services.find(ScoreboardService.class)
                .ifPresent(s -> s.reload(new ScoreboardConfig(services.get(ConfigService.class).scoreboard()))));

        sender.sendMessage(Component.text("Reloaded (" + done.size() + ") configs: "
                + String.join(", ", done), NamedTextColor.GREEN));
        if (!failed.isEmpty()) {
            sender.sendMessage(Component.text("Failed (" + failed.size() + "): "
                    + String.join(", ", failed) + " — check the console for details.",
                    NamedTextColor.RED));
        }
        return true;
    }

    private void safe(List<String> done, List<String> failed, String name, Runnable action) {
        try {
            action.run();
            done.add(name);
        } catch (Throwable t) {
            failed.add(name);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
