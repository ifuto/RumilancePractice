/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.rumilance.practice.model.KitItemEntry
 */
package com.rumilance.practice.model;

import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public static final class KitDefinition.Builder {
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
    private List<String> startCommands = new ArrayList<String>();
    private boolean presetEnabled;

    private KitDefinition.Builder(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = name;
    }

    private KitDefinition.Builder(KitDefinition source) {
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
        this.startCommands = new ArrayList<String>(source.startCommands);
        this.presetEnabled = source.presetEnabled;
    }

    public KitDefinition.Builder name(String value) {
        this.name = Objects.requireNonNull(value, "name");
        return this;
    }

    public KitDefinition.Builder displayName(String value) {
        this.displayName = Objects.requireNonNull(value, "displayName");
        return this;
    }

    public KitDefinition.Builder icon(String value) {
        this.icon = Objects.requireNonNull(value, "icon");
        return this;
    }

    public KitDefinition.Builder ranked(boolean value) {
        this.ranked = value;
        return this;
    }

    public KitDefinition.Builder ffaEnabled(boolean value) {
        this.ffaEnabled = value;
        return this;
    }

    public KitDefinition.Builder maxHealth(double value) {
        this.maxHealth = value;
        return this;
    }

    public KitDefinition.Builder naturalHealthRegen(boolean value) {
        this.naturalHealthRegen = value;
        return this;
    }

    public KitDefinition.Builder knockbackMultiplier(double value) {
        this.knockbackMultiplier = value;
        return this;
    }

    public KitDefinition.Builder items(List<KitItemEntry> value) {
        this.items = new ArrayList<KitItemEntry>((Collection)Objects.requireNonNull(value, "items"));
        return this;
    }

    public KitDefinition.Builder armor(Map<String, String> value) {
        this.armor = new LinkedHashMap<String, String>(Objects.requireNonNull(value, "armor"));
        return this;
    }

    public KitDefinition.Builder enabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public KitDefinition.Builder autoFood(boolean value) {
        this.autoFood = value;
        return this;
    }

    public KitDefinition.Builder swordShieldBreak(boolean value) {
        this.swordShieldBreak = value;
        return this;
    }

    public KitDefinition.Builder blockPlace(boolean value) {
        this.blockPlace = value;
        return this;
    }

    public KitDefinition.Builder blockBreak(boolean value) {
        this.blockBreak = value;
        return this;
    }

    public KitDefinition.Builder blockInteract(boolean value) {
        this.blockPlace = value;
        this.blockBreak = value;
        return this;
    }

    public KitDefinition.Builder breakPlayerPlacedOnly(boolean value) {
        this.breakPlayerPlacedOnly = value;
        return this;
    }

    public KitDefinition.Builder canBreak(List<String> value) {
        this.canBreak = new ArrayList<String>((Collection)Objects.requireNonNull(value, "canBreak"));
        return this;
    }

    public KitDefinition.Builder addCanBreak(String material) {
        this.canBreak.add(Objects.requireNonNull(material, "material"));
        return this;
    }

    public KitDefinition.Builder pearl(boolean value) {
        this.pearl = value;
        return this;
    }

    public KitDefinition.Builder totem(boolean value) {
        this.totem = value;
        return this;
    }

    public KitDefinition.Builder forceAdventure(boolean value) {
        this.forceAdventure = value;
        return this;
    }

    public KitDefinition.Builder timeoutSeconds(int value) {
        this.timeoutSeconds = value;
        return this;
    }

    public KitDefinition.Builder arenaName(String value) {
        this.arenas = value == null || value.isBlank() ? new ArrayList<String>() : new ArrayList<String>(List.of(value.trim().toLowerCase(Locale.ROOT)));
        return this;
    }

    public KitDefinition.Builder arenas(List<String> value) {
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

    public KitDefinition.Builder addArena(String value) {
        String id;
        if (value != null && !value.isBlank() && !this.arenas.contains(id = value.trim().toLowerCase(Locale.ROOT))) {
            this.arenas.add(id);
        }
        return this;
    }

    public KitDefinition.Builder startCommands(List<String> value) {
        this.startCommands = new ArrayList<String>(value == null ? List.of() : value);
        return this;
    }

    public KitDefinition.Builder addStartCommand(String command) {
        if (command != null && !command.isBlank()) {
            this.startCommands.add(command.trim());
        }
        return this;
    }

    public KitDefinition.Builder presetEnabled(boolean value) {
        this.presetEnabled = value;
        return this;
    }

    public KitDefinition build() {
        return new KitDefinition(this.name, this.displayName, this.icon, this.ranked, this.ffaEnabled, this.maxHealth, this.naturalHealthRegen, this.knockbackMultiplier, this.items, this.armor, this.enabled, this.autoFood, this.swordShieldBreak, this.blockPlace, this.blockBreak, this.breakPlayerPlacedOnly, this.canBreak, this.pearl, this.totem, this.forceAdventure, this.timeoutSeconds, this.arenas, this.startCommands, this.presetEnabled);
    }
}
