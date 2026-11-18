package io.github.west3436.zas.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.west3436.zas.airflow.FlungBodies;
import io.github.west3436.zas.airflow.Tumble;
import io.github.west3436.zas.config.ZasClientConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Flung bodies tumble (CLIENT-027 to CLIENT-029). The server says which living entities airflow has
 * flung ({@link FlungBodies}); each client tick this turns each of them a little more ({@link
 * Tumble}), and its renderer turns the whole model by that much about its middle, before the
 * entity's own rotations ({@code LivingEntityRendererMixin}). So it works on any model, a resource
 * pack's included, and neither the name tag nor the shadow turns.
 *
 * <p>The client also stops a tumble the moment it sees the body land, swim, ride or glide, rather
 * than wait for the server's word. The camera never turns: only the model does, and not at all for
 * the body the camera is in while it looks out in first person (CLIENT-028), for mods that draw it.
 * With the setting off nothing turns (CLIENT-029). Client thread.
 */
public final class ClientTumbles {
    private static final Int2ObjectMap<Tumble> TUMBLES = new Int2ObjectOpenHashMap<>();

    private static ClientLevel lastLevel;

    private ClientTumbles() {}

    /** One client tick, after the entities' own. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            TUMBLES.clear();
            lastLevel = level;
        }
        if (level == null) {
            FlungBodies.CLIENT.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        for (IntIterator it = FlungBodies.CLIENT.ids().iterator(); it.hasNext(); ) {
            int id = it.nextInt();
            if (!(level.getEntity(id) instanceof LivingEntity)) {
                it.remove();
            } else if (ZasClientConfig.tumble()) {
                TUMBLES.computeIfAbsent(id, k -> new Tumble());
            }
        }
        if (!ZasClientConfig.tumble()) {
            TUMBLES.clear();
            return;
        }
        for (var it = TUMBLES.int2ObjectEntrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Entity e = level.getEntity(entry.getIntKey());
            if (!(e instanceof LivingEntity living) || e.isRemoved()) {
                it.remove();
                continue;
            }
            boolean flung = FlungBodies.CLIENT.flung(entry.getIntKey()) && airborne(living);
            double yaw = Math.toRadians(living.yBodyRot);
            boolean turned = entry.getValue()
                    .tick(flung, e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo, -Math.sin(yaw), Math.cos(yaw));
            if (!turned) {
                it.remove();
            }
        }
    }

    /** What the client sees of the server's test: off the ground, out of liquid, not riding or gliding. */
    private static boolean airborne(LivingEntity e) {
        return !e.onGround() && !e.isInLiquid() && !e.isPassenger() && !e.isFallFlying();
    }

    /**
     * Turns a body's model by its tumble, at the start of its renderer's rotations: the pose is at
     * the body's feet then, upright, in the world's axes and the body's scale.
     */
    public static void apply(LivingEntity e, PoseStack pose, float partialTick, float scale) {
        if (TUMBLES.isEmpty()) {
            return;
        }
        Tumble t = TUMBLES.get(e.getId());
        if (t == null || !ZasClientConfig.tumble()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        double angle = t.angle(partialTick);
        if (angle == 0) {
            return;
        }
        float middle = e.getBbHeight() / 2 / scale;
        pose.translate(0, middle, 0);
        pose.mulPose(
                new Quaternionf().rotateAxis((float) Math.toRadians(angle), (float) t.axisX(), 0, (float) t.axisZ()));
        pose.translate(0, -middle, 0);
    }

    /** Drops every tumble, as when leaving a server. */
    static void clear() {
        TUMBLES.clear();
        FlungBodies.CLIENT.clear();
    }
}
