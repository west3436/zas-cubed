package io.github.west3436.zas.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EngineClasspathTest {
    /** The engine builds and tests without Minecraft or NeoForge on its classpath. */
    @Test
    void core043_noMinecraftOnClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.minecraft.SharedConstants"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.neoforged.fml.common.Mod"));
    }
}
