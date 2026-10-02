package com.rumilance.practice.team;

import com.rumilance.practice.state.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages fake bot members in Party (Team) fights.
 * Bot UUIDs are randomly generated (never collide with real players).
 * The bot has no AI — it exists only to fill a roster slot.
 * MatchService must handle bot UUIDs (skip Bukkit.getPlayer, spawn a dummy entity).
 *
 * Usage: /team addbot → adds a NARENA BOT to the owner's side.
 */
public final class PartyBotService {

    /** Party ID → set of bot UUIDs currently in that party. */
    private final Map<UUID, java.util.Set<UUID>> partyBots = new ConcurrentHashMap<>();

    /** bot UUID → display name. */
    private final Map<UUID, String> botNames = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private int nextIndex = 1;

    public PartyBotService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return true if the UUID belongs to a party bot managed by this service.
     */
    public boolean isPartyBot(UUID uuid) {
        return botNames.containsKey(uuid);
    }

    /**
     * @return the display name of a party bot, or null if not a bot.
     */
    public String botName(UUID uuid) {
        return botNames.get(uuid);
    }

    /**
     * Adds a bot to the given party on the owner's side.
     * @return the bot's UUID, or null if no more bot slots available
     */
    public UUID addBot(Team team) {
        if (team == null) return null;
        java.util.Set<UUID> bots = partyBots.computeIfAbsent(team.id(), k -> ConcurrentHashMap.newKeySet());
        if (bots.size() >= 3) return null; // max 3 bots per party
        // Generate a truly random UUID — collision with a real player is astronomically unlikely.
        UUID botUuid = UUID.randomUUID();
        bots.add(botUuid);
        String name = "NARENA BOT" + (nextIndex > 1 ? " " + nextIndex : "");
        nextIndex++;
        botNames.put(botUuid, name);
        // Add to the team on the owner's side
        TeamColor ownerSide = team.sideOf(team.owner());
        team.addMember(botUuid, name, ownerSide);
        return botUuid;
    }

    /**
     * Removes all bots from a party.
     */
    public void removeAllBots(Team team) {
        if (team == null) return;
        java.util.Set<UUID> bots = partyBots.remove(team.id());
        if (bots != null) {
            for (UUID botUuid : bots) {
                team.removeMember(botUuid);
                botNames.remove(botUuid);
            }
        }
    }

    /**
     * Gets the bot UUIDs for a party.
     */
    public java.util.Set<UUID> botsOf(UUID partyId) {
        return partyBots.getOrDefault(partyId, java.util.Set.of());
    }
}