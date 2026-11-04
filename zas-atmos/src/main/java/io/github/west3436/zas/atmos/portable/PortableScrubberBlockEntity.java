package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A portable scrubber's block entity (CONT-018): each second it takes the selected gases of {@link
 * #LITRES} of the air it works on, a held tank or the zone it stands in, into its store, and stops
 * once the store reaches 50 atm (the scrubbing rules of CONT-079 and CONT-081). It draws {@code
 * [power] portable_scrubber} FE/t (15) from its battery only while it moves gas.
 */
public class PortableScrubberBlockEntity extends PortableGasDevice {
    /** Who a portable scrubber's changes are made by. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "portable_scrubber");

    /** The volume of air it scrubs each second, L: vg's 300 L per 2 s tick (D-20). */
    public static final double LITRES = 150;

    private static final String SELECTION = "Selection";

    private GasSelection selection = GasSelection.DEFAULT;

    public PortableScrubberBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.PORTABLE_SCRUBBER.get(), pos, state, SOURCE);
    }

    /** The gases it scrubs. */
    public GasSelection selection() {
        return selection;
    }

    @Override
    protected Move plan(GasMixture air) {
        GasMixture buffer = store().mixture();
        if (ScrubberMath.stalled(buffer)) {
            return Move.idle(PortableStatus.STORE_FULL);
        }
        Map<ResourceLocation, Double> take = ScrubberMath.take(air, LITRES, selection, buffer);
        double total = take.values().stream().mapToDouble(Double::doubleValue).sum();
        return total < PipeBlockEntity.MIN_FLOW ? Move.idle(PortableStatus.NOTHING_TO_MOVE) : Move.take(take);
    }

    @Override
    protected int drawPerTick() {
        return AtmosServerConfig.get(AtmosServerConfig.PORTABLE_SCRUBBER_DRAW);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(SELECTION, selection.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        selection = GasSelection.load(tag.getCompound(SELECTION));
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortableScrubberMenu(
                id,
                inventory,
                battery(),
                tank(),
                menuData(PortableGasMenu.DATA_COUNT),
                ContainerLevelAccess.create(level, worldPosition),
                this);
    }
}
