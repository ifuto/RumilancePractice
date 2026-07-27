package com.rumilance.practice.command;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.locale.MessageService;
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

    public LobbyCommand(
            LobbyService lobbyService,
            PlayerStateManager stateManager,
            SpectatorService spectatorService,
            FfaService ffaService,
            MessageService messageService
    ) {
        this.lobbyService = lobbyService;
        this.stateManager = stateManager;
        this.spectatorService = spectatorService;
        this.ffaService = ffaService;
        this.messageService = messageService;
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
        if (state == PlayerState.FFA || ffaService.isInFfa(player.getUniqueId())) {
            ffaService.leave(player);
            messageService.send(player, "ffa.left");
            return true;
        }
        lobbyService.sendToLobby(player);
        stateManager.resetToLobby(player.getUniqueId());
        messageService.send(player, "lobby.teleported");
        return true;
    }
}
