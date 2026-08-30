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
    private com.rumilance.practice.match.MatchService matchService;

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

    public void setMatchService(com.rumilance.practice.match.MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * Hub teleport + inventory only. Callers that already left a match/FFA must use this
     * instead of {@code /hub} so ENDING/FFA branches cannot re-enter those leave paths.
     */
    public void applyHub(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state == PlayerState.FIGHTING || state == PlayerState.COUNTDOWN
                || state == PlayerState.PREPARING_MATCH || state == PlayerState.FFA
                || state == PlayerState.SPECTATING || state == PlayerState.PRACTICE_WAIT
                || state == PlayerState.PRACTICE_ACTIVE) {
            return;
        }
        if (queueCoordinator != null) {
            try {
                queueCoordinator.leave(player);
            } catch (Exception ignored) {
            }
        }
        lobbyService.sendToLobby(player);
        if (stateManager.getState(player.getUniqueId()) != PlayerState.LOBBY) {
            stateManager.resetToLobby(player.getUniqueId());
        }
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
        if (state == PlayerState.FFA || ffaService.isInFfa(player.getUniqueId())) {
            ffaService.leave(player);
            messageService.send(player, "ffa.left");
            return true;
        }
        if (state == PlayerState.ENDING && matchService != null) {
            matchService.returnToLobby(player);
            messageService.send(player, "lobby.teleported");
            return true;
        }
        if (practiceService != null && (state == PlayerState.PRACTICE_WAIT || state == PlayerState.PRACTICE_ACTIVE
                || practiceService.isInPractice(player.getUniqueId()))) {
            practiceService.leave(player, true);
            if (stateManager.getState(player.getUniqueId()) != PlayerState.LOBBY) {
                stateManager.resetToLobby(player.getUniqueId());
                lobbyService.sendToLobby(player);
                messageService.send(player, "lobby.teleported");
            }
            return true;
        }
        lobbyService.sendToLobby(player);
        stateManager.resetToLobby(player.getUniqueId());
        messageService.send(player, "lobby.teleported");
        return true;
    }
}
