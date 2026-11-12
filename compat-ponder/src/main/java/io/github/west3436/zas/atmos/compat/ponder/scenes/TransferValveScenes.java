package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveBlock;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The tank transfer valve (CONT-103, CONT-104), placed (D-86). */
public final class TransferValveScenes {
    private TransferValveScenes() {}

    /**
     * A TTV set down by a sneak-use, fitted with two tanks, its window opened by an empty hand, and
     * primed where it stands; its timer opens its valve, and a hot mix of fuel and O₂ burns.
     */
    public static void transferValve(SceneBuilder scene, SceneBuildingUtil util) {
        // A TTV is small, so the view is drawn in close.
        scene.scaleSceneView(ZOOM);
        Stage.open(scene, "tank_transfer_valve");

        BlockPos floor = util.grid().at(1, 0, 1);
        BlockPos pos = floor.above();
        Vec3 ground = util.vector().topOf(floor);
        Vec3 above = ground.add(0, 0.4, 0);

        // Sneak-used on the floor, it is set down, its timer toward whoever set it down.
        Hand.sneakUse(scene, util, floor, Direction.UP, new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get()));
        Build.drop(
                scene,
                util,
                pos,
                AtmosBlocks.TANK_TRANSFER_VALVE
                        .get()
                        .defaultBlockState()
                        .setValue(TransferValveBlock.FACING, Direction.NORTH));
        Say.at(scene, above, "ttv.place");

        // A tank used on it fits, up to two: an O₂ tank and a plain one, as fuel would be in.
        List<ItemStack> tanks =
                List.of(new ItemStack(AtmosItems.OXYGEN_TANK.get()), new ItemStack(AtmosItems.EMPTY_TANK.get()));
        List<ItemStack> fitted = new ArrayList<>();
        for (ItemStack tank : tanks) {
            Hand.use(scene, util, pos, Direction.UP, tank);
            fitted.add(tank);
            show(scene, pos, fitted);
        }
        Say.at(scene, above, "ttv.tanks");
        Say.at(scene, above, "ttv.valve");

        // An empty hand opens its window, where its timer is set and started.
        Hand.use(scene, util, pos, Direction.UP, ItemStack.EMPTY);
        Say.at(scene, above, "ttv.use");

        // Started, it primes where it stands, out of reach, until its timer opens its valve.
        TransferValve valve =
                new TransferValve(tanks.get(0).copy(), tanks.get(1).copy(), false);
        Build.place(scene, pos, Blocks.AIR.defaultBlockState());
        ElementLink<EntityElement> primed = scene.world().createEntity(level -> primed(level, ground, valve, false));
        Say.at(scene, above, "ttv.start");
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

    /**
     * Shows the placed TTV holding these tanks: its block's count, which picks the model, and each
     * tank's kind, which tints it. A server sends a client the kinds in the block entity's update tag,
     * so the scene makes that tag from a TTV outside the world and hands it over.
     */
    private static void show(SceneBuilder scene, BlockPos pos, List<ItemStack> fitted) {
        List<ItemStack> tanks = fitted.stream().map(ItemStack::copy).toList();
        scene.world().modifyBlockEntity(pos, TransferValveBlockEntity.class, ttv -> {
            TransferValveBlockEntity sent = new TransferValveBlockEntity(pos, ttv.getBlockState());
            tanks.forEach(tank -> sent.fit(tank.copy()));
            HolderLookup.Provider registries = ttv.getLevel().registryAccess();
            ttv.handleUpdateTag(sent.getUpdateTag(registries), registries);
        });
        Build.change(scene, pos, s -> s.setValue(TransferValveBlock.TANKS, tanks.size()));
    }

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
