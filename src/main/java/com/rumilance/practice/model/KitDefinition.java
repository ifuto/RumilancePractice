package com.rumilance.practice.model;

import com.rumilance.practice.state.ArenaTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Full definition of a selectable practice kit, as configured in {@code kits.yml} or created
 * at runtime via {@code /kit create}. Defensive, immutable copies are taken of the collections.
 *
 * <p>Phase 3 extends the original Phase 1 shape (name/display/icon/ranked/ffa/health/items/armor)
 * with the full set of admin-configurable match rules exposed by {@code /kit}: enable state,
 * preferred arena terrain, auto-regen/auto-food/sword-shield-break toggles, block place/break
 * permissions (plus an explicit allow-list of breakable materials), ender pearl/totem permissions
 * and a per-kit match timeout override. Use {@link #builder()} / {@link #toBuilder()} rather than
 * the full canonical constructor when only a handful of fields need to change.</p>
 */
public record KitDefinition(
        String name,
        String displayName,
        String icon,
        boolean ranked,
        boolean ffaEnabled,
        double maxHealth,
        boolean naturalHealthRegen,
        double knockbackMultiplier,
        List<KitItemEntry> items,
        Map<String, String> armor,
        boolean enabled,
        ArenaTerrain arenaTerrain,
        boolean autoFood,
        boolean swordShieldBreak,
        boolean blockPlace,
        boolean blockBreak,
        List<String> canBreak,
        boolean pearl,
        boolean totem,
        boolean forceAdventure,
        int timeoutSeconds,
        String arenaName
) {

    /**
     * {@link #naturalHealthRegen()} doubles as the {@code /kit autoregen} toggle: when disabled,
     * {@code com.rumilance.practice.kit.KitListener} cancels {@code EntityRegainHealthEvent}s whose
     * {@code RegainReason} is {@code SATIATED} (regen from a full hunger bar) for players fighting
     * with this kit, while leaving other regen sources (golden apples, potions, ...) untouched.
     *
     * <p>{@link #arenaName()} pins the kit to ONE specific arena template (empty = any arena).
     * When set, matches with this kit always reserve that template; the legacy
     * {@link #arenaTerrain()} preference is ignored.</p>
     */
    public KitDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(arenaTerrain, "arenaTerrain");
        maxHealth = maxHealth <= 0 ? 20.0d : maxHealth;
        knockbackMultiplier = knockbackMultiplier <= 0 ? 1.0d : knockbackMultiplier;
        items = List.copyOf(items);
        armor = Map.copyOf(armor);
        canBreak = List.copyOf(canBreak);
        timeoutSeconds = Math.max(0, timeoutSeconds);
        arenaName = arenaName == null ? "" : arenaName;
    }

    /** @return true when this kit is pinned to one specific arena template. */
    public boolean hasFixedArena() {
        return arenaName != null && !arenaName.isBlank();
    }

    public static KitDefinition simple(String name, String displayName, String icon) {
        return builder(name).displayName(displayName).icon(icon).build();
    }

    /**
     * Display name for GUIs. When the configured display name is "just the id" (same text up
     * to case, with underscores/spaces interchangeable), underscores become spaces and the
     * configured case style applies — e.g. {@code "no_debuff"} renders as {@code "No Debuff"}.
     * Genuinely custom display names (MiniMessage markup or different wording) are untouched.
     */
    public String prettyDisplayName() {
        boolean looksLikeId = !displayName.contains("<")
                && displayName.replace(' ', '_').equalsIgnoreCase(name);
        if (!looksLikeId) {
            return displayName;
        }
        // Pass the display name (not the lowercased id) so KEEP style retains original casing.
        return com.rumilance.practice.util.KitNames.pretty(displayName);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return {@code true} if {@code material} (a Bukkit {@code Material} name) may be broken
     * even when {@link #blockBreak()} is disabled for this kit.
     */
    public boolean isExplicitlyBreakable(String material) {
        return canBreak.stream().anyMatch(m -> m.equalsIgnoreCase(material));
    }

    /**
     * Fluent, defensively-copying builder for {@link KitDefinition}. Every setter returns
     * {@code this} so multiple kit-admin subcommands (icon/rename/enable/type/...) can each
     * apply a single targeted change on top of {@link KitDefinition#toBuilder()}.
     */
    public static final class Builder {
        private String name;
        private String displayName;
        private String icon = "STONE";
        private boolean ranked = true;
        private boolean ffaEnabled = true;
        private double maxHealth = 20.0d;
        private boolean naturalHealthRegen = true;
        private double knockbackMultiplier = 1.0d;
        private List<KitItemEntry> items = new ArrayList<>();
        private Map<String, String> armor = new java.util.LinkedHashMap<>();
        private boolean enabled = true;
        private ArenaTerrain arenaTerrain = ArenaTerrain.ANY;
        private boolean autoFood = false;
        private boolean swordShieldBreak = false;
        private boolean blockPlace = false;
        private boolean blockBreak = false;
        private List<String> canBreak = new ArrayList<>();
        private boolean pearl = true;
        private boolean totem = true;
        private boolean forceAdventure = false;
        private int timeoutSeconds = 0;
        private String arenaName = "";

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
            this.displayName = name;
        }

        private Builder(KitDefinition source) {
            this.name = source.name;
            this.displayName = source.displayName;
            this.icon = source.icon;
            this.ranked = source.ranked;
            this.ffaEnabled = source.ffaEnabled;
            this.maxHealth = source.maxHealth;
            this.naturalHealthRegen = source.naturalHealthRegen;
            this.knockbackMultiplier = source.knockbackMultiplier;
            this.items = new ArrayList<>(source.items);
            this.armor = new java.util.LinkedHashMap<>(source.armor);
            this.enabled = source.enabled;
            this.arenaTerrain = source.arenaTerrain;
            this.autoFood = source.autoFood;
            this.swordShieldBreak = source.swordShieldBreak;
            this.blockPlace = source.blockPlace;
            this.blockBreak = source.blockBreak;
            this.canBreak = new ArrayList<>(source.canBreak);
            this.pearl = source.pearl;
            this.totem = source.totem;
            this.forceAdventure = source.forceAdventure;
            this.timeoutSeconds = source.timeoutSeconds;
            this.arenaName = source.arenaName;
        }

        public Builder name(String value) {
            this.name = Objects.requireNonNull(value, "name");
            return this;
        }

        public Builder displayName(String value) {
            this.displayName = Objects.requireNonNull(value, "displayName");
            return this;
        }

        public Builder icon(String value) {
            this.icon = Objects.requireNonNull(value, "icon");
            return this;
        }

        public Builder ranked(boolean value) {
            this.ranked = value;
            return this;
        }

        public Builder ffaEnabled(boolean value) {
            this.ffaEnabled = value;
            return this;
        }

        public Builder maxHealth(double value) {
            this.maxHealth = value;
            return this;
        }

        public Builder naturalHealthRegen(boolean value) {
            this.naturalHealthRegen = value;
            return this;
        }

        public Builder knockbackMultiplier(double value) {
            this.knockbackMultiplier = value;
            return this;
        }

        public Builder items(List<KitItemEntry> value) {
            this.items = new ArrayList<>(Objects.requireNonNull(value, "items"));
            return this;
        }

        public Builder armor(Map<String, String> value) {
            this.armor = new java.util.LinkedHashMap<>(Objects.requireNonNull(value, "armor"));
            return this;
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder arenaTerrain(ArenaTerrain value) {
            this.arenaTerrain = Objects.requireNonNull(value, "arenaTerrain");
            return this;
        }

        public Builder autoFood(boolean value) {
            this.autoFood = value;
            return this;
        }

        public Builder swordShieldBreak(boolean value) {
            this.swordShieldBreak = value;
            return this;
        }

        public Builder blockPlace(boolean value) {
            this.blockPlace = value;
            return this;
        }

        public Builder blockBreak(boolean value) {
            this.blockBreak = value;
            return this;
        }

        public Builder canBreak(List<String> value) {
            this.canBreak = new ArrayList<>(Objects.requireNonNull(value, "canBreak"));
            return this;
        }

        public Builder addCanBreak(String material) {
            this.canBreak.add(Objects.requireNonNull(material, "material"));
            return this;
        }

        public Builder pearl(boolean value) {
            this.pearl = value;
            return this;
        }

        public Builder totem(boolean value) {
            this.totem = value;
            return this;
        }

        public Builder forceAdventure(boolean value) {
            this.forceAdventure = value;
            return this;
        }

        public Builder timeoutSeconds(int value) {
            this.timeoutSeconds = value;
            return this;
        }

        public Builder arenaName(String value) {
            this.arenaName = value == null ? "" : value;
            return this;
        }

        public KitDefinition build() {
            return new KitDefinition(
                    name, displayName, icon, ranked, ffaEnabled, maxHealth, naturalHealthRegen,
                    knockbackMultiplier, items, armor, enabled, arenaTerrain, autoFood,
                    swordShieldBreak, blockPlace, blockBreak, canBreak, pearl, totem, forceAdventure,
                    timeoutSeconds, arenaName
            );
        }
    }
}
