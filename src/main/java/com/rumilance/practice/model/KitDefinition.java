package com.rumilance.practice.model;

import com.rumilance.practice.util.KitNames;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record KitDefinition(String name, String displayName, String icon, boolean ranked, boolean ffaEnabled, double maxHealth, boolean naturalHealthRegen, double knockbackMultiplier, List<KitItemEntry> items, Map<String, String> armor, boolean enabled, boolean autoFood, boolean swordShieldBreak, boolean blockPlace, boolean blockBreak, boolean breakPlayerPlacedOnly, List<String> canBreak, boolean pearl, boolean totem, boolean forceAdventure, int timeoutSeconds, List<String> arenas, List<String> partyArenas, List<String> startCommands, List<KitStartEffect> startEffects, boolean presetEnabled) {
    public KitDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        maxHealth = maxHealth <= 0.0 ? 20.0 : maxHealth;
        knockbackMultiplier = knockbackMultiplier <= 0.0 ? 1.0 : knockbackMultiplier;
        items = List.copyOf(items);
        armor = Map.copyOf(armor);
        canBreak = List.copyOf(canBreak);
        timeoutSeconds = Math.max(0, timeoutSeconds);
        arenas = KitDefinition.normalizeArenas(arenas);
        partyArenas = KitDefinition.normalizeArenas(partyArenas);
        startCommands = List.copyOf(startCommands == null ? List.of() : startCommands);
        startEffects = List.copyOf(startEffects == null ? List.of() : startEffects);
    }

    private static List<String> normalizeArenas(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        ArrayList<String> out = new ArrayList<String>();
        for (String entry : raw) {
            String id;
            if (entry == null || entry.isBlank() || out.contains(id = entry.trim().toLowerCase(Locale.ROOT))) continue;
            out.add(id);
        }
        return List.copyOf(out);
    }

    public String arenaName() {
        return this.arenas.isEmpty() ? "" : this.arenas.getFirst();
    }

    public boolean hasFixedArena() {
        return !this.arenas.isEmpty();
    }

    public boolean usesAnyArena() {
        return this.arenas.isEmpty();
    }

    public boolean hasPartyArenaPool() {
        return !this.partyArenas.isEmpty();
    }

    public String partyArenaName() {
        return this.partyArenas.isEmpty() ? "" : this.partyArenas.getFirst();
    }

    public static KitDefinition simple(String name, String displayName, String icon) {
        return KitDefinition.builder(name).displayName(displayName).icon(icon).build();
    }

    public String prettyDisplayName() {
        boolean looksLikeId;
        boolean bl = looksLikeId = !this.displayName.contains("<") && this.displayName.replace(' ', '_').equalsIgnoreCase(this.name);
        if (!looksLikeId) {
            return this.displayName;
        }
        return KitNames.pretty((String)this.displayName);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public boolean isExplicitlyBreakable(String material) {
        return this.canBreak.stream().anyMatch(m -> m.equalsIgnoreCase(material));
    }

    public boolean blockInteract() {
        return this.blockPlace || this.blockBreak;
    }

    public boolean allowsBlockPlace() {
        return this.blockPlace || this.breakPlayerPlacedOnly;
    }

    public boolean allowsBlockBreak(boolean playerPlaced) {
        if (this.breakPlayerPlacedOnly) {
            return playerPlaced;
        }
        return this.blockBreak;
    }

    public static final class Builder {
        private String name;
        private String displayName;
        private String icon = "STONE";
        private boolean ranked = true;
        private boolean ffaEnabled = true;
        private double maxHealth = 20.0;
        private boolean naturalHealthRegen = true;
        private double knockbackMultiplier = 1.0;
        private List<KitItemEntry> items = new ArrayList<KitItemEntry>();
        private Map<String, String> armor = new LinkedHashMap<String, String>();
        private boolean enabled = true;
        private boolean autoFood = false;
        private boolean swordShieldBreak = false;
        private boolean blockPlace = false;
        private boolean blockBreak = false;
        private boolean breakPlayerPlacedOnly = false;
        private List<String> canBreak = new ArrayList<String>();
        private boolean pearl = true;
        private boolean totem = true;
        private boolean forceAdventure = false;
        private int timeoutSeconds = 0;
        private List<String> arenas = new ArrayList<String>();
        private List<String> partyArenas = new ArrayList<String>();
        private List<String> startCommands = new ArrayList<String>();
        private List<KitStartEffect> startEffects = new ArrayList<KitStartEffect>();
        private boolean presetEnabled;

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
            this.items = new ArrayList<KitItemEntry>(source.items);
            this.armor = new LinkedHashMap<String, String>(source.armor);
            this.enabled = source.enabled;
            this.autoFood = source.autoFood;
            this.swordShieldBreak = source.swordShieldBreak;
            this.blockPlace = source.blockPlace;
            this.blockBreak = source.blockBreak;
            this.breakPlayerPlacedOnly = source.breakPlayerPlacedOnly;
            this.canBreak = new ArrayList<String>(source.canBreak);
            this.pearl = source.pearl;
            this.totem = source.totem;
            this.forceAdventure = source.forceAdventure;
            this.timeoutSeconds = source.timeoutSeconds;
            this.arenas = new ArrayList<String>(source.arenas);
            this.partyArenas = new ArrayList<String>(source.partyArenas);
            this.startCommands = new ArrayList<String>(source.startCommands);
            this.startEffects = new ArrayList<KitStartEffect>(source.startEffects);
            this.presetEnabled = source.presetEnabled;
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
            this.items = new ArrayList<KitItemEntry>((Collection)Objects.requireNonNull(value, "items"));
            return this;
        }

        public Builder armor(Map<String, String> value) {
            this.armor = new LinkedHashMap<String, String>(Objects.requireNonNull(value, "armor"));
            return this;
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
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

        public Builder blockInteract(boolean value) {
            this.blockPlace = value;
            this.blockBreak = value;
            return this;
        }

        public Builder breakPlayerPlacedOnly(boolean value) {
            this.breakPlayerPlacedOnly = value;
            return this;
        }

        public Builder canBreak(List<String> value) {
            this.canBreak = new ArrayList<String>((Collection)Objects.requireNonNull(value, "canBreak"));
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
            this.arenas = value == null || value.isBlank() ? new ArrayList<String>() : new ArrayList<String>(List.of(value.trim().toLowerCase(Locale.ROOT)));
            return this;
        }

        public Builder arenas(List<String> value) {
            this.arenas = new ArrayList<String>();
            if (value != null) {
                for (String entry : value) {
                    String id;
                    if (entry == null || entry.isBlank() || this.arenas.contains(id = entry.trim().toLowerCase(Locale.ROOT))) continue;
                    this.arenas.add(id);
                }
            }
            return this;
        }

        public Builder addArena(String value) {
            String id;
            if (value != null && !value.isBlank() && !this.arenas.contains(id = value.trim().toLowerCase(Locale.ROOT))) {
                this.arenas.add(id);
            }
            return this;
        }

        public Builder partyArenas(List<String> value) {
            this.partyArenas = new ArrayList<String>();
            if (value != null) {
                for (String entry : value) {
                    String id;
                    if (entry == null || entry.isBlank() || this.partyArenas.contains(id = entry.trim().toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    this.partyArenas.add(id);
                }
            }
            return this;
        }

        public Builder addPartyArena(String value) {
            String id;
            if (value != null && !value.isBlank() && !this.partyArenas.contains(id = value.trim().toLowerCase(Locale.ROOT))) {
                this.partyArenas.add(id);
            }
            return this;
        }

        public Builder startCommands(List<String> value) {
            this.startCommands = new ArrayList<String>(value == null ? List.of() : value);
            return this;
        }

        public Builder addStartCommand(String command) {
            if (command != null && !command.isBlank()) {
                this.startCommands.add(command.trim());
            }
            return this;
        }

        public Builder startEffects(List<KitStartEffect> value) {
            this.startEffects = new ArrayList<KitStartEffect>(value == null ? List.of() : value);
            return this;
        }

        public Builder addStartEffect(KitStartEffect effect) {
            if (effect != null) {
                this.startEffects.add(effect);
            }
            return this;
        }

        public Builder presetEnabled(boolean value) {
            this.presetEnabled = value;
            return this;
        }

        public KitDefinition build() {
            return new KitDefinition(this.name, this.displayName, this.icon, this.ranked, this.ffaEnabled, this.maxHealth, this.naturalHealthRegen, this.knockbackMultiplier, this.items, this.armor, this.enabled, this.autoFood, this.swordShieldBreak, this.blockPlace, this.blockBreak, this.breakPlayerPlacedOnly, this.canBreak, this.pearl, this.totem, this.forceAdventure, this.timeoutSeconds, this.arenas, this.partyArenas, this.startCommands, this.startEffects, this.presetEnabled);
        }
    }
}
