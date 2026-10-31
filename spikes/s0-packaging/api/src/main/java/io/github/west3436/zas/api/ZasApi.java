package io.github.west3436.zas.api;

import java.io.InputStream;
import java.util.Properties;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** S0: entry point. Returns the fallback until ZAS³ installs its service. */
public final class ZasApi {
    public static final String VERSION = loadVersion();

    private static final AtmosphereService FALLBACK = new AtmosphereService() {
        @Override
        public double pressureKPa(Level level, BlockPos pos) {
            return 101.325;
        }

        @Override
        public String implementationName() {
            return "fallback";
        }
    };

    private static volatile AtmosphereService service;

    private ZasApi() {}

    public static AtmosphereService get() {
        AtmosphereService s = service;
        return s != null ? s : FALLBACK;
    }

    public static boolean isPresent() {
        return service != null;
    }

    /** Called by ZAS³ during mod construction. Not for consumers. */
    public static synchronized void install(AtmosphereService impl) {
        if (service != null) {
            throw new IllegalStateException("An atmosphere service is already installed: " + service.implementationName());
        }
        service = impl;
    }

    /** Where this copy of the API was loaded from, for the spike log. */
    public static String origin() {
        var src = ZasApi.class.getProtectionDomain().getCodeSource();
        return "module=" + ZasApi.class.getModule().getName() + " loader=" + ZasApi.class.getClassLoader().getName()
                + " source=" + (src == null ? "?" : src.getLocation());
    }

    private static String loadVersion() {
        try (InputStream in = ZasApi.class.getResourceAsStream("/META-INF/zas-api.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version", "unknown");
        } catch (Exception e) {
            return "error:" + e;
        }
    }
}
