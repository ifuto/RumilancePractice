package com.rumilance.practice.command;

import com.rumilance.practice.bootstrap.ServiceRegistry;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.font.IconFontService;
import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.resourcepack.ResourcePackService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Diagnostics for the resource-pack rank icons.
 *
 * <ul>
 *   <li>{@code /rankicon} — status: icon config, pack config, and every online player's
 *       client-side pack state (loaded / declined / pending).</li>
 *   <li>{@code /rankicon test} — renders all three glyphs (admin / VIP+ / VIP) into the
 *       sender's own chat + action bar. If the sender sees empty squares here, their client
 *       does not have the pack applied; if the icons render here but not in nametags, the
 *       problem is in the prefix layer, not the pack.</li>
 * </ul>
 */
public final class RankIconCommand implements CommandExecutor, TabCompleter {

    private final ServiceRegistry services;

    public RankIconCommand(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && "test".equalsIgnoreCase(args[0])) {
            test(sender);
            return true;
        }
        status(sender);
        return true;
    }

    private void status(CommandSender sender) {
        IconFontService icons = services.find(IconFontService.class).orElse(null);
        ConfigService config = services.get(ConfigService.class);
        sender.sendMessage(Component.text("— Rank icons —", NamedTextColor.GOLD));
        if (icons == null) {
            sender.sendMessage(Component.text("IconFontService not registered.", NamedTextColor.RED));
        } else {
            String configured = config.config().getString("icons.font", "default");
            sender.sendMessage(Component.text("icons.enabled: " + icons.enabled(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("icons.font: " + configured
                    + (icons.font() == null ? "  (glyphs merged into minecraft:default by the pack)" : ""),
                    NamedTextColor.GRAY));
            sender.sendMessage(sample("admin", config.config().getString("icons.glyphs.admin", "\uE001"), icons));
            sender.sendMessage(sample("vip", config.config().getString("icons.glyphs.vip", "\uE002"), icons));
            sender.sendMessage(sample("vip+", config.config().getString("icons.glyphs.vip-plus", "\uE003"), icons));
        }
        ResourcePackService packs = services.find(ResourcePackService.class).orElse(null);
        if (packs != null) {
            sender.sendMessage(Component.text("resource-pack.enabled: " + packs.enabled()
                    + "  required: " + packs.required(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("resource-pack.url: "
                    + config.config().getString("resource-pack.url", "(default)"), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("— Client pack state —", NamedTextColor.GOLD));
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean has = online.hasResourcePack();
            String state = has ? "LOADED" : String.valueOf(online.getResourcePackStatus());
            sender.sendMessage(Component.text(online.getName(), has ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(Component.text("  " + (has ? "pack applied" : "pack not applied (" + state + ")"),
                            has ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
    }

    private Component sample(String label, String glyph, IconFontService icons) {
        Component icon = icons.rankIcon(rankFor(label));
        // Exact JSON the client receives: proves which font id and which glyph codepoint
        // the server actually sends (expected: font "rumilance:icons", text = the U+E00X char).
        String json = GsonComponentSerializer.gson().serialize(icon);
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(icon)
                .append(Component.text("  U+"
                                + Integer.toHexString(codepoint(glyph)).toUpperCase(Locale.ROOT)
                                + "  " + json,
                        NamedTextColor.DARK_GRAY));
    }

    private static int codepoint(String glyph) {
        return (glyph == null || glyph.isEmpty()) ? 0 : glyph.codePointAt(0);
    }

    private static PlayerRank rankFor(String label) {
        return switch (label) {
            case "admin" -> PlayerRank.ADMIN;
            case "vip+" -> PlayerRank.VIP_PLUS;
            default -> PlayerRank.VIP;
        };
    }

    private void test(CommandSender sender) {
        IconFontService icons = services.find(IconFontService.class).orElse(null);
        if (icons == null || !icons.enabled()) {
            sender.sendMessage(Component.text("Icons are disabled (IconFontService).", NamedTextColor.RED));
            return;
        }
        Component admin = icons.rankIcon(PlayerRank.ADMIN);
        Component vipPlus = icons.rankIcon(PlayerRank.VIP_PLUS);
        Component vip = icons.rankIcon(PlayerRank.VIP);
        sender.sendMessage(Component.text("Icon test — you should see badges, not squares:",
                NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ADMIN   ", NamedTextColor.GRAY).append(admin).append(Component.text("  icon")));
        sender.sendMessage(Component.text("VIP+    ", NamedTextColor.GRAY).append(vipPlus).append(Component.text("  icon")));
        sender.sendMessage(Component.text("VIP     ", NamedTextColor.GRAY).append(vip).append(Component.text("  icon")));
        // Diagnostic probes — distinguish "font not loaded on the client" from
        // "font loaded but glyph providers missing".
        net.kyori.adventure.key.Key iconFont = net.kyori.adventure.key.Key.key("rumilance", "icons");
        String pua = new String(Character.toChars(0xE001));
        sender.sendMessage(Component.text("Probe A ", NamedTextColor.DARK_GRAY)
                .append(Component.text(pua).style(s -> s.font(iconFont)))
                .append(Component.text("  = icons font + U+E001 (expected: admin badge)", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Probe B ", NamedTextColor.DARK_GRAY)
                .append(Component.text("ABC").style(s -> s.font(iconFont)))
                .append(Component.text("  = icons font + ABC (normal letters = font MISSING on client; squares = font loaded, glyphs broken)", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Probe C ", NamedTextColor.DARK_GRAY)
                .append(Component.text(pua))
                .append(Component.text("  = default font + U+E001 (with the merged pack this shows the admin badge)", NamedTextColor.DARK_GRAY)));
        if (sender instanceof Player player) {
            player.sendActionBar(Component.empty()
                    .append(admin)
                    .append(Component.text(" vs ", NamedTextColor.GRAY))
                    .append(vipPlus)
                    .decoration(TextDecoration.BOLD, false));
        }
        sender.sendMessage(Component.text(
                "Squares here = your client has no pack. Icons here but squares on names = prefix layer issue.",
                NamedTextColor.DARK_GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("test") : List.of();
    }
}
