// Modified by OGDom445 on 2026-10-15: unofficial compatibility patch. GNU GPL v3; see LICENSE.
package oxy.geyser.fp.util;

import org.geysermc.geyser.session.GeyserSession;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

/** Resolves both registry APIs without linking the extension to either relocated type. */
public final class RegistryAccess {
    private static final Method SESSION_REGISTRIES;
    private static final Method REGISTRY;
    private static final Method SIZE;
    private static final Field BIOME;

    static {
        try {
            ClassLoader loader = GeyserSession.class.getClassLoader();
            Method accessor;
            String registryPackage;
            try {
                accessor = GeyserSession.class.getMethod("javaRegistries");
                registryPackage = "org.geysermc.geyser.registry.java.";
            } catch (NoSuchMethodException legacy) {
                accessor = GeyserSession.class.getMethod("getRegistryCache");
                registryPackage = "org.geysermc.geyser.session.cache.registry.";
            }
            SESSION_REGISTRIES = accessor;
            Class<?> key = Class.forName(registryPackage + "JavaRegistryKey", false, loader);
            Class<?> provider = Class.forName(registryPackage + "JavaRegistryProvider", false, loader);
            Class<?> registry = Class.forName(registryPackage + "JavaRegistry", false, loader);
            // Resolve the field without initializing Geyser's registries before Geyser is ready.
            BIOME = Class.forName(registryPackage + "JavaRegistries", false, loader).getField("BIOME");
            REGISTRY = provider.getMethod("registry", key);
            SIZE = registry.getMethod("size");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Geyser registry API; update GeyserFloatingPoints", e);
        }
    }

    private RegistryAccess() {
    }

    public static void validate() {
        // Trigger API resolution before accepting players.
    }

    public static int biomeCount(GeyserSession session) {
        try {
            Object provider = SESSION_REGISTRIES.invoke(session);
            Object registry = REGISTRY.invoke(provider, BIOME.get(null));
            int size = (Integer) SIZE.invoke(registry);
            if (size <= 0) {
                throw new IllegalStateException("Java biome registry is empty");
            }
            return size;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read the Java biome registry", e);
        }
    }
}
