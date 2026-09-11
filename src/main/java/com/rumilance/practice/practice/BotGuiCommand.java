package com.rumilance.practice.practice;

import com.rumilance.practice.gui.menus.PracticeBotSelectGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /bot} — opens the bot-select GUI directly (the same screen the practice menu shows).
 * A player already inside a practice session is bounced with a message instead.
 */
public final class BotGuiCommand implements CommandExecutor, TabCompleter {

    private final PracticeService practiceService;
    private final PracticeBotSelectGui botSelectGui;

    public BotGuiCommand(PracticeService practiceService, PracticeBotSelectGui botSelectGui) {
        this.practiceService = practiceService;
        this.botSelectGui = botSelectGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the bot screen.", NamedTextColor.RED));
            return true;
        }
        if (practiceService.session(player.getUniqueId()).isPresent()) {
            player.sendMessage(Component.text(
                    "練習中はBOT画面を開けません。先に退出してください。", NamedTextColor.RED));
            return true;
        }
        botSelectGui.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
