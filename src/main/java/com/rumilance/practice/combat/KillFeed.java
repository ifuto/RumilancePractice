package com.rumilance.practice.combat;

import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Global kill line: {@code ⚔ killer → victim (♥scaled/10)}. Names open that fighter's
 * stored end-of-match inventory when a {@code matchId} is provided.
 */
public final class KillFeed {

    @FunctionalInterface
    public interface InventoryOpener {
        void open(Player viewer, UUID matchId, UUID fighterId);
    }

    private static volatile InventoryOpener inventoryOpener;

    /** Plays the killer's selected (paid) kill effect at the victim's death position. */
    @FunctionalInterface
    public interface KillEffectPlayer {
        void play(Player killer, org.bukkit.Location victimLocation);
    }

    private static volatile KillEffectPlayer killEffectPlayer;

    private KillFeed() {
    }

    public static void setInventoryOpener(InventoryOpener opener) {
        inventoryOpener = opener;
    }

    public static void setKillEffectPlayer(KillEffectPlayer player) {
        killEffectPlayer = player;
    }

    public static void broadcast(Player killer, Player victim, TeamColor killerTeam) {
        if (killer == null || victim == null) {
            return;
        }
        broadcast(killer, victim, killerTeam, killer.getHealth(), maxHealth(killer), null);
    }

    public static void broadcast(Player killer, Player victim, TeamColor killerTeam, UUID matchId) {
        if (killer == null || victim == null) {
            return;
        }
        broadcast(killer, victim, killerTeam, killer.getHealth(), maxHealth(killer), matchId);
    }

    public static void broadcast(Player killer, Player victim, TeamColor killerTeam,
                                 double killerHealth, double killerMax) {
        broadcast(killer, victim, killerTeam, killerHealth, killerMax, null);
    }

    public static void broadcast(Player killer, Player victim, TeamColor killerTeam,
                                 double killerHealth, double killerMax, UUID matchId) {
        if (killer == null || victim == null) {
            return;
        }
        NamedTextColor killerColor = color(killerTeam, true);
        NamedTextColor victimColor = color(killerTeam, false);
        Bukkit.broadcast(line(killer, victim, killerColor, victimColor, killerHealth, killerMax, matchId));
    }

    public static Component line(Player killer, Player victim,
                                 NamedTextColor killerColor, NamedTextColor victimColor) {
        return line(killer, victim, killerColor, victimColor, killer.getHealth(), maxHealth(killer), null);
    }

    public static Component line(Player killer, Player victim,
                                 NamedTextColor killerColor, NamedTextColor victimColor,
                                 double killerHealth, double killerMax, UUID matchId) {
        Component killerName = clickableName(killer.getName(), killerColor, true, matchId, killer.getUniqueId());
        Component victimName = clickableName(victim.getName(), victimColor, false, matchId, victim.getUniqueId());
        // Fire the killer's selected paid kill effect at the victim's position. All kill paths
        // (solo/team match, FFA) funnel through this broadcast, so this is the single hook.
        KillEffectPlayer fx = killEffectPlayer;
        if (fx != null) {
            try {
                fx.play(killer, victim.getLocation());
            } catch (RuntimeException ignored) {
                // Cosmetics must never interfere with the kill flow.
            }
        }
        double scaled = scaledToTen(killerHealth, killerMax);
        return Component.text("⚔ ", NamedTextColor.WHITE)
                .append(killerName)
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(victimName)
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(Component.text("♥", NamedTextColor.RED))
                .append(Component.text(formatHp(scaled), NamedTextColor.WHITE))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text("10", NamedTextColor.WHITE))
                .append(Component.text(")", NamedTextColor.DARK_GRAY));
    }

    private static Component clickableName(String name, NamedTextColor color, boolean bold,
                                           UUID matchId, UUID playerId) {
        Component base = Component.text(name, color);
        if (bold) {
            base = base.decorate(TextDecoration.BOLD);
        }
        if (matchId == null || playerId == null) {
            return base;
        }
        return base
                .clickEvent(ClickEvent.runCommand("/matchinv " + matchId + " " + playerId))
                .hoverEvent(HoverEvent.showText(Component.text("Click to view end inventory", NamedTextColor.YELLOW)));
    }

    private static NamedTextColor color(TeamColor team, boolean killer) {
        if (team == TeamColor.RED) {
            return killer ? NamedTextColor.RED : NamedTextColor.BLUE;
        }
        if (team == TeamColor.BLUE) {
            return killer ? NamedTextColor.BLUE : NamedTextColor.RED;
        }
        return killer ? NamedTextColor.AQUA : NamedTextColor.RED;
    }

    public static double maxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0d : attr.getValue();
    }

    /** Remaining HP as if the fighter's max hearts were 10. */
    static double scaledToTen(double health, double max) {
        if (max <= 0.0d) {
            return 0.0d;
        }
        double scaled = (health / max) * 10.0d;
        return scaled < 0.0d ? 0.0d : scaled;
    }

    static String formatHp(double hp) {
        if (hp < 0.0d) {
            hp = 0.0d;
        }
        if (Math.abs(hp - Math.rint(hp)) < 0.05d) {
            return String.valueOf((int) Math.rint(hp));
        }
        return String.format(java.util.Locale.US, "%.1f", hp);
    }
}
