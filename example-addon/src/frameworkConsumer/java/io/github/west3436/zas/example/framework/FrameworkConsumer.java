package io.github.west3436.zas.example.framework;

import net.neoforged.fml.common.Mod;

/**
 * A second consumer of SS13 Framework beside the ZAS³ release jar. Its jar holds nothing but this
 * class and a newer framework, nested, so the one-copy run has two copies to choose between.
 */
@Mod(FrameworkConsumer.MOD_ID)
public final class FrameworkConsumer {
    /** This mod's id. */
    public static final String MOD_ID = "zas_example_framework";

    public FrameworkConsumer() {}
}
