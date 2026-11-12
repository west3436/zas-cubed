package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.holder.TankHolderBlock;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The tank holder (CONT-149 to CONT-151): a rack of ten personal tanks on two shelves, with no window.
 * A tank used on it goes in, and an empty hand takes out the last one put in.
 */
public final class HolderScenes {
    private HolderScenes() {}

    /** The ticks between tanks going in, when several do. */
    private static final int STEP = 8;

    /** Tanks going in, filling the lower shelf first, one coming out, and a full holder. */
    public static void tankHolder(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "tank_holder");
        BlockPos holder = util.grid().at(2, 1, 2);
        Build.drop(
                scene,
                util,
                holder,
                AtmosBlocks.TANK_HOLDER.get().defaultBlockState().setValue(TankHolderBlock.FACING, Direction.NORTH));
        Vec3 front = util.vector().blockSurface(holder, Direction.NORTH);

        // A tank used on it goes in.
        List<Item> held = new ArrayList<>();
        Hand.use(scene, util, holder, Direction.NORTH, new ItemStack(AtmosItems.OXYGEN_TANK.get()));
        put(scene, holder, held, AtmosItems.OXYGEN_TANK.get());
        Say.at(scene, front, "tank_holder.put");

        // More go in, along the lower shelf and then onto the upper one.
        List<Item> more = List.of(
                AtmosItems.AIR_TANK.get(),
                AtmosItems.OXYGEN_TANK.get(),
                AtmosItems.EMPTY_TANK.get(),
                AtmosItems.AIR_TANK.get(),
                AtmosItems.EMERGENCY_OXYGEN_TANK.get());
        Say.over(scene, more.size() * STEP + Say.LINE, front, "tank_holder.order");
        for (Item tank : more) {
            scene.idle(STEP);
            put(scene, holder, held, tank);
        }
        scene.idle(Say.LINE + 10);

        // An empty hand takes out the last one put in.
        Hand.use(scene, util, holder, Direction.NORTH, ItemStack.EMPTY);
        held.removeLast();
        show(scene, holder, held);
        Say.at(scene, front, "tank_holder.take");

        // Full, it takes no more: a tank used on it stays in hand.
        while (held.size() < TankHolderBlockEntity.PLACES) {
            put(scene, holder, held, held.size() % 2 == 0 ? AtmosItems.AIR_TANK.get() : AtmosItems.OXYGEN_TANK.get());
            scene.idle(STEP);
        }
        Hand.use(scene, util, holder, Direction.NORTH, new ItemStack(AtmosItems.AIR_TANK.get()));
        scene.overlay().showOutline(PonderPalette.RED, "full", util.select().position(holder), Say.LINE);
        Say.at(scene, front, "tank_holder.full");
        scene.markAsFinished();
    }

    /** One more tank goes in, in the next place. */
    private static void put(SceneBuilder scene, BlockPos pos, List<Item> held, Item tank) {
        held.add(tank);
        show(scene, pos, held);
    }

    /**
     * Shows the holder holding these tanks: its block's count, which picks the model, and each tank's
     * kind, which tints it (CONT-151). A server sends a client the kinds in the block entity's update
     * tag, so the scene makes that tag from a holder outside the world and hands it over.
     */
    private static void show(SceneBuilder scene, BlockPos pos, List<Item> held) {
        List<Item> tanks = List.copyOf(held);
        scene.world().modifyBlockEntity(pos, TankHolderBlockEntity.class, holder -> {
            TankHolderBlockEntity sent = new TankHolderBlockEntity(pos, holder.getBlockState());
            for (Item tank : tanks) {
                sent.put(new ItemStack(tank));
            }
            HolderLookup.Provider registries = holder.getLevel().registryAccess();
            holder.handleUpdateTag(sent.getUpdateTag(registries), registries);
        });
        Build.change(scene, pos, s -> s.setValue(TankHolderBlock.TANKS, tanks.size()));
    }
}
