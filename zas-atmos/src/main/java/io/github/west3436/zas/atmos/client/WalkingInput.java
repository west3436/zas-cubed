package io.github.west3436.zas.atmos.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Lets a player walk while a window is open, for a window that reads where the player is (CONT-034).
 * The game stops reading the movement keys while any screen is open, so this reads the keys they're
 * bound to from the keyboard and mouse themselves: forward, back, left, right, jump and sneak. Looking
 * around still needs the window closed.
 */
final class WalkingInput extends Input {
    private final Options options;
    private final long window;
    private final Input replaced;

    private WalkingInput(Options options, long window, Input replaced) {
        this.options = options;
        this.window = window;
        this.replaced = replaced;
    }

    /** Puts one in place of the player's input, until {@link #stop}. */
    static void start(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null && !(player.input instanceof WalkingInput)) {
            player.input =
                    new WalkingInput(minecraft.options, minecraft.getWindow().getWindow(), player.input);
        }
    }

    /** Gives the player back the input {@link #start} replaced, unless the player has changed since. */
    static void stop(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null && player.input instanceof WalkingInput walking) {
            player.input = walking.replaced;
        }
    }

    @Override
    public void tick(boolean isSneaking, float sneakingSpeedMultiplier) {
        up = held(options.keyUp);
        down = held(options.keyDown);
        left = held(options.keyLeft);
        right = held(options.keyRight);
        forwardImpulse = impulse(up, down);
        leftImpulse = impulse(left, right);
        jumping = held(options.keyJump);
        shiftKeyDown = held(options.keyShift);
        if (isSneaking) {
            leftImpulse *= sneakingSpeedMultiplier;
            forwardImpulse *= sneakingSpeedMultiplier;
        }
    }

    private static float impulse(boolean towards, boolean away) {
        return towards == away ? 0 : towards ? 1 : -1;
    }

    private boolean held(KeyMapping key) {
        InputConstants.Key bound = key.getKey();
        return switch (bound.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, bound.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}
