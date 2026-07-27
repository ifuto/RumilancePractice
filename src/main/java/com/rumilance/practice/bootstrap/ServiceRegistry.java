package com.rumilance.practice.bootstrap;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal type-safe service locator used to wire together every RumilancePractice service
 * without resorting to a full dependency-injection framework. Registered once during
 * {@code onEnable} and consulted by commands/listeners/GUIs as they are implemented in later
 * phases.
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T instance) {
        services.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(instance, "instance"));
    }

    public <T> T get(Class<T> type) {
        Object instance = services.get(type);
        if (instance == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return type.cast(instance);
    }

    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(services.get(type)).map(type::cast);
    }

    public boolean isRegistered(Class<?> type) {
        return services.containsKey(type);
    }

    public void unregisterAll() {
        services.clear();
    }
}
