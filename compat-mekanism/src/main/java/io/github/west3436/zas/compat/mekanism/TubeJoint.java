package io.github.west3436.zas.compat.mekanism;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import java.util.Map;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A Mekanism tube as ZAS³ sees it (MEK-013): a gas handler that holds nothing, takes nothing and
 * gives nothing. It is there so that a pipe grows an arm to the tube and counts as connected, with
 * no open end to leak from, on exactly the faces where the tube would connect to the pipe.
 *
 * <p>The tube does the moving, through the pipe's own {@link GasAsChemicals}: it pushes what its
 * network carries into the pipe, and draws from the pipe on a face set to pull, at the tube's own
 * rate. If the pipe shared gas with the tube as well, as it does with a tank, the two would pass the
 * same gas back and forth, since a tube in its usual mode both takes and gives on one face.
 */
final class TubeJoint implements IGasHandler {
    private static final TubeJoint JOINT = new TubeJoint();

    private TubeJoint() {}

    /** The capability provider: the joint wherever the tube offers its chemical handler. */
    @Nullable
    static IGasHandler of(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        if (!(level instanceof ServerLevel) || side == null) {
            return null;
        }
        IChemicalHandler tube = level.getCapability(ChemicalConnection.CHEMICAL, pos, state, entity, side);
        return tube == null || ChemicalConnection.isView(tube) ? null : JOINT;
    }

    @Override
    public GasMixture contents() {
        return ChemicalsAsGas.NOTHING;
    }

    @Override
    public GasParcel fill(GasParcel gas, Action action) {
        return GasParcel.EMPTY;
    }

    @Override
    public GasParcel drain(Map<ResourceLocation, Double> moles, Action action) {
        return GasParcel.EMPTY;
    }
}
