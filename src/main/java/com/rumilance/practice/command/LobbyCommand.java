package com.rumilance.practice.command;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.PlayerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class LobbyCommand implements CommandExecutor {

    private final LobbyService lobbyService;
    private final PlayerStateManager stateManager;
    private final SpectatorService spectatorService;
    private final FfaService ffaService;
    private final MessageService messageService;
    private final PracticeService practiceService;
    private com.rumilance.practice.queue.QueueCoordinator queueCoordinator;
    private com.rumilance.practice.bot.SwordBotService swordBotService;

    public LobbyCommand(
            LobbyService lobbyService,
            PlayerStateManager stateManager,
            SpectatorService spectatorService,
            FfaService ffaService,
            MessageService messageService,
            PracticeService practiceService
    ) {
        this.lobbyService = lobbyService;
        this.stateManager = stateManager;
        this.spectatorService = spectatorService;
        this.ffaService = ffaService;
        this.messageService = messageService;
        this.practiceService = practiceService;
    }

    /** Recovery-compatible overload without practice rooms. */
    public LobbyCommand(
            LobbyService lobbyService,
            PlayerStateManager stateManager,
            SpectatorService spectatorService,
            FfaService ffaService,
            MessageService messageService
    ) {
        this(lobbyService, stateManager, spectatorService, ffaService, messageService, null);
    }

    public void setQueueCoordinator(com.rumilance.practice.queue.QueueCoordinator queueCoordinator) {
        this.queueCoordinator = queueCoordinator;
    }

    public void setSwordBotService(com.rumilance.practice.bot.SwordBotService swordBotService) {
        this.swordBotService = swordBotService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state == PlayerState.FIGHTING || state == PlayerState.COUNTDOWN || state == PlayerState.PREPARING_MATCH) {
            player.sendMessage(Component.text("You cannot return to lobby during a match.", NamedTextColor.RED));
            return true;
        }
        if (state == PlayerState.SPECTATING) {
            spectatorService.leave(player);
            messageService.send(player, "ffa.left");
            return true;
        }
        if (queueCoordinator != null) {
            queueCoordinator.leave(player);
        }
        if (swordBotService != null && swordBotService.isFighting(player.getUniqueId())) {
            swordBotService.onPlayerQuit(player.getUniqueId());
        }
        if (state == PlayerState.FFA || ffaService.isInFfa(player.getUniqueId())) {
            if (ffaService.inCombat(player.getUniqueId())) {
                player.sendMessage(Component.text("You cannot return to lobby during combat.", NamedTextColor.RED));
                return true;
            }
            ffaService.leave(player);
            messageService.send(player, "ffa.left");
            return true;
        }
        if (practiceService != null && (state == PlayerState.PRACTICE_WAIT || state == PlayerState.PRACTICE_ACTIVE
                || practiceService.isInPractice(player.getUniqueId()))) {
            practiceService.leave(player, true);
            return true;
        }
        lobbyService.sendToLobby(player);
        stateManager.resetToLobby(player.getUniqueId());
        messageService.send(player, "lobby.teleported");
        return true;
    }
}
