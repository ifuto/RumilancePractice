package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.FreeInventoryEdit;
import com.rumilance.practice.gui.GuiCloseHandler;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitStartEffect;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 試合開始時の付与エフェクト設定 — **ポーション投入式**(2026-09-28 刷新)。
 *
 * <p>旧来のパレット(一覧からエフェクトを選んでレベルを回す方式)は「時間を変えられない」
 * 問題が根本にあった。新UIは小さなチェストと同じ操作: プレイヤーは何らかのポーション
 * (通常/スプラッシュ/残留、クリエモードのブリューイング結果でOK)を上部の27スロットに
 * そのまま置き、**Save** を押すだけ。投入された各ポーションの
 * ベースエフェクト+カスタムエフェクト(種類・強さ・**残り時間**)がそのまま試合開始時の
 * 付与リスト({@link KitStartEffect#durationTicks} 付き)として保存される。
 * 重複する同じエフェクトは強い版・長い版が勝つ。ポーション以外のアイテムは
 * Save 時に持ち主へ返却される。GUI を閉じると未セーブ分は自動的に返却される
 * (アイテム消失事故防止)。</p>
 */
public final class KitStartEffectsGui extends AbstractGui implements FreeInventoryEdit, GuiCloseHandler {

    /** Deposit area: the whole 27-slot top band (rows 0-2), chest-style. */
    private static final int DEPOSIT_SLOTS = 27;
    private static final int HINT_SLOT = 4 + 3 * 9;     // (3,4) the what-goes-here book
    private static final int BACK_SLOT = 2 + 4 * 9;     // (4,2)
    private static final int SAVE_SLOT = 4 + 4 * 9;     // (4,4) hero
    private static final int CLEAR_SLOT = 6 + 4 * 9;    // (4,6)
    /** Bottom bar rows (rows 3+4) stay as cancelled GUI buttons. */
    private static final int CONTROL_FROM = DEPOSIT_SLOTS;

    private final KitService kitService;
    private BiConsumer<Player, String> returnTo = (p, kit) -> { };

    public KitStartEffectsGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_START_EFFECTS, 5, false);
        this.kitService = kitService;
    }

    public void setReturnTo(BiConsumer<Player, String> returnTo) {
        this.returnTo = returnTo == null ? (p, kit) -> { } : returnTo;
    }

    public void open(Player player, String kitId) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.POTION;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit() == null ? "" : session.selectedKit();
        return t(player, "gui.start-effects-title",
                com.rumilance.practice.locale.MessageService.tags("kit", kit)).color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        KitDefinition kit = kitOf(session);
        if (kit == null) {
            inventory.setItem(BACK_SLOT, ItemBuilder.action(UiTheme.BACK, t(player, "menu.back"), "back"));
            return;
        }
        // Pre-fill the chest with potions that mirror the kit's CURRENT configuration, so a
        // player who opens the screen and presses Save without touching anything ends up
        // exactly where they started (round-trip safe, durations included).
        if (!Boolean.TRUE.equals(session.get("deposit_seeded", Boolean.class))) {
            session.put("deposit_seeded", Boolean.TRUE);
            seedDeposit(kit, inventory);
        }
        // What-goes-here card in the centre of the control row.
        inventory.setItem(HINT_SLOT, ItemBuilder.of(Material.BOOK)
                .name(t(player, "gui.start-effects-hint").color(UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        t(player, "gui.start-effects-hint-lore-1").color(UiTheme.MUTED)
                                .decoration(TextDecoration.ITALIC, false),
                        t(player, "gui.start-effects-hint-lore-2").color(UiTheme.MUTED)
                                .decoration(TextDecoration.ITALIC, false))
                .action("decorate")
                .build());
        inventory.setItem(BACK_SLOT, ItemBuilder.action(UiTheme.BACK, t(player, "menu.back"), "back"));
        inventory.setItem(SAVE_SLOT, ItemBuilder.action(UiTheme.CONFIRM,
                t(player, "gui.start-effects-save"), "save"));
        inventory.setItem(CLEAR_SLOT, ItemBuilder.action(UiTheme.CLOSE,
                t(player, "gui.start-effects-clear"), "clear"));
    }

    /** Renders the kit's configured effects as potion items in the deposit band. */
    private void seedDeposit(KitDefinition kit, Inventory inventory) {
        int i = 0;
        for (KitStartEffect effect : kit.startEffects()) {
            if (i >= DEPOSIT_SLOTS) {
                break;
            }
            ItemStack potion = potionFor(effect);
            if (potion != null) {
                inventory.setItem(i++, potion);
            }
        }
    }

    /**
     * Rebuilds a representative splash potion for one configured effect: the base type picks
     * the colour family, and the effect itself rides along as a custom effect with its
     * exact amplifier + configured duration so a seed → save round-trip changes nothing.
     */
    private static ItemStack potionFor(KitStartEffect effect) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        if (potion.getItemMeta() instanceof PotionMeta meta) {
            org.bukkit.potion.PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE
                    .get(org.bukkit.NamespacedKey.minecraft(
                            com.rumilance.practice.util.SplashPotionDurations
                                    .normalizeKey(effect.potionEffectKey())));
            if (type == null) {
                return null;
            }
            int ticks = effect.durationTicks() >= 0
                    ? effect.durationTicks()
                    : com.rumilance.practice.util.SplashPotionDurations.ticks(type, effect.amplifier());
            PotionEffect custom = new PotionEffect(type, Math.max(1, ticks), effect.amplifier());
            meta.addCustomEffect(custom, true);
            meta.displayName(Component.text(type.getKey().getKey().toUpperCase(java.util.Locale.ROOT)
                                    + " Lv" + (effect.amplifier() + 1), UiTheme.SECONDARY)
                            .decoration(TextDecoration.ITALIC, false));
            potion.setItemMeta(meta);
        }
        return potion;
    }

    // ------------------------------------------------------------------ FreeInventoryEdit

    @Override
    public boolean isFreeEditActive(GuiSession session) {
        return true; // the deposit band is always a chest-like free area
    }

    @Override
    public boolean isControlSlot(GuiSession session, int topSlot) {
        return topSlot >= CONTROL_FROM;
    }

    @Override
    public void persistFreeEdit(Player player, GuiSession session, Inventory top) {
        // Free-edit state lives INSIDE the top inventory itself; nothing to flush here —
        // persistence happens only through the explicit Save action.
    }

    @Override
    public void onGuiClose(Player player, GuiSession session, Inventory inventory,
                           InventoryCloseEvent.Reason reason) {
        // 閉じたら未セーブの投入物は持ち主へ返す — カーソルからもマウス下の現物も消えさせない。
        for (int i = 0; i < DEPOSIT_SLOTS && i < inventory.getSize(); i++) {
            ItemStack leftover = inventory.getItem(i);
            if (leftover == null || leftover.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(leftover);
            overflow.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        }
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || "decorate".equals(action)) {
            return;
        }
        if ("back".equals(action)) {
            String kitId = session.selectedKit();
            sounds.play(player, "gui-back");
            player.closeInventory();
            returnTo.accept(player, kitId);
            return;
        }
        KitDefinition kit = kitOf(session);
        if (kit == null) {
            return;
        }
        if ("clear".equals(action)) {
            for (int i = 0; i < DEPOSIT_SLOTS; i++) {
                inventory.setItem(i, null);
            }
            sounds.play(player, "cancel");
            return;
        }
        if ("save".equals(action)) {
            save(player, session, inventory, kit);
        }
    }

    /** Parses the deposit band into the kit's start-effect list and persists it. */
    private void save(Player player, GuiSession session, Inventory inventory, KitDefinition kit) {
        Map<String, KitStartEffect> merged = new LinkedHashMap<>();
        List<ItemStack> rejected = new ArrayList<>();
        for (int i = 0; i < DEPOSIT_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!isPotion(stack.getType()) || !(stack.getItemMeta() instanceof PotionMeta meta)) {
                rejected.add(stack.clone());
                continue;
            }
            for (PotionEffect effect : effectsOf(meta)) {
                KitStartEffect incoming = new KitStartEffect(
                        effect.getType().getKey().getKey(), effect.getAmplifier(),
                        Math.max(1, effect.getDuration()));
                KitStartEffect existing = merged.get(incoming.potionEffectKey());
                // Duplicate effect types: the stronger variant wins; at equal strength the
                // longer duration wins.
                if (existing == null
                        || incoming.amplifier() > existing.amplifier()
                        || (incoming.amplifier() == existing.amplifier()
                                && incoming.durationTicks() > existing.durationTicks())) {
                    merged.put(incoming.potionEffectKey(), incoming);
                }
            }
        }
        kitService.save(kit.toBuilder().startEffects(List.copyOf(merged.values())).build());
        // Rejected (non-potion) items come straight back; saved potions are consumed.
        for (ItemStack it : rejected) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(it);
            overflow.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        sounds.play(player, "save-done");
        player.sendMessage(t(player, "gui.start-effects-saved",
                com.rumilance.practice.locale.MessageService.tags(
                        "n", String.valueOf(merged.size()),
                        "rejected", String.valueOf(rejected.size()))));
        String kitId = session.selectedKit();
        player.closeInventory();
        returnTo.accept(player, kitId);
    }

    private static boolean isPotion(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
    }

    /** Base-type effects + admin/custom-brewed effects on the item. */
    private static List<PotionEffect> effectsOf(PotionMeta meta) {
        List<PotionEffect> out = new ArrayList<>();
        org.bukkit.potion.PotionType base = meta.getBasePotionType();
        if (base != null) {
            out.addAll(base.getPotionEffects());
        }
        if (meta.hasCustomEffects()) {
            out.addAll(meta.getCustomEffects());
        }
        return out;
    }

    private KitDefinition kitOf(GuiSession session) {
        return session.selectedKit() == null ? null : kitService.get(session.selectedKit()).orElse(null);
    }
}
