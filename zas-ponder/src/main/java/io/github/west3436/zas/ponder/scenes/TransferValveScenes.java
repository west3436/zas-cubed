package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.ponder.scene.Hand;
import io.github.west3436.zas.ponder.scene.Say;
import io.github.west3436.zas.ponder.scene.Stage;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The tank transfer valve (CONT-103, CONT-104). */
public final class TransferValveScenes {
    private TransferValveScenes() {}

    /** A TTV holds two tanks; its timer opens its valve, and a hot mix of fuel and O₂ burns. */
    public static void transferValve(SceneBuilder scene, SceneBuildingUtil util) {
        // A TTV is small, so the view is drawn in close.
        scene.scaleSceneView(ZOOM);
        Stage.open(scene, "tank_transfer_valve");

        // An O₂ tank and a plain one, as fuel would be in.
        TransferValve valve = new TransferValve(
                new ItemStack(AtmosItems.OXYGEN_TANK.get()), new ItemStack(AtmosItems.EMPTY_TANK.get()), false);
        ItemStack ttv = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem.save(ttv, valve);

        Vec3 floor = util.vector().topOf(util.grid().at(1, 0, 1));
        Vec3 above = floor.add(0, 0.4, 0);
        ElementLink<EntityElement> item = scene.world().createItemEntity(floor.add(0, 0.6, 0), Vec3.ZERO, ttv);
        scene.idle(20);
        Say.at(scene, above, "ttv.tanks");
        Hand.useAt(scene, above.add(0, 0.3, 0), ttv);
        Say.at(scene, above, "ttv.use");
        Say.at(scene, above, "ttv.valve");

        // Its timer started, it lies in the world, out of reach, until the timer opens its valve.
        scene.world().modifyEntity(item, Entity::discard);
        ElementLink<EntityElement> primed = scene.world().createEntity(level -> primed(level, floor, valve, false));
        Say.at(scene, above, "ttv.timer");

        scene.world().modifyEntity(primed, entity -> setOpen(entity, valve));
        scene.idle(10);
        scene.effects()
                .emitParticles(
                        above,
                        scene.effects().simpleParticleEmitter(ParticleTypes.FLAME, new Vec3(0, 0.02, 0)),
                        2,
                        Say.LINE + 20);
        Say.at(scene, above, "ttv.burn");
        scene.effects()
                .emitParticles(above, scene.effects().simpleParticleEmitter(ParticleTypes.EXPLOSION, Vec3.ZERO), 1, 1);
        Say.at(scene, above, "ttv.burst");
        scene.markAsFinished();
    }

    /** How much closer than usual the view is drawn. */
    private static final float ZOOM = 2f;

    /** A primed TTV lying at a spot, with its tanks and its valve as given. */
    private static Entity primed(Level level, Vec3 at, TransferValve valve, boolean open) {
        PrimedTransferValve primed = new PrimedTransferValve(AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(), level);
        primed.setPos(at);
        load(primed, valve, open);
        return primed;
    }

    /** Opens a primed TTV's valve, as its timer running out does. */
    private static void setOpen(Entity entity, TransferValve valve) {
        if (entity instanceof PrimedTransferValve primed) {
            load(primed, valve, true);
        }
    }

    /**
     * Gives a primed TTV its tanks and valve through its saved data, the only way in from outside: the
     * entity draws what it was loaded with. The names are its saved fields'.
     */
    private static void load(PrimedTransferValve primed, TransferValve valve, boolean open) {
        CompoundTag tag = primed.saveWithoutId(new CompoundTag());
        tag.put("First", valve.first().saveOptional(primed.registryAccess()));
        tag.put("Second", valve.second().saveOptional(primed.registryAccess()));
        tag.putBoolean("Open", open);
        primed.load(tag);
    }
}
