package io.github.west3436.zas.compat.jei;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.compat.viewer.GasEntry;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerGate;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * Shows every gas (COMPAT-061) and every reaction (COMPAT-060) in JEI. A gas is an ingredient of its
 * own type, so asking JEI for a gas's recipes or uses finds the reactions that give or take it.
 *
 * <p>JEI builds its lists when a client has joined and received the recipes, and again after {@code
 * /reload}. The server sends the gases and reactions just before the recipes both times, so each
 * list is made from what the server runs now.
 *
 * <p>JEI makes one of these on a client that has JEI, and nothing else does. Outside the supported
 * versions it registers nothing (COMPAT-002).
 */
@JeiPlugin
public final class ZasJeiPlugin implements IModPlugin {
    /**
     * The JEI versions the plugin supports: from build 19.21.0.247, which it is compiled against
     * and tested with, up to JEI 20. The range starts at that build and not at 19.21, so that it
     * holds no build older than the one the plugin is known to work with.
     */
    public static final String SUPPORTED = "[19.21.0.247,20)";

    /** JEI's mod id. */
    public static final String JEI = "jei";

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "viewer");

    /** One gate for the whole game, so its warning is logged once however often JEI reloads. */
    private static final ViewerGate INSTALLED = new ViewerGate(JEI, SUPPORTED);

    private final ViewerGate gate;

    /** JEI makes the plugin with this. */
    public ZasJeiPlugin() {
        this(INSTALLED);
    }

    /** A plugin behind a gate of the caller's, for tests. */
    public ZasJeiPlugin(ViewerGate gate) {
        this.gate = gate;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        List<GasEntry> gases = gases();
        if (gases.isEmpty()) {
            return;
        }
        registration.register(
                GasIngredients.TYPE,
                gases.stream().map(GasStack::of).toList(),
                new GasIngredients.Helper(),
                new GasIngredients.Renderer(),
                GasIngredients.CODEC);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        List<GasEntry> gases = gases();
        if (gases.isEmpty()) {
            return;
        }
        registration.addRecipeCategories(
                new GasCategory(registration.getJeiHelpers().getGuiHelper(), gases),
                new ReactionCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<GasEntry> gases = gases();
        if (gases.isEmpty()) {
            return;
        }
        registration.addRecipes(GasCategory.TYPE, gases);
        registration.addRecipes(ReactionCategory.TYPE, ViewerData.reactions());
    }

    /**
     * The gases to show: none if this JEI is outside the supported versions, and none on a server
     * without ZAS³, which sends no gases. With no gas there is nothing to register at all.
     */
    private List<GasEntry> gases() {
        return gate.on() ? ViewerData.gases() : List.of();
    }
}
