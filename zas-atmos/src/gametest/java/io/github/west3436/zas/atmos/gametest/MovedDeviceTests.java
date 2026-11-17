package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.api.relocation.RelocationOutcome;
import io.github.west3436.zas.api.relocation.RelocationResult;
import io.github.west3436.zas.api.relocation.RelocationTicket;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Devices moved with their blocks by another mod through the relocation API (CONT-156): they keep
 * their gas, which the mover carries in the data it copies, instead of handing it back to the world
 * as they are taken away.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovedDeviceTests {
    private static final SourceId MOVER = SourceId.of("zas_gametest", "mover");
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final double MOLES = 1_000;

    private MovedDeviceTests() {}

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    /**
     * Moves a box as a mover mod does: copies each block entity's data, begins, takes the blocks away,
     * sets them at the destination with their data, and completes.
     */
    private static RelocationResult move(Area a, BoundingBox box, Destination d) {
        ServerLevel level = a.level;
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> data = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
            BlockPos at = p.immutable();
            states.put(at, level.getBlockState(at));
            BlockEntity be = level.getBlockEntity(at);
            if (be != null) {
                data.put(at, be.saveWithoutMetadata(level.registryAccess()));
            }
        }
        RelocationTicket ticket = Relocations.begin(level, box, MOVER);
        for (BlockPos p : states.keySet()) {
            level.setBlockAndUpdate(p, Area.AIR);
        }
        states.forEach((p, state) -> d.level().setBlockAndUpdate(d.map(p), state.rotate(d.rotation())));
        data.forEach((p, tag) -> {
            BlockEntity be = d.level().getBlockEntity(d.map(p));
            if (be != null) {
                be.loadWithComponents(tag, level.registryAccess());
                be.setChanged();
                if (be instanceof AtmosDevice device && !DeviceTicker.isScheduled(device)) {
                    device.onLoad(); // the game would at the start of the next tick
                }
            }
        });
        return a.settle(Relocations.complete(ticket, d));
    }

    /**
     * A sealed room with a capped row of pipes holding 1,000 mol of carbon dioxide, moved 32 blocks:
     * the row holds the same 1,000 mol after the move, and none of it reached the room's air, where it
     * would have gone had the pipes released it as they were taken away (CONT-156).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cont_156_moved_pipes_keep_their_gas(GameTestHelper h) {
        Area a = new Area(h, 5500);
        a.clear(-1, -1, 42, 5);
        a.box(0, 0, 8, 4); // interior x 1 to 7, z 1 to 3
        a.place(1, 2, 2, cap(Direction.EAST));
        for (int x = 2; x <= 6; x++) {
            a.place(x, 2, 2, GasDeviceTests.pipe());
        }
        a.place(7, 2, 2, cap(Direction.WEST));
        PipeNodeEntity west = a.entity(2, 2, 2);
        west.network().gas().add(new GasParcel(Map.of(CO2, MOLES), T));
        a.fill(4, 1, 1, Map.of(Area.O2, 1.0));
        ZoneInfo room = a.zone(4, 1, 1);
        a.close(room.mixture().moles().getOrDefault(CO2, 0.0), 0, "carbon dioxide in the room before");

        RelocationResult moved = move(
                a,
                BoundingBox.fromCorners(a.abs(0, 0, 0), a.abs(8, 4, 4)),
                Destination.offset(a.level, new Vec3i(32, 0, 0)));
        h.assertValueEqual(moved.outcome(), RelocationOutcome.MOVED, "the move");
        a.tick();

        PipeNodeEntity moved2 = a.entity(34, 2, 2);
        a.close(moved2.network().gas().moles(CO2), MOLES, "carbon dioxide in the moved row");
        ZoneInfo after = a.zone(36, 1, 1);
        h.assertValueEqual(after.id(), room.id(), "the room's zone");
        a.close(after.mixture().moles().getOrDefault(CO2, 0.0), 0, "carbon dioxide in the room after");
        a.close(after.mixture().totalMoles(), room.mixture().totalMoles(), "the room's moles");
        h.assertFalse(Relocations.isMoving(a.level, a.abs(34, 2, 2)), "the moved row is still moving after it arrived");
        h.succeed();
    }
}
