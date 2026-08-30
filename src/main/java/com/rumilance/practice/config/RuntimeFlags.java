package com.rumilance.practice.config;

/**
 * Mutable runtime flags that outlive a single {@link PluginSettings} snapshot (e.g. maintenance).
 */
public final class RuntimeFlags {

    private volatile boolean maintenance;

    public RuntimeFlags(boolean maintenance) {
        this.maintenance = maintenance;
    }

    public boolean maintenance() {
        return maintenance;
    }

    public void setMaintenance(boolean maintenance) {
        this.maintenance = maintenance;
    }
}
