package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.NameDisplay;
import com.rumilance.practice.util.SafeTeleport;
import com.rumilance.practice.util.SpawnFooting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin teleport browser: every arena template and FFA arena has a persisted "source"
 * position — the region it was pasted from / registered at. Clicking an entry teleports the
 * admin onto grounded footing at that source point (region center for templates, configured
 * spawn for FFA), so anchors can be inspected and edited even across worlds.
 */
public final class ArenaSourceGui extends AbstractGui {

    /** One clickable source landmark. */
    private record Entry(String id, String kindKey, String worldName, Location target, boolean enabled, String iconName) {
    }

    private final ArenaTemplateStore arenaTemplates;
    private final FfaService ffaService;

    public ArenaSourceGui(GuiSessionRegistry registry, SoundService sounds,
                          ArenaTemplateStore arenaTemplates, FfaService ffaService) {
        super(registry, sounds, GuiType.ARENA_SOURCE, 6, false);
        this.arenaTemplates = arenaTemplates;
        this.ffaService = ffaService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.ORANGE;
    }

    @Override
    protected Material titleIcon() {
        return Material.ENDER_PEARL;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.arena-source-title").color(UiTheme.SECONDARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        List<Entry> entries = collect();
        int pageSize = MenuScaffold.gridPageSize();
        int offset = session.page() * pageSize;
        int placed = 0;
        for (int i = offset; i < entries.size() && placed < pageSize; i++, placed++) {
            Entry entry = entries.get(i);
            inventory.setItem(MenuScaffold.gridSlot(placed), icon(player, entry, i));
        }
        if (entries.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.arena-source-empty").color(UiTheme.MUTED))
                            .action("decorate")
                            .build());
        }
        paintPaging(player, inventory, session.page(), entries.size());
        paintNav(player, session, inventory);
    }

    private ItemStack icon(Player player, Entry entry, int index) {
        Material material = entry.enabled() ? Material.ENDER_EYE : Material.ENDER_PEARL;
        Location target = entry.target();
        String coords = target == null ? "?"
                : target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ();
        return ItemBuilder.of(material)
                .name(Component.text(NameDisplay.pretty(entry.id()), UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.arena-source-kind"),
                                line(player, entry.kindKey())),
                        UiTheme.labelValue(line(player, "gui.arena-source-world"), entry.worldName()),
                        UiTheme.labelValue(line(player, "gui.arena-source-pos"), coords),
                        UiTheme.status(
                                target == null
                                        ? line(player, "gui.arena-source-offline")
                                        : (entry.enabled()
                                                ? line(player, "gui.arena-source-ready")
                                                : line(player, "gui.arena-source-disabled")),
                                target == null || !entry.enabled() ? UiTheme.DANGER : UiTheme.SUCCESS),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, target == null
                                ? "gui.arena-source-unloadable" : "menu.click"))
                )
                .glint(target != null && entry.enabled())
                .action("tp:" + index)
                .build();
    }

    private List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
        for (ArenaTemplate t : arenaTemplates.templates()) {
            World world = Bukkit.getWorld(t.world());
            Location target = null;
            if (world != null) {
                double x = (t.minX() + t.maxX()) / 2.0d + 0.5d;
                double z = (t.minZ() + t.maxZ()) / 2.0d + 0.5d;
                double y = t.maxY() + 1.0d;
                target = new Location(world, x, y, z);
                Location grounded = SpawnFooting.standClear(target);
                if (grounded != null) {
                    target = grounded;
                }
            }
            out.add(new Entry(t.name(), "gui.arena-source-kind-arena", t.world(), target, t.enabled(), t.iconMaterial()));
        }
        for (FfaService.FfaArena arena : ffaService.list()) {
            World world = Bukkit.getWorld(arena.world());
            Location target = null;
            if (world != null) {
                Location spawn = arena.spawn();
                target = spawn != null && spawn.getWorld() != null ? spawn.clone()
                        : new Location(world,
                                (arena.region().minX() + arena.region().maxX()) / 2.0d + 0.5d,
                                arena.region().maxY() + 1.0d,
                                (arena.region().minZ() + arena.region().maxZ()) / 2.0d + 0.5d);
                target.setWorld(world);
                Location grounded = SpawnFooting.standClear(target);
                if (grounded != null) {
                    target = grounded;
                }
            }
            out.add(new Entry(arena.id(), "gui.arena-source-kind-ffa", arena.world(), target, arena.enabled(), arena.iconMaterial()));
        }
        return out;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(session.page() - 1);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("page:next".equals(action)) {
            session.setPage(session.page() + 1);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("tp:")) {
            int index;
            try {
                index = Integer.parseInt(action.substring(3));
            } catch (NumberFormatException e) {
                return;
            }
            List<Entry> entries = collect();
            if (index < 0 || index >= entries.size()) {
                return;
            }
            Entry entry = entries.get(index);
            Location target = entry.target();
            if (target == null || target.getWorld() == null) {
                messages().send(player, "arena-source.world-missing",
                        Placeholder.unparsed("id", entry.id()),
                        Placeholder.unparsed("world", entry.worldName()));
                sounds.play(player, "gui-back");
                return;
            }
            sounds.play(player, "select");
            player.closeInventory();
            SafeTeleport.teleport(player, LocationUtil.safeTeleportLocation(target));
            messages().send(player, "arena-source.teleported",
                    Placeholder.unparsed("id", entry.id()));
        }
    }

    /** Convenience for opening via other menus (delegates to the registered final open). */
    public void openMenu(Player player) {
        open(player);
    }
}
