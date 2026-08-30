package com.rumilance.practice.gui;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all practice GUIs. Labels come from {@link MessageService} / lang YAML.
 */
public abstract class AbstractGui {

    protected final GuiSessionRegistry registry;
    protected final SoundService sounds;
    protected final GuiType type;
    protected final int rows;
    protected final boolean rankedBorder;
    private PlayerStateManager stateManager;
    private MessageService messages;

    protected AbstractGui(GuiSessionRegistry registry, SoundService sounds, GuiType type, int rows, boolean rankedBorder) {
        this.registry = Objects.requireNonNull(registry);
        this.sounds = Objects.requireNonNull(sounds);
        this.type = type;
        this.rows = rows;
        this.rankedBorder = rankedBorder;
    }

    public GuiType type() {
        return type;
    }

    public void setStateManager(PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setMessages(MessageService messages) {
        this.messages = messages;
    }

    protected MessageService messages() {
        return messages;
    }

    /** Localised component for {@code key}, italic stripped for inventory titles/icons. */
    protected Component t(Player player, String key, TagResolver... resolvers) {
        if (messages == null) {
            return Component.text(key).decoration(TextDecoration.ITALIC, false);
        }
        return messages.render(player, key, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    /** Raw MiniMessage / plain string for lore lines that go through {@link UiTheme#line}. */
    protected String line(Player player, String key) {
        if (messages == null) {
            return key;
        }
        return messages.raw(player, key);
    }

    protected void paintNav(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.nav(inventory, session, t(player, "menu.close"), t(player, "menu.back"));
    }

    protected void paintPaging(Player player, Inventory inventory, int page, int totalItems) {
        int pageSize = MenuScaffold.gridPageSize();
        int pageCount = Math.max(1, (totalItems + pageSize - 1) / pageSize);
        MenuScaffold.pagingButtons(
                inventory,
                page,
                totalItems,
                t(player, "menu.page-prev"),
                t(player, "menu.page-next"),
                t(player, "menu.page-of", MessageService.tags(
                        "page", String.valueOf(page + 1),
                        "pages", String.valueOf(pageCount))),
                t(player, "menu.page-goto", MessageService.tags("page", String.valueOf(page))),
                t(player, "menu.page-goto", MessageService.tags("page", String.valueOf(page + 2))));
    }

    /** @return the number of inventory rows this menu uses. */
    public int rows() {
        return rows;
    }

    public final void open(Player player) {
        if (stateManager != null && stateManager.getState(player.getUniqueId()) == PlayerState.LOBBY) {
            try {
                stateManager.transition(player.getUniqueId(), PlayerState.OPENING_GUI);
            } catch (Exception ignored) {
                // keep opening
            }
        }
        GuiSession session = registry.open(player.getUniqueId(), type, rows);
        configureSession(session, player);
        PracticeGuiHolder holder = new PracticeGuiHolder(session.sessionId(), type, rows);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title(player, session));
        holder.bind(inventory);
        render(player, session, inventory);
        player.openInventory(inventory);
        sounds.play(player, "gui-open");
    }

    protected void configureSession(GuiSession session, Player player) {
        // subclasses override
    }

    protected abstract Component title(Player player, GuiSession session);

    protected abstract void render(Player player, GuiSession session, Inventory inventory);

    public final Component titlePublic(Player player, GuiSession session) {
        return title(player, session);
    }

    public final void renderPublic(Player player, GuiSession session, Inventory inventory) {
        inventory.clear();
        render(player, session, inventory);
    }

    /**
     * Simple click handler (no {@link org.bukkit.event.inventory.ClickType}). Menus can override
     * either this or the extended overload below; the default is a no-op so a menu that only
     * cares about click types doesn't have to implement both.
     */
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        // default: no-op — most menus override this, ClickType-aware menus override the overload.
    }

    /**
     * Extended click handler that also receives the Bukkit {@link org.bukkit.event.inventory.ClickType}.
     * The default delegates to {@link #handleClick}; menus that need to distinguish left from
     * right clicks (e.g. queue list with a right-click preview) can override this.
     */
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        handleClick(player, session, inventory, slot, action);
    }

    /**
     * Re-renders this menu in place (clears, then fills) without issuing a new open packet,
     * so toggling a setting or turning a page feels instant instead of re-opening the
     * inventory. Safe to call from {@link #handleClick} on the main thread.
     */
    protected void refresh(Player player, GuiSession session, Inventory inventory) {
        inventory.clear();
        render(player, session, inventory);
    }

    public boolean matches(UUID sessionId, GuiType type) {
        return this.type == type;
    }
}
