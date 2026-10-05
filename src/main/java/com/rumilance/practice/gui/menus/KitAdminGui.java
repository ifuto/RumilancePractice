package com.rumilance.practice.gui.menus;

import com.rumilance.practice.chat.PendingInput;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin kit management GUI ({@code /kit} with no args). Lists every kit; clicking one opens a
 * toggle panel for its settings (enable / adventure / ranked / pinned-arena / regen / food /
 * block-place / block-break / pearl / totem / shield-break). Changes are saved immediately.
 *
 * <p>All labels are localised via {@link MessageService} under the {@code admin-gui.*} keys
 * (Japanese for {@code ja_jp}, English fallback otherwise).</p>
 */
public final class KitAdminGui extends AbstractGui {

    /**
     * Admin kit labels follow the operator's {@code /lang} locale (English default).
     */

    private final KitService kitService;
    private final MessageService messageService;
    /** Supplies the saved arena template names for the arena-pin cycle button (wired at boot). */
    private java.util.function.Supplier<List<String>> arenaNames = List::of;
    private java.util.function.Consumer<Player> openPresetAdmin = p -> { };
    private java.util.function.BiConsumer<Player, String> openStartEffects = (p, kit) -> { };
    private java.util.function.BiConsumer<Player, String> openArenaSelect = (p, kit) -> { };
    private java.util.function.BiConsumer<Player, String> openBlockRules = (p, kit) -> { };
    private java.util.function.BiConsumer<Player, String> openItemRules = (p, kit) -> { };
    private java.util.function.BiConsumer<Player, String> openInnerKits = (p, kit) -> { };
    private java.util.function.BiConsumer<Player, String> openOfficialEditor = (p, kit) -> { };

    /** The same 41-slot editor, but its admin mode writes kits.yml instead of kit_layouts. */
    public void setOpenOfficialEditor(java.util.function.BiConsumer<Player, String> editor) {
        this.openOfficialEditor = editor == null ? (p, kit) -> { } : editor;
    }

    public KitAdminGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService, MessageService messageService) {
        super(registry, sounds, GuiType.KIT_ADMIN, 6, false);
        this.kitService = kitService;
        this.messageService = messageService;
    }

    public void setArenaNames(java.util.function.Supplier<List<String>> arenaNames) {
        this.arenaNames = arenaNames == null ? List::of : arenaNames;
    }

    public void setOpenPresetAdmin(java.util.function.Consumer<Player> openPresetAdmin) {
        this.openPresetAdmin = openPresetAdmin == null ? p -> { } : openPresetAdmin;
    }

    public void setOpenStartEffects(java.util.function.BiConsumer<Player, String> openStartEffects) {
        this.openStartEffects = openStartEffects == null ? (p, kit) -> { } : openStartEffects;
    }

    public void setOpenArenaSelect(java.util.function.BiConsumer<Player, String> openArenaSelect) {
        this.openArenaSelect = openArenaSelect == null ? (p, kit) -> { } : openArenaSelect;
    }

    public void setOpenBlockRules(java.util.function.BiConsumer<Player, String> openBlockRules) {
        this.openBlockRules = openBlockRules == null ? (p, kit) -> { } : openBlockRules;
    }

    public void setOpenItemRules(java.util.function.BiConsumer<Player, String> openItemRules) {
        this.openItemRules = openItemRules == null ? (p, kit) -> { } : openItemRules;
    }

    /** 中キット (inner kit) management screen of one kit. */
    public void setOpenInnerKits(java.util.function.BiConsumer<Player, String> openInnerKits) {
        this.openInnerKits = openInnerKits == null ? (p, kit) -> { } : openInnerKits;
    }

    /** Reopens the config panel for a kit (used when returning from Start Effects GUI). */
    public void openConfig(Player player, String kitId) {
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(t(player, "general.no-permission"));
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("view", "config");
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    /** Localised raw label string from {@code admin-gui.<key>}. */
    private String t(String locale, String key) {
        return messageService.localeService().rawMessage(locale, "admin-gui." + key);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.put("view", "list");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.BOOK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return messageService.render(player, "admin-gui.title");
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        String locale = messageService.resolveLocale(player);
        String view = session.get("view", String.class);
        if (view == null) {
            view = "list";
        }
        if ("config".equals(view) && session.selectedKit() != null
                && kitService.get(session.selectedKit()).isPresent()) {
            renderConfig(player, inventory, session, locale);
        } else {
            renderList(player, inventory, session, locale);
        }
    }

    private void renderList(Player player, Inventory inventory, GuiSession session, String locale) {
        // List only the top level; child kits are managed from their folder. Page instead of
        // silently truncating the list at 28 (the admin can turn ANY kit into a folder).
        List<KitDefinition> kits = kitService.topLevel();
        int perPage = MenuScaffold.gridPageSize();
        int page = Math.min(session.page(), Math.max(0, (kits.size() - 1) / perPage));
        for (int i = 0; i < perPage && page * perPage + i < kits.size(); i++) {
            inventory.setItem(MenuScaffold.gridSlot(i), kitIcon(kits.get(page * perPage + i), locale));
        }
        paintPaging(player, inventory, page, kits.size());
    }

    private ItemStack kitIcon(KitDefinition kit, String locale) {
        Material material = Material.matchMaterial(kit.icon());
        if (material == null || material.isAir()) {
            material = Material.DIAMOND_SWORD;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(kit.prettyDisplayName(),
                        kit.enabled() ? UiTheme.SUCCESS : UiTheme.DANGER)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text((kit.category() == com.rumilance.practice.model.KitCategory.SUB
                        ? "Sub Kits" : "Main Kits"),
                kit.category() == com.rumilance.practice.model.KitCategory.SUB
                        ? UiTheme.SECONDARY : UiTheme.SUCCESS).decoration(TextDecoration.ITALIC, false));
        lore.add(stateLine(t(locale, "enabled"), kit.enabled(), locale));
        lore.add(stateLine(t(locale, "adventure"), kit.forceAdventure(), locale));
        lore.add(stateLine(t(locale, "ranked"), kit.ranked(), locale));
        lore.add(stateLine("Crystal FFA", kit.crystalFfa(), locale));
        lore.add(Component.text(
                rawGui(locale, "gui.kit-admin-duel").replace("<arenas>", arenaSummary(kit.arenas(), locale)),
                UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(
                rawGui(locale, "gui.kit-admin-party").replace("<arenas>", arenaSummary(kit.partyArenas(), locale)),
                UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(rawGui(locale, "gui.kit-admin-shift-up"), UiTheme.MUTED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(rawGui(locale, "gui.kit-admin-shift-down"), UiTheme.MUTED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "select:" + kit.name());
        stack.setItemMeta(meta);
        return stack;
    }

    private void renderConfig(Player player, Inventory inventory, GuiSession session, String locale) {
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            renderList(player, inventory, session, locale);
            return;
        }
        inventory.setItem(GuiSlots.slot(0, 4), header(kit, locale));
        inventory.setItem(GuiSlots.slot(1, 2), toggle(t(locale, "enabled"), kit.enabled(), "toggle:enabled",
                kit.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, locale));
        inventory.setItem(GuiSlots.slot(1, 4), toggle(t(locale, "adventure"), kit.forceAdventure(), "toggle:adventure",
                kit.forceAdventure() ? Material.LIME_DYE : Material.GRAY_DYE, locale));
        inventory.setItem(GuiSlots.slot(1, 6), toggle(t(locale, "ranked"), kit.ranked(), "toggle:ranked",
                kit.ranked() ? Material.LIME_DYE : Material.GRAY_DYE, locale));
        // --- Main / Sub category tiles: which section of the pickers this kit appears in ---
        boolean isSub = kit.category() == com.rumilance.practice.model.KitCategory.SUB;
        inventory.setItem(GuiSlots.slot(1, 3), toggle("Main Kits", !isSub, "toggle:mainkit",
                Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, locale));
        inventory.setItem(GuiSlots.slot(1, 7), toggle("Sub Kits", isSub, "toggle:subkit",
                Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, locale));
        // --- Crystal FFA declaration: THE crystal FFA kit (exclusive across all kits) gets
        // --- the KIT1..9 variant editor; FFA spawns the player with the selected slot.
        inventory.setItem(GuiSlots.slot(1, 5), toggle("Crystal FFA", kit.crystalFfa(),
                "toggle:crystalffa", Material.END_CRYSTAL, locale));
        // --- row 2: shared contents + sub-menu + icon (children are ordinary kits) ---
        if (!kitService.isFolder(kit.name())) {
            inventory.setItem(GuiSlots.slot(2, 2), entry(Material.CHEST,
                    rawGui(locale, "gui.kit-admin-official"),
                    rawGui(locale, "gui.kit-admin-official-lore"), UiTheme.SUCCESS,
                    "open:official", locale));
        }
        if (!kit.isChild()) {
            inventory.setItem(GuiSlots.slot(2, 4), entry(Material.SHULKER_BOX,
                    rawGui(locale, "gui.kit-admin-innerkits"),
                    rawGui(locale, "gui.kit-admin-innerkits-lore"), UiTheme.SECONDARY,
                    "open:inner-kits", locale));
        }
        inventory.setItem(GuiSlots.slot(2, 6), entry(Material.ITEM_FRAME,
                rawGui(locale, "gui.kit-admin-icon"),
                rawGui(locale, "gui.kit-admin-icon-lore"), UiTheme.PRIMARY,
                "icon:held", locale));

        // --- row 3: rule groups live in dedicated sub-GUIs so nothing is crowded ---
        inventory.setItem(GuiSlots.slot(3, 2), entry(Material.STONE_PICKAXE,
                rawGui(locale, "gui.kit-admin-block-rules"),
                rawGui(locale, "gui.kit-admin-block-rules-lore"), UiTheme.PRIMARY, "open:block-rules", locale));
        inventory.setItem(GuiSlots.slot(3, 4), ItemBuilder.action(Material.GRASS_BLOCK,
                Component.text(rawGui(locale, "gui.kit-admin-arenas"), UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false), "open:arenas"));
        inventory.setItem(GuiSlots.slot(3, 6), entry(Material.GOLDEN_APPLE,
                rawGui(locale, "gui.kit-admin-item-rules"),
                rawGui(locale, "gui.kit-admin-item-rules-lore"), UiTheme.WARNING, "open:item-rules", locale));

        // --- row 4: preset & start effects ---
        inventory.setItem(GuiSlots.slot(4, 2), toggle(rawGui(locale, "gui.kit-admin-preset"), kit.presetEnabled(),
                "toggle:preset", Material.CHEST, locale));
        inventory.setItem(GuiSlots.slot(4, 4), GuiDecorator.button(Material.SPLASH_POTION,
                Component.text(t(locale, "start-effects")
                                + (kit.startEffects().isEmpty() ? "" : " (" + kit.startEffects().size() + ")"),
                        UiTheme.SECONDARY)
                        .decoration(TextDecoration.ITALIC, false), "open:start-effects"));
        inventory.setItem(GuiSlots.slot(4, 6), ItemBuilder.action(Material.NETHER_STAR,
                Component.text(rawGui(locale, "gui.kit-admin-preset-open"), UiTheme.SECONDARY), "open:preset"));
        // Heart indicator (♥ HP readout under the nametag). ON by default; the kit config
        // screen is where a kit opts out.
        inventory.setItem(GuiSlots.slot(5, 2), toggle("Heart Indicator", kit.heartIndicator(),
                "toggle:heartindicator",
                kit.heartIndicator() ? Material.LIME_DYE : Material.GRAY_DYE, locale));

        if (kit.isChild()) {
            inventory.setItem(GuiSlots.slot(5, 7), entry(Material.HOPPER,
                    rawGui(locale, "gui.kit-admin-unfile"),
                    rawGui(locale, "gui.kit-admin-unfile-lore"), UiTheme.WARNING,
                    "unfile", locale));
        }
        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.action(UiTheme.BACK,
                Component.text(t(locale, "back"), UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false), "back"));
    }

    private ItemStack header(KitDefinition kit, String locale) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(kit.prettyDisplayName(), UiTheme.SECONDARY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(rawGui(locale, "gui.kit-admin-rename-lore"), UiTheme.MUTED)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "rename:display");
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack toggle(String label, boolean state, String action, Material material, String locale) {
        return GuiDecorator.button(material,
                Component.text(label + ": ", UiTheme.MUTED)
                        .append(Component.text(state ? t(locale, "on") : t(locale, "off"),
                                state ? UiTheme.SUCCESS : UiTheme.DANGER))
                        .decoration(TextDecoration.ITALIC, false),
                action);
    }

    /** Spaced sub-GUI entry tile with a divider + hint lore. */
    private ItemStack entry(Material material, String name, String loreLine, net.kyori.adventure.text.format.TextColor color,
                            String action, String locale) {
        return ItemBuilder.of(material)
                .name(Component.text(name, color).decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.line(loreLine),
                        UiTheme.blank(),
                        UiTheme.hint(rawGui(locale, "menu.click")))
                .action(action)
                .build();
    }

    private Component stateLine(String label, boolean state, String locale) {
        return Component.text(label + ": ", UiTheme.MUTED)
                .append(Component.text(state ? t(locale, "on") : t(locale, "off"),
                        state ? UiTheme.SUCCESS : UiTheme.DANGER))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * The explicit "can break" exception list, edited with the block held in hand:
     * left-click adds it, right-click removes it, shift-click clears the whole list.
     */
    private ItemStack canBreakItem(KitDefinition kit, String locale) {
        List<Component> lore = new ArrayList<>();
        if (kit.canBreak().isEmpty()) {
            lore.add(Component.text(rawGui(locale, "gui.kit-admin-canbreak-empty"), UiTheme.MUTED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            int shown = 0;
            for (String material : kit.canBreak()) {
                if (shown++ >= 8) {
                    lore.add(Component.text("+ " + (kit.canBreak().size() - shown + 1) + " ...", UiTheme.MUTED)
                            .decoration(TextDecoration.ITALIC, false));
                    break;
                }
                lore.add(Component.text("- " + material, UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.text("", UiTheme.MUTED));
        lore.add(Component.text(rawGui(locale, "gui.kit-admin-canbreak-hint-1"), UiTheme.MUTED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(rawGui(locale, "gui.kit-admin-canbreak-hint-2"), UiTheme.MUTED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(rawGui(locale, "gui.kit-admin-canbreak-hint-3"), UiTheme.MUTED)
                .decoration(TextDecoration.ITALIC, false));
        return ItemBuilder.of(Material.STONE_PICKAXE)
                .name(Component.text(rawGui(locale, "gui.kit-admin-canbreak")
                        + (kit.canBreak().isEmpty() ? "" : " (" + kit.canBreak().size() + ")"),
                        UiTheme.SECONDARY).decoration(TextDecoration.ITALIC, false))
                .lore(lore.toArray(new Component[0]))
                .action("canbreak:edit")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || !player.hasPermission("rumilance.admin")) {
            return;
        }
        if ((action.equals("page:prev") || action.equals("page:next"))
                && "list".equals(session.get("view", String.class))) {
            session.setPage(Math.max(0, session.page()
                    + (action.equals("page:next") ? 1 : -1)));
            refresh(player, session, inventory);
            return;
        }
        if (action.equals("back")) {
            KitDefinition selected = kitService.get(session.selectedKit()).orElse(null);
            if (selected != null && selected.isChild()) {
                session.setNavigatingAway(true);
                openInnerKits.accept(player, selected.parent());
                return;
            }
            session.put("view", "list");
            session.setSelectedKit(null);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("select:")) {
            String kitName = action.substring("select:".length());
            session.setSelectedKit(kitName);
            session.put("view", "config");
            sounds.play(player, "kit-select");
            refresh(player, session, inventory);
            return;
        }
        if (action.equals("noop")) {
            return;
        }
        if (action.equals("open:start-effects")) {
            if (session.selectedKit() != null) {
                sounds.play(player, "gui-click");
                openStartEffects.accept(player, session.selectedKit());
            }
            return;
        }
        if (action.equals("open:arenas")) {
            if (session.selectedKit() != null) {
                sounds.play(player, "gui-click");
                openArenaSelect.accept(player, session.selectedKit());
            }
            return;
        }
        if (action.equals("open:block-rules")) {
            if (session.selectedKit() != null) {
                sounds.play(player, "gui-click");
                openBlockRules.accept(player, session.selectedKit());
            }
            return;
        }
        if (action.equals("open:item-rules")) {
            if (session.selectedKit() != null) {
                sounds.play(player, "gui-click");
                openItemRules.accept(player, session.selectedKit());
            }
            return;
        }
        if (action.equals("open:official")) {
            if (session.selectedKit() != null) {
                session.setNavigatingAway(true);
                openOfficialEditor.accept(player, session.selectedKit());
            }
            return;
        }
        if (action.equals("rename:display")) {
            String kitId = session.selectedKit();
            if (kitId == null) {
                return;
            }
            player.closeInventory();
            player.sendMessage(t(player, "gui.kit-admin-rename-prompt"));
            PendingInput.await(player, value -> {
                if (value != null && !value.isBlank() && !"cancel".equalsIgnoreCase(value.trim())) {
                    kitService.get(kitId).ifPresent(kit ->
                            kitService.save(kit.toBuilder().displayName(value.trim()).build()));
                }
                if (player.isOnline()) {
                    openConfig(player, kitId);
                }
            });
            return;
        }
        if (action.equals("icon:held")) {
            KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
            Material held = player.getInventory().getItemInMainHand().getType();
            if (kit != null && !held.isAir()) {
                kitService.save(kit.toBuilder().icon(held.name()).build());
                sounds.play(player, "select");
                refresh(player, session, inventory);
            } else {
                player.sendMessage(t(player, "gui.kit-admin-icon-hold"));
                sounds.play(player, "error");
            }
            return;
        }
        if (action.equals("unfile")) {
            KitDefinition child = kitService.get(session.selectedKit()).orElse(null);
            if (child != null && child.isChild() && kitService.setParent(child.name(), null)) {
                sounds.play(player, "select");
                player.sendMessage(t(player, "gui.kit-admin-unfiled"));
                session.put("view", "list");
                session.setSelectedKit(null);
                refresh(player, session, inventory);
            }
            return;
        }
        if (action.equals("open:inner-kits")) {
            if (session.selectedKit() != null) {
                sounds.play(player, "gui-click");
                openInnerKits.accept(player, session.selectedKit());
            }
            return;
        }
        if (action.equals("open:preset")) {
            sounds.play(player, "gui-click");
            openPresetAdmin.accept(player);
            return;
        }
        KitDefinition current = session.selectedKit() == null ? null
                : kitService.get(session.selectedKit()).orElse(null);
        if (current == null) {
            return;
        }
        KitDefinition updated = applyConfigChange(current, action);
        if (updated != null) {
            kitService.save(updated);
            if (updated.crystalFfa()) {
                // The crystal FFA declaration is exclusive: declaring this kit un-declares
                // every other kit (persisted too).
                kitService.clearCrystalFfaExcept(updated.name());
            }
            sounds.play(player, updated.equals(current) ? "gui-click" : "select");
            refresh(player, session, inventory);
        }
    }

    /** Toggles a single kit rule; shared with the block/item rule sub-GUIs. */
    public static KitDefinition applyConfigChange(KitDefinition kit, String action) {
        KitDefinition.Builder b = kit.toBuilder();
        return switch (action) {
            case "toggle:mainkit" -> b.category(com.rumilance.practice.model.KitCategory.MAIN).build();
            case "toggle:subkit" -> b.category(com.rumilance.practice.model.KitCategory.SUB).build();
            case "toggle:crystalffa" -> b.crystalFfa(!kit.crystalFfa()).build();
            case "toggle:enabled" -> b.enabled(!kit.enabled()).build();
            case "toggle:adventure" -> b.forceAdventure(!kit.forceAdventure()).build();
            case "toggle:ranked" -> b.ranked(!kit.ranked()).build();
            case "toggle:autoregen" -> b.naturalHealthRegen(!kit.naturalHealthRegen()).build();
            case "toggle:autofood" -> b.autoFood(!kit.autoFood()).build();
            case "toggle:blockplace" -> b.blockPlace(!kit.blockPlace()).build();
            case "toggle:blockbreak" -> b.blockBreak(!kit.blockBreak()).breakPlayerPlacedOnly(false).build();
            case "toggle:breakplayerplaced" -> {
                boolean next = !kit.breakPlayerPlacedOnly();
                yield b.breakPlayerPlacedOnly(next).blockBreak(false).build();
            }
            case "toggle:pearl" -> b.pearl(!kit.pearl()).build();
            case "toggle:totem" -> b.totem(!kit.totem()).build();
            case "toggle:swordshieldbreak" -> b.swordShieldBreak(!kit.swordShieldBreak()).build();
            case "toggle:preset" -> b.presetEnabled(!kit.presetEnabled()).build();
            case "toggle:bedexplosion" -> b.bedExplosion(!kit.bedExplosion()).build();
            case "toggle:heartindicator" -> b.heartIndicator(!kit.heartIndicator()).build();
            default -> null;
        };
    }

    private String rawGui(String locale, String key) {
        return messageService.localeService().rawMessage(locale, key);
    }

    private String arenaSummary(List<String> arenas, String locale) {
        if (arenas == null || arenas.isEmpty()) {
            return rawGui(locale, "gui.kit-admin-random");
        }
        if (arenas.size() == 1) {
            return arenas.getFirst();
        }
        return arenas.size() + " maps";
    }

    /** @deprecated replaced by {@link KitArenaSelectGui} */
    @SuppressWarnings("unused")
    private String nextArena(String current) {
        List<String> names = arenaNames.get();
        if (names.isEmpty()) {
            return "";
        }
        if (current == null || current.isBlank()) {
            return names.get(0);
        }
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(current)) {
                return i + 1 < names.size() ? names.get(i + 1) : "";
            }
        }
        return "";
    }

    /**
     * ClickType-aware overload: in the kit list, Shift+left moves the kit up in the display
     * order and Shift+right moves it down (persisted to kits.yml as {@code kit-order}).
     * All other clicks fall through to the simple handler.
     */
    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType click) {
        if (action != null && action.startsWith("select:")
                && click == org.bukkit.event.inventory.ClickType.RIGHT
                && player.hasPermission("rumilance.admin")) {
            session.setNavigatingAway(true);
            openInnerKits.accept(player, action.substring("select:".length()));
            return;
        }
        if (action != null && action.startsWith("select:") && click.isShiftClick()) {
            String kitName = action.substring("select:".length());
            boolean moved = kitService.move(kitName,
                    click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT);
            sounds.play(player, moved ? "gui-click" : "error");
            refresh(player, session, inventory);
            return;
        }
        if ("canbreak:edit".equals(action) && session.selectedKit() != null) {
            KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
            if (kit == null) {
                return;
            }
            Material held = player.getInventory().getItemInMainHand().getType();
            boolean shift = click.isShiftClick();
            List<String> list = new ArrayList<>(kit.canBreak());
            if (shift) {
                list.clear();
            } else if (held == null || held.isAir() || !held.isBlock()) {
                sounds.play(player, "error");
                player.sendMessage(messageService.render(player, "gui.kit-admin-canbreak-hold")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            } else if (click == org.bukkit.event.inventory.ClickType.RIGHT) {
                list.removeIf(m -> m.equalsIgnoreCase(held.name()));
            } else {
                boolean present = list.stream().anyMatch(m -> m.equalsIgnoreCase(held.name()));
                if (!present) {
                    list.add(held.name());
                }
            }
            kitService.save(kit.toBuilder().canBreak(list).build());
            sounds.play(player, "select");
            refresh(player, session, inventory);
            return;
        }
        handleClick(player, session, inventory, slot, action);
    }
}
