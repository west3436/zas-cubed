package io.github.west3436.zas.atmos.lang;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.LiteralMessage;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Code for {@link PlayerTextTest}'s scan to read, in each way text can reach a player. It's never run,
 * and it's only in the test source set.
 */
final class PlayerTextSamples {
    /** What has to be scanned together to find each kind of sample. */
    static final List<Class<?>> FLAGGED = List.of(Flagged.class, Mode.class, Label.class, PlayerTextSamples.class);

    static final List<Class<?>> ALLOWED = List.of(Allowed.class, Diagnostic.class);
    static final List<Class<?>> KEYS = List.of(Keys.class, Mode.class);

    private static final String CONSTANT = "Constant";
    private static final String[] ARRAY = {"Array"};

    private PlayerTextSamples() {}

    private enum Mode {
        ON("Enum field", "enum");

        final String label;
        final String key;

        Mode(String label, String key) {
            this.label = label;
            this.key = key;
        }
    }

    private record Label(String text) {}

    /** A line that's left out of the scan on purpose. */
    record Diagnostic(String file) {
        @Override
        public String toString() {
            return "Diagnostic in " + file;
        }
    }

    private static final class Flagged {
        Object direct() {
            return Component.literal("Direct");
        }

        Object concatenated(int n) {
            return Component.literal("Concatenated " + n);
        }

        Object local() {
            String text = "Local";
            return Component.literal(text);
        }

        Object ternary(boolean on) {
            return Component.literal(on ? "Either" : "Or");
        }

        Object constant() {
            return Component.literal(CONSTANT);
        }

        Object array(int i) {
            return Component.literal(ARRAY[i]);
        }

        private Object helper(String text) {
            return Component.literal(text);
        }

        Object passed() {
            return helper("Passed");
        }

        private String word() {
            return "Returned";
        }

        Object returned() {
            return Component.literal(word());
        }

        Object enumField(Mode mode) {
            return Component.literal(mode.label);
        }

        Object recordField() {
            return Component.literal(new Label("Record field").text());
        }

        Object appended() {
            return Component.empty().append("Appended");
        }

        void drawn(GuiGraphics g, Font font) {
            g.drawString(font, "Drawn", 0, 0, 0);
        }

        void floating(PoseStack pose, MultiBufferSource buffers) {
            DebugRenderer.renderFloatingText(pose, buffers, "Floating", 0, 0, 0, 0);
        }

        Object argument() {
            return Component.translatable("sample.plain", "Argument");
        }

        Object argumentOfAClientKey() {
            return I18n.get("sample.plain", "Client argument");
        }

        Object fallback() {
            return Component.translatableWithFallback("sample.fallback", "Fallback");
        }

        Object format(int n) {
            return Component.literal(String.format(Locale.ROOT, "Format %d", n));
        }

        Object formatArgument() {
            return Component.literal(String.format(Locale.ROOT, "%s", "Argument of a format"));
        }

        Supplier<Object> captured() {
            String text = "Captured";
            return () -> Component.literal(text);
        }

        Object commandError() {
            return new LiteralMessage("Command error");
        }

        Object substring() {
            return Component.literal("Substring".substring(0));
        }
    }

    /** Nothing here is language. */
    private static final class Allowed {
        private static final String[] STEPS = {"−10", "+.1"};

        Object symbols(boolean on) {
            return Component.literal(on ? "● " : "—").append(" ").append(Component.literal("✕"));
        }

        Object numbers(int x, int y, double d) {
            return Component.literal(x + ", " + y + String.format(Locale.ROOT, "%,.1f %s%%", d, "■"));
        }

        Object builtFormat(int decimals, double d) {
            return Component.literal(String.format(Locale.ROOT, "%." + decimals + "f %s", d, "○"));
        }

        Object step(int i) {
            return Component.literal(STEPS[i]);
        }

        Object fromElsewhere(ResourceLocation id) {
            return Component.literal(id.toString());
        }

        Object unit(String number, boolean kelvin) {
            return Component.literal(number + " " + (kelvin ? "K" : "kPa"));
        }

        Object diagnostic(Diagnostic line) {
            return Component.literal(line.toString());
        }

        Object notText() {
            return ResourceLocation.fromNamespaceAndPath("sample", "not_text");
        }
    }

    private static final class Keys {
        private static Object text(String key) {
            return Component.translatable("sample.prefix." + key);
        }

        Object plain() {
            return Component.translatable("sample.plain");
        }

        Object client() {
            return I18n.get("sample.client");
        }

        Object ternary(boolean on) {
            return Component.translatable(on ? "sample.either" : "sample.or");
        }

        Object helper() {
            return text("helper");
        }

        Object fromEnum(Mode mode) {
            return text(mode.key);
        }

        Object unknown(ResourceLocation id) {
            return text(id.getPath());
        }
    }
}
